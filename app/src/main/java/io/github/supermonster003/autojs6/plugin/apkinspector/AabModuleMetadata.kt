package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.IOException
import java.util.Locale

internal enum class AabMetadataIssueCode {
    MISSING,
    LIMIT,
    INVALID,
}

internal data class AabMetadataIssue(
    val code: AabMetadataIssueCode,
    val detail: String = "",
)

internal enum class AabModuleType {
    BASE,
    FEATURE,
    ASSET_PACK,
    ML_PACK,
    AI_PACK,
    SDK,
    UNKNOWN,
}

internal enum class AabModuleDeliveryMode {
    INSTALL_TIME,
    ON_DEMAND,
    FAST_FOLLOW,
    UNKNOWN,
}

internal enum class AabModuleConditionKind {
    MIN_SDK,
    MAX_SDK,
    DEVICE_FEATURE,
    INCLUDED_COUNTRY,
    EXCLUDED_COUNTRY,
    DEVICE_GROUP,
    UNKNOWN,
}

internal data class AabModuleCondition(
    val kind: AabModuleConditionKind,
    val value: String,
    val version: Int? = null,
)

internal data class AabModuleMetadata(
    val name: String,
    val type: AabModuleType,
    val declaredType: String?,
    val deliveryModes: List<AabModuleDeliveryMode>,
    val deliveryDeclared: Boolean,
    val conditions: List<AabModuleCondition>,
    val omittedConditionCount: Int,
    val fusingIncluded: Boolean?,
    val installTimeRemovable: Boolean?,
    val issue: AabMetadataIssue? = null,
) {

    companion object {
        fun invalid(name: String, detail: String): AabModuleMetadata = AabModuleMetadata(
            name = name,
            type = if (name == "base") AabModuleType.BASE else AabModuleType.UNKNOWN,
            declaredType = null,
            deliveryModes = if (name == "base") {
                listOf(AabModuleDeliveryMode.INSTALL_TIME)
            } else {
                emptyList()
            },
            deliveryDeclared = false,
            conditions = emptyList(),
            omittedConditionCount = 0,
            fusingIncluded = null,
            installTimeRemovable = null,
            issue = AabMetadataIssue(
                AabMetadataIssueCode.INVALID,
                detail.take(MAX_ISSUE_DETAIL_CHARS),
            ),
        )

        private const val MAX_ISSUE_DETAIL_CHARS = 240
    }
}

/**
 * Extracts Play Feature Delivery declarations from an already bounded AAPT2 manifest tree.
 * Namespace-aware tree access avoids reparsing rendered XML and keeps declarations separate from
 * ordinary Android manifest elements with similar local names.
 */
internal object AabModuleMetadataParser {

    internal data class Limits(
        val maxConditionValues: Int = 128,
        val maxConditionValueChars: Int = 1_024,
    )

