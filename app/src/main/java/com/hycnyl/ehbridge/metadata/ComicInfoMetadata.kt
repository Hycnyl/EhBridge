package com.hycnyl.ehbridge.metadata

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

data class XmlMetadata(val token: String? = null, val pages: Int? = null, val readable: Boolean = false)

object ComicInfoMetadata {
    const val MAX_XML = 2 * 1024 * 1024
    private val link = Regex("(?i)(?<![a-z0-9._/@:-])(?:https?://)?(?:e-hentai\\.org|exhentai\\.org)/g/([0-9]+)/([0-9a-f]{10})(?=[/\\s?#\"'<>]|$)")
    private val path = Regex("(?i)(?<![a-z0-9./:])/g/([0-9]+)/([0-9a-f]{10})(?=[/\\s?#\"'<>]|$)")

    /** The saved EhViewer name is sanitized and may be truncated: keep its title verbatim. */
    fun fromFolder(folderName: String, gid: Long, pageCount: Int): ByteArray {
        val title = folderName.substringAfter('-', folderName)
        fun escaped(value: String): String = buildString {
            for (character in value) when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (character == '\t' || character == '\n' || character == '\r' || character.code in 0x20..0xFFFD) append(character)
            }
        }
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<ComicInfo>\n  <Title>${escaped(title)}</Title>\n  <Series>${escaped(title)}</Series>\n" +
            "  <PageCount>$pageCount</PageCount>\n  <Notes>Gallery ID: $gid</Notes>\n</ComicInfo>\n").toByteArray(Charsets.UTF_8)
    }

    fun parse(bytes: ByteArray?, gid: Long): XmlMetadata {
        if (bytes == null || bytes.size > MAX_XML) return XmlMetadata()
        return try {
            val texts = mutableListOf<String>()
            val webTexts = mutableListOf<String>()
            var pageCount: Int? = null
            val stack = ArrayDeque<Pair<String, StringBuilder>>()
            val handler = object : DefaultHandler2() {
                override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD disabled") }
                override fun resolveEntity(publicId: String?, systemId: String?): InputSource { throw SAXException("External entities disabled") }
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                    val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()).substringAfter(':')
                    stack.addLast(tag to StringBuilder())
                    for (i in 0 until attributes.length) texts.add(attributes.getValue(i))
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { stack.lastOrNull()?.second?.append(ch, start, length) }
                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val (tag, value) = stack.removeLast()
                    val text = value.toString()
                    texts.add(text)
                    if (tag.equals("Web", true)) webTexts.add(text)
                    if (tag.equals("PageCount", true)) {
                        val raw = text.trim()
                        if (raw.matches(Regex("[0-9]+"))) raw.toIntOrNull()?.takeIf { it > 0 }?.let { pageCount = maxOf(pageCount ?: 0, it) }
                    }
                }
            }
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
            for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd"))
                runCatching { factory.setFeature(feature, false) }
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.entityResolver = handler
            // Mandatory lexical handler rejects DOCTYPE even on Android parsers lacking the optional feature above.
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.parse(InputSource(ByteArrayInputStream(bytes)))
            val token = (webTexts + texts).firstNotNullOfOrNull { text ->
                (link.findAll(text) + path.findAll(text)).firstOrNull { it.groupValues[1].toLongOrNull() == gid }?.groupValues?.get(2)?.lowercase(Locale.ROOT)
            }
            XmlMetadata(token, pageCount, true)
        } catch (_: Exception) { XmlMetadata() }
    }
}
