package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AabModuleMetadataParserTest {

    @Test
    fun baseModuleIsAlwaysInstallTime() {
        val metadata = AabModuleMetadataParser.parse(manifest(), "base")

        assertEquals(AabModuleType.BASE, metadata.type)
        assertEquals(listOf(AabModuleDeliveryMode.INSTALL_TIME), metadata.deliveryModes)
        assertFalse(metadata.deliveryDeclared)
        assertTrue(metadata.conditions.isEmpty())
    }

    @Test
    fun conditionalFeatureDeliveryAndFallbackAreDecoded() {
        val conditions = distElement(
            "conditions",
            children = listOf(
                distElement("min-sdk", attributes = listOf(distAttribute("value", "26"))),
                distElement("max-sdk", attributes = listOf(distAttribute("value", "35"))),
                distElement(
                    "device-feature",
                    attributes = listOf(
                        distAttribute("name", "android.hardware.camera.ar"),
                        distAttribute("version", "2"),
                    ),
                ),
                distElement(
                    "user-countries",
                    attributes = listOf(distAttribute("exclude", "false")),
                    children = listOf(
                        distElement("country", attributes = listOf(distAttribute("code", "us"))),
                        distElement("country", attributes = listOf(distAttribute("code", "ca"))),
                    ),
                ),
                distElement(
                    "device-groups",
                    children = listOf(
                        distElement("device-group", attributes = listOf(distAttribute("name", "high_ram"))),
                    ),
                ),
            ),
        )
        val module = distElement(
            "module",
            attributes = listOf(distAttribute("type", "feature")),
            children = listOf(
                distElement(
                    "delivery",
                    children = listOf(
                        distElement(
                            "install-time",
                            children = listOf(
                                conditions,
                                distElement(
                                    "removable",
                                    attributes = listOf(distAttribute("value", "true")),
                                ),
                            ),
                        ),
                        distElement("on-demand"),
                    ),
                ),
                distElement("fusing", attributes = listOf(distAttribute("include", "false"))),
            ),
        )

        val metadata = AabModuleMetadataParser.parse(manifest(module), "camera")

        assertEquals(AabModuleType.FEATURE, metadata.type)
        assertEquals(
            listOf(AabModuleDeliveryMode.INSTALL_TIME, AabModuleDeliveryMode.ON_DEMAND),
            metadata.deliveryModes,
        )
        assertTrue(metadata.deliveryDeclared)
        assertEquals(6, metadata.conditions.size)
        assertEquals(AabModuleConditionKind.MIN_SDK, metadata.conditions[0].kind)
        assertEquals("26", metadata.conditions[0].value)
        assertEquals(AabModuleConditionKind.DEVICE_FEATURE, metadata.conditions[2].kind)
        assertEquals(2, metadata.conditions[2].version)
        assertEquals("US", metadata.conditions[3].value)
        assertEquals("CA", metadata.conditions[4].value)
        assertEquals(AabModuleConditionKind.DEVICE_GROUP, metadata.conditions[5].kind)
        assertEquals(false, metadata.fusingIncluded)
        assertEquals(true, metadata.installTimeRemovable)
        assertNull(metadata.issue)
    }

    @Test
    fun legacyOnDemandAndLegacyFusingAttributesAreSupported() {
        val module = distElement(
            "module",
            attributes = listOf(attribute("onDemand", "true")),
            children = listOf(
                distElement("fusing", attributes = listOf(attribute("include", "true"))),
            ),
        )

        val metadata = AabModuleMetadataParser.parse(manifest(module), "legacy")

        assertEquals(AabModuleType.FEATURE, metadata.type)
        assertEquals(listOf(AabModuleDeliveryMode.ON_DEMAND), metadata.deliveryModes)
        assertTrue(metadata.deliveryDeclared)
        assertEquals(true, metadata.fusingIncluded)
    }

    @Test
    fun assetPackFastFollowConditionsAreReadFromDeliveryElement() {
        val module = distElement(
            "module",
            attributes = listOf(distAttribute("type", "asset-pack")),
            children = listOf(
                distElement(
                    "delivery",
                    children = listOf(
                        distElement("fast-follow"),
                        distElement(
                            "conditions",
                            children = listOf(
                                distElement(
                                    "user-countries",
                                    attributes = listOf(distAttribute("exclude", "true")),
                                    children = listOf(
                                        distElement(
                                            "country",
                                            attributes = listOf(distAttribute("code", "cn")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val metadata = AabModuleMetadataParser.parse(manifest(module), "assets")

        assertEquals(AabModuleType.ASSET_PACK, metadata.type)
        assertEquals(listOf(AabModuleDeliveryMode.FAST_FOLLOW), metadata.deliveryModes)
        assertEquals(AabModuleConditionKind.EXCLUDED_COUNTRY, metadata.conditions.single().kind)
        assertEquals("CN", metadata.conditions.single().value)
    }

    @Test
    fun conditionCountIsTruncatedAndConditionStringsStayBounded() {
        val conditionElements = (1..3).map { index ->
            distElement(
                "device-feature",
                attributes = listOf(distAttribute("name", "feature.$index")),
            )
        }
        val module = featureModule(conditionElements)

        val metadata = AabModuleMetadataParser.parse(
            manifest(module),
            "bounded",
            AabModuleMetadataParser.Limits(maxConditionValues = 2),
        )

        assertEquals(2, metadata.conditions.size)
        assertEquals(1, metadata.omittedConditionCount)
        captureIOException {
            AabModuleMetadataParser.parse(
                manifest(module),
                "bounded",
                AabModuleMetadataParser.Limits(maxConditionValueChars = 4),
            )
        }
    }

    @Test
    fun conflictingLegacyAndModernDeliveryIsRejected() {
        val module = distElement(
            "module",
            attributes = listOf(distAttribute("onDemand", "true")),
            children = listOf(
                distElement("delivery", children = listOf(distElement("on-demand"))),
            ),
        )

        captureIOException { AabModuleMetadataParser.parse(manifest(module), "invalid") }
    }

    private fun featureModule(
        conditions: List<AabModuleManifest.Element>,
    ): AabModuleManifest.Element = distElement(
        "module",
        attributes = listOf(distAttribute("type", "feature")),
        children = listOf(
            distElement(
                "delivery",
                children = listOf(
                    distElement(
                        "install-time",
                        children = listOf(distElement("conditions", children = conditions)),
                    ),
                ),
            ),
        ),
    )

    private fun manifest(
        vararg children: AabModuleManifest.Element,
    ): AabModuleManifest.Element = element(
        namespace = "",
        name = "manifest",
        children = children.toList(),
    )

    private fun distElement(
        name: String,
        attributes: List<AabModuleManifest.Attribute> = emptyList(),
        children: List<AabModuleManifest.Element> = emptyList(),
    ): AabModuleManifest.Element = element(DISTRIBUTION_NAMESPACE, name, attributes, children)

    private fun element(
        namespace: String,
        name: String,
        attributes: List<AabModuleManifest.Attribute> = emptyList(),
        children: List<AabModuleManifest.Element> = emptyList(),
    ): AabModuleManifest.Element = AabModuleManifest.Element(
        namespaceUri = namespace,
        name = name,
        attributes = attributes,
        children = children,
    )

    private fun distAttribute(name: String, value: String): AabModuleManifest.Attribute =
        attribute(name, value, DISTRIBUTION_NAMESPACE)

    private fun attribute(
        name: String,
        value: String,
        namespace: String = "",
    ): AabModuleManifest.Attribute = AabModuleManifest.Attribute(
        namespaceUri = namespace,
        name = name,
        value = value,
    )

    private fun captureIOException(block: () -> Unit): IOException {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (exception: IOException) {
            return exception
        }
    }

    private companion object {
        const val DISTRIBUTION_NAMESPACE = "http://schemas.android.com/apk/distribution"
    }
}