    fun parse(
        root: AabManifestDisplayDecoder.XmlElement,
        moduleName: String,
        limits: Limits = Limits(),
    ): AabModuleMetadata {
        require(limits.maxConditionValues >= 0) { "Condition value limit must be non-negative" }
        require(limits.maxConditionValueChars >= 0) { "Condition string limit must be non-negative" }
        if (root.name != MANIFEST_ELEMENT || root.namespaceUri.isNotEmpty()) {
            throw malformed("root element is not an Android manifest")
        }
        if (moduleName == BASE_MODULE) {
            return AabModuleMetadata(
                name = moduleName,
                type = AabModuleType.BASE,
                declaredType = null,
                deliveryModes = listOf(AabModuleDeliveryMode.INSTALL_TIME),
                deliveryDeclared = false,
                conditions = emptyList(),
                omittedConditionCount = 0,
                fusingIncluded = null,
                installTimeRemovable = null,
            )
        }

        val module = root.singleOptionalChild(DISTRIBUTION_NAMESPACE, MODULE_ELEMENT)
            ?: return AabModuleMetadata(
                name = moduleName,
                type = AabModuleType.FEATURE,
                declaredType = null,
                deliveryModes = listOf(AabModuleDeliveryMode.INSTALL_TIME),
                deliveryDeclared = false,
                conditions = emptyList(),
                omittedConditionCount = 0,
                fusingIncluded = null,
                installTimeRemovable = null,
            )
        val declaredType = module.attribute(DISTRIBUTION_NAMESPACE, TYPE_ATTRIBUTE)
            ?.takeIf(String::isNotBlank)
        val moduleType = declaredType.toModuleType()
        val delivery = module.singleOptionalChild(DISTRIBUTION_NAMESPACE, DELIVERY_ELEMENT)
        val legacyOnDemandAttribute = module.attributeWithLegacyFallback(ON_DEMAND_ATTRIBUTE)
        if (delivery != null && legacyOnDemandAttribute != null) {
            throw malformed("module uses both dist:delivery and legacy dist:onDemand")
        }

        val deliveryModes = if (delivery == null) {
            listOf(
                if (legacyOnDemandAttribute?.toStrictBoolean(ON_DEMAND_ATTRIBUTE) == true) {
                    AabModuleDeliveryMode.ON_DEMAND
                } else {
                    AabModuleDeliveryMode.INSTALL_TIME
                },
            )
        } else {
            buildList {
                if (delivery.singleOptionalChild(DISTRIBUTION_NAMESPACE, INSTALL_TIME_ELEMENT) != null) {
                    add(AabModuleDeliveryMode.INSTALL_TIME)
                }
                if (delivery.singleOptionalChild(DISTRIBUTION_NAMESPACE, ON_DEMAND_ELEMENT) != null) {
                    add(AabModuleDeliveryMode.ON_DEMAND)
                }
                if (delivery.singleOptionalChild(DISTRIBUTION_NAMESPACE, FAST_FOLLOW_ELEMENT) != null) {
                    add(AabModuleDeliveryMode.FAST_FOLLOW)
                }
                if (isEmpty()) add(AabModuleDeliveryMode.UNKNOWN)
            }
        }

        val installTime = delivery?.singleOptionalChild(
            DISTRIBUTION_NAMESPACE,
            INSTALL_TIME_ELEMENT,
        )
        val conditionParent = when (moduleType) {
            AabModuleType.ASSET_PACK, AabModuleType.AI_PACK -> delivery
            else -> installTime
        }
        val conditionContainer = conditionParent?.singleOptionalChild(
            DISTRIBUTION_NAMESPACE,
            CONDITIONS_ELEMENT,
        )
        val collector = ConditionCollector(limits)
        conditionContainer?.elementChildren()?.forEach { condition ->
            parseCondition(condition, collector)
        }

        val fusing = module.singleOptionalChild(DISTRIBUTION_NAMESPACE, FUSING_ELEMENT)
        val fusingIncluded = fusing?.attributeWithLegacyFallback(INCLUDE_ATTRIBUTE)
            ?.toStrictBoolean(INCLUDE_ATTRIBUTE)
            ?: if (fusing == null) null else throw malformed("dist:fusing is missing dist:include")
        val removable = installTime
            ?.singleOptionalChild(DISTRIBUTION_NAMESPACE, REMOVABLE_ELEMENT)
            ?.let { element ->
                element.attribute(DISTRIBUTION_NAMESPACE, VALUE_ATTRIBUTE)
                    ?.toStrictBoolean(VALUE_ATTRIBUTE)
                    ?: throw malformed("dist:removable is missing dist:value")
            }

        return AabModuleMetadata(
            name = moduleName,
            type = moduleType,
            declaredType = declaredType,
            deliveryModes = deliveryModes,
            deliveryDeclared = delivery != null || legacyOnDemandAttribute != null,
            conditions = collector.values,
            omittedConditionCount = collector.omittedCount,
            fusingIncluded = fusingIncluded,
            installTimeRemovable = removable,
        )
    }

