package com.lumalink.g100d.protocol

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.xml.sax.InputSource

/** Shared safe XML setup for camera descriptions and SOAP/DIDL responses. */
internal object CameraXml {
    fun hasUnsafeDeclaration(value: String): Boolean =
        value.contains("<!DOCTYPE", ignoreCase = true) || value.contains("<!ENTITY", ignoreCase = true)

    fun parse(value: String, factoryProvider: () -> DocumentBuilderFactory): Document? = runCatching {
        if (hasUnsafeDeclaration(value)) return@runCatching null
        val factory = factoryProvider().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }.parse(InputSource(StringReader(value)))
    }.getOrNull()
}
