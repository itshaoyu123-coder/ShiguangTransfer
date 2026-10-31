/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.protocol

import javax.xml.parsers.DocumentBuilderFactory

internal object UpnpFaultParser {
    fun parse(body: String, httpStatus: Int, start: Int, count: Int): LumixError.UpnpFault? {
        if (!body.trimStart().startsWith("<")) return null
        val document = CameraXml.parse(body) { DocumentBuilderFactory.newInstance() } ?: return null
        if (document.getElementsByTagNameNS("*", "Fault").length == 0) return null
        val code = document.getElementsByTagNameNS("*", "errorCode").item(0)?.textContent?.trim()?.toIntOrNull() ?: return null
        val description = document.getElementsByTagNameNS("*", "errorDescription").item(0)?.textContent?.trim()?.take(160).orEmpty()
        return LumixError.UpnpFault(code, description.ifEmpty { "Unknown UPnP error" }, httpStatus, start, count)
    }
}
