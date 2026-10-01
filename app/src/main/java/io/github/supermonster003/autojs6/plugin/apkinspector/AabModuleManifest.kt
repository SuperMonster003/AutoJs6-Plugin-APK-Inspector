package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.AabManifestDisplayDecoder
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.StringReader
import java.util.ArrayDeque
import javax.xml.parsers.SAXParserFactory

/** Inspector-only delivery metadata tree built from the shared decoder's bounded XML output. */
internal object AabModuleManifest {
    data class Attribute(val namespaceUri: String, val name: String, val value: String)
    data class Element(
        val namespaceUri: String,
        val name: String,
        val attributes: List<Attribute> = emptyList(),
        val children: List<Element> = emptyList(),
    )

    fun parse(xml: String, limits: AabManifestDisplayDecoder.Limits = AabManifestDisplayDecoder.Limits()): Element {
        if (xml.length > limits.maxOutputChars || xml.contains("<!DOCTYPE", ignoreCase = true)) {
            throw IOException("AAB metadata XML exceeds the limit or contains a document type")
        }
        data class Pending(val uri: String, val name: String, val attributes: List<Attribute>, val children: MutableList<Element> = mutableListOf())
        val stack = ArrayDeque<Pending>()
        var root: Element? = null
        var nodes = 0
        var attributes = 0
        val handler = object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                throw SAXException("External entities are not permitted")

            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                nodes++
                if (nodes > limits.maxNodes || stack.size >= limits.maxDepth ||
                    attrs.length > limits.maxAttributesPerElement || attrs.length > limits.maxAttributes - attributes) {
                    throw SAXException("AAB metadata XML exceeds the tree limit")
                }
                attributes += attrs.length
                stack.addLast(Pending(uri, localName, (0 until attrs.length).map { index ->
                    Attribute(attrs.getURI(index), attrs.getLocalName(index), attrs.getValue(index))
                }))
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                val item = stack.removeLast()
                val element = Element(item.uri, item.name, item.attributes, item.children.toList())
                if (stack.isEmpty()) root = element else stack.peekLast()!!.children += element
            }
        }
        try {
            SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
                .newSAXParser().parse(InputSource(StringReader(xml)), handler)
        } catch (error: Exception) {
            throw IOException("Invalid shared AAB manifest XML", error)
        }
        return root ?: throw IOException("Shared AAB manifest XML has no root")
    }
}