    private fun parseCondition(
        element: AabManifestDisplayDecoder.XmlElement,
        collector: ConditionCollector,
    ) {
        if (element.namespaceUri != DISTRIBUTION_NAMESPACE) {
            throw malformed("delivery condition ${element.name} has an invalid namespace")
        }
        when (element.name) {
            MIN_SDK_ELEMENT -> collector.add(
                AabModuleConditionKind.MIN_SDK,
                element.requiredAttribute(VALUE_ATTRIBUTE).requireNonNegativeInt(VALUE_ATTRIBUTE).toString(),
            )
            MAX_SDK_ELEMENT -> collector.add(
                AabModuleConditionKind.MAX_SDK,
                element.requiredAttribute(VALUE_ATTRIBUTE).requireNonNegativeInt(VALUE_ATTRIBUTE).toString(),
            )
            DEVICE_FEATURE_ELEMENT -> {
                val version = element.attribute(DISTRIBUTION_NAMESPACE, VERSION_ATTRIBUTE)
                    ?.requireNonNegativeInt(VERSION_ATTRIBUTE)
                collector.add(
                    AabModuleConditionKind.DEVICE_FEATURE,
                    element.requiredAttribute(NAME_ATTRIBUTE),
                    version,
                )
            }
            USER_COUNTRIES_ELEMENT -> {
                val excluded = element.attribute(DISTRIBUTION_NAMESPACE, EXCLUDE_ATTRIBUTE)
                    ?.toStrictBoolean(EXCLUDE_ATTRIBUTE)
                    ?: false
                element.elementChildren().forEach { country ->
                    if (country.namespaceUri != DISTRIBUTION_NAMESPACE || country.name != COUNTRY_ELEMENT) {
                        throw malformed("dist:user-countries contains an unexpected child")
                    }
                    collector.add(
                        if (excluded) {
                            AabModuleConditionKind.EXCLUDED_COUNTRY
                        } else {
                            AabModuleConditionKind.INCLUDED_COUNTRY
                        },
                        country.requiredAttribute(CODE_ATTRIBUTE).uppercase(Locale.ROOT),
                    )
                }
            }
            DEVICE_GROUPS_ELEMENT -> {
                element.elementChildren().forEach { group ->
                    if (group.namespaceUri != DISTRIBUTION_NAMESPACE || group.name != DEVICE_GROUP_ELEMENT) {
                        throw malformed("dist:device-groups contains an unexpected child")
                    }
                    collector.add(
                        AabModuleConditionKind.DEVICE_GROUP,
                        group.requiredAttribute(NAME_ATTRIBUTE),
                    )
                }
            }
            else -> collector.add(AabModuleConditionKind.UNKNOWN, element.name)
        }
    }

    private class ConditionCollector(private val limits: Limits) {
        val values = ArrayList<AabModuleCondition>()
        var omittedCount: Int = 0
            private set

        fun add(kind: AabModuleConditionKind, value: String, version: Int? = null) {
            if (value.isBlank()) throw malformed("delivery condition contains an empty value")
            if (value.length > limits.maxConditionValueChars) {
                throw malformed("delivery condition value exceeds the inspection limit")
            }
            if (values.size >= limits.maxConditionValues) {
                omittedCount = Math.addExact(omittedCount, 1)
                return
            }
            values += AabModuleCondition(kind, value, version)
        }
    }

    private fun AabManifestDisplayDecoder.XmlElement.requiredAttribute(name: String): String =
        attribute(DISTRIBUTION_NAMESPACE, name)
            ?.takeIf(String::isNotBlank)
            ?: throw malformed("dist:${this.name} is missing dist:$name")

