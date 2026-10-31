package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class PreviewAccessTest {
    @Test fun heartbeatOnlyReadsStateAndPreservesSession() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<camrply><result>ok</result></camrply>")); server.start()
            CameraSessionStore.set("existing")
            try {
                assertThat(protocol(server).keepSessionActive()).isInstanceOf(LumixResult.Success::class.java)
                assertThat(server.requestCount).isEqualTo(1)
                assertThat(server.takeRequest().path).isEqualTo("/cam.cgi?mode=getstate")
                assertThat(CameraSessionStore.sessionId).isEqualTo("existing")
            } finally { CameraSessionStore.clear() }
        }
    }
    @Test fun rejectedHeartbeatDoesNotAuthorizeOrChangeMode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<camrply><result>err_critical</result></camrply>")); server.start()
            assertThat(protocol(server).keepSessionActive()).isInstanceOf(LumixResult.Failure::class.java)
            assertThat(server.requestCount).isEqualTo(1)
        }
    }
    private fun protocol(server: MockWebServer) = LumixHttpProtocol(
        baseUrl = server.url("/").toString(),
        upnp = UpnpContentDirectoryClient(baseUrl = server.url("/").toString(),
            soapUrlOverride = server.url("/browse").toString(), tryLumixPrefix = false, sendProtocolInfo = false),
    )
    private fun emptyBrowse() = MockResponse().setBody("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><Result>&lt;DIDL-Lite/&gt;</Result><NumberReturned>0</NumberReturned><TotalMatches>0</TotalMatches></s:Body></s:Envelope>""")

    @Test fun successfulPreviewDoesNotBrowseOrReauthorize() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("image")); server.start()
            val file = CameraFile(server.url("/photo.JPG").toString(), "photo.JPG", 5)
            val result = protocol(server).openPreviewConnection(file) as LumixResult.Success
            try {
                assertThat(result.value.responseCode).isEqualTo(200)
                assertThat(result.value.readTimeout).isEqualTo(5000)
                assertThat(result.value.connectTimeout).isEqualTo(3000)
            } finally { result.value.disconnect() }
            assertThat(server.requestCount).isEqualTo(1)
            assertThat(server.takeRequest().method).isEqualTo("GET")
        }
    }
    @Test fun forbiddenPreviewRenewsSmallGrantAndRetriesOnce() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403)); server.enqueue(emptyBrowse())
            server.enqueue(MockResponse().setBody("image")); server.start()
            val file = CameraFile(server.url("/photo.JPG").toString(), "photo.JPG", 5)
            val result = protocol(server).openPreviewConnection(file) as LumixResult.Success
            try { assertThat(result.value.responseCode).isEqualTo(200) } finally { result.value.disconnect() }
            assertThat(server.requestCount).isEqualTo(3)
            assertThat(server.takeRequest().method).isEqualTo("GET")
            val browse = server.takeRequest()
            assertThat(browse.method).isEqualTo("POST")
            assertThat(browse.body.readUtf8()).contains("<RequestedCount>1</RequestedCount>")
            assertThat(server.takeRequest().method).isEqualTo("GET")
        }
    }
    @Test fun otherHttpFailuresDoNotTriggerAuthorization() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500)); server.start()
            val file = CameraFile(server.url("/photo.JPG").toString(), "photo.JPG", 5)
            val result = protocol(server).openPreviewConnection(file) as LumixResult.Success
            try { assertThat(result.value.responseCode).isEqualTo(500) } finally { result.value.disconnect() }
            assertThat(server.requestCount).isEqualTo(1)
        }
    }
    @Test fun establishedBrowseRouteStopsAtFirst403() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(emptyBrowse()); server.enqueue(MockResponse().setResponseCode(403)); server.start()
            val browse = UpnpContentDirectoryClient(baseUrl = server.url("/").toString(),
                soapUrlOverride = server.url("/browse").toString(), tryLumixPrefix = false, sendProtocolInfo = false)
            assertThat(browse.refreshAccess()).isInstanceOf(LumixResult.Success::class.java)
            assertThat(browse.refreshAccess()).isInstanceOf(LumixResult.Failure::class.java)
            assertThat(server.requestCount).isEqualTo(2)
        }
    }
}
