package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal fun noObjectFault() = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode><errorDescription>No such object</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""

class UpnpFaultParserTest {
    @Test fun preservesUserReportedNoSuchObjectFaultAndPageContext() {
        val fault = UpnpFaultParser.parse(noObjectFault(), 500, 50, 1)!!
        assertThat(fault.code).isEqualTo(701)
        assertThat(fault.description).isEqualTo("No such object")
        assertThat(fault.message).contains("SOAP 701")
        assertThat(fault.message).contains("start=50 count=1")
    }
    @Test fun handlesNamespacedCodeAndDescription() {
        val body = noObjectFault().replace("<errorCode>", "<e:errorCode xmlns:e='urn:errors'>").replace("</errorCode>", "</e:errorCode>")
        assertThat(UpnpFaultParser.parse(body, 500, 0, 1)?.code).isEqualTo(701)
    }
    @Test fun rejectsNonFaultBodiesAndUnsafeXml() {
        assertThat(UpnpFaultParser.parse("<root><errorCode>701</errorCode></root>", 500, 0, 1)).isNull()
        assertThat(UpnpFaultParser.parse("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///private'>]>" + noObjectFault(), 500, 0, 1)).isNull()
        assertThat(UpnpFaultParser.parse("server busy", 500, 0, 1)).isNull()
    }
}