    private fun AabManifestDisplayDecoder.XmlElement.attribute(
        namespace: String,
        name: String,
    ): String? = attributes.singleOrNull { attribute ->
        attribute.namespaceUri == namespace && attribute.name == name
    }?.value ?: if (attributes.count { it.namespaceUri == namespace && it.name == name } > 1) {
        throw malformed("element ${this.name} contains duplicate $name attributes")
    } else {
        null
    }

    private fun AabManifestDisplayDecoder.XmlElement.attributeWithLegacyFallback(name: String): String? =
        attribute(DISTRIBUTION_NAMESPACE, name) ?: attribute("", name)

    private fun AabManifestDisplayDecoder.XmlElement.elementChildren(): List<AabManifestDisplayDecoder.XmlElement> =
        children.mapNotNull { child ->
            (child as? AabManifestDisplayDecoder.XmlNode.Element)?.value
        }

    private fun AabManifestDisplayDecoder.XmlElement.singleOptionalChild(
        namespace: String,
        name: String,
    ): AabManifestDisplayDecoder.XmlElement? {
        val matching = elementChildren().filter { child ->
            child.namespaceUri == namespace && child.name == name
        }
        if (matching.size > 1) throw malformed("element ${this.name} contains multiple $name children")
        return matching.singleOrNull()
    }

    private fun String?.toModuleType(): AabModuleType = when (this?.lowercase(Locale.ROOT)) {
        null, "feature" -> AabModuleType.FEATURE
        "asset-pack" -> AabModuleType.ASSET_PACK
        "ml-pack" -> AabModuleType.ML_PACK
        "ai-pack" -> AabModuleType.AI_PACK
        "sdk" -> AabModuleType.SDK
        else -> AabModuleType.UNKNOWN
    }

    private fun String.toStrictBoolean(label: String): Boolean = when {
        equals("true", ignoreCase = true) || this == "1" || equals("0xffffffff", true) -> true
        equals("false", ignoreCase = true) || this == "0" -> false
        else -> throw malformed("dist:$label is not a boolean")
    }

    private fun String.requireNonNegativeInt(label: String): Int =
        toIntOrNull()?.takeIf { value -> value >= 0 }
            ?: throw malformed("dist:$label is not a non-negative integer")

    private fun malformed(detail: String): IOException =
        IOException("Invalid AAB module delivery metadata: $detail")

    private const val BASE_MODULE = "base"
    private const val MANIFEST_ELEMENT = "manifest"
    private const val MODULE_ELEMENT = "module"
    private const val DELIVERY_ELEMENT = "delivery"
    private const val INSTALL_TIME_ELEMENT = "install-time"
    private const val ON_DEMAND_ELEMENT = "on-demand"
    private const val FAST_FOLLOW_ELEMENT = "fast-follow"
    private const val CONDITIONS_ELEMENT = "conditions"
    private const val FUSING_ELEMENT = "fusing"
    private const val REMOVABLE_ELEMENT = "removable"
    private const val MIN_SDK_ELEMENT = "min-sdk"
    private const val MAX_SDK_ELEMENT = "max-sdk"
    private const val DEVICE_FEATURE_ELEMENT = "device-feature"
    private const val USER_COUNTRIES_ELEMENT = "user-countries"
    private const val COUNTRY_ELEMENT = "country"
    private const val DEVICE_GROUPS_ELEMENT = "device-groups"
    private const val DEVICE_GROUP_ELEMENT = "device-group"
    private const val TYPE_ATTRIBUTE = "type"
    private const val ON_DEMAND_ATTRIBUTE = "onDemand"
    private const val INCLUDE_ATTRIBUTE = "include"
    private const val VALUE_ATTRIBUTE = "value"
    private const val NAME_ATTRIBUTE = "name"
    private const val VERSION_ATTRIBUTE = "version"
    private const val EXCLUDE_ATTRIBUTE = "exclude"
    private const val CODE_ATTRIBUTE = "code"
    private const val DISTRIBUTION_NAMESPACE = "http://schemas.android.com/apk/distribution"
}
