/* Modified 2026-10-30 by Shiguang Transfer contributors.
 * Based on LumaLink progressive.3, Apache-2.0. See docs/CHANGES_SHIGUANG_0.2.0.md. */
package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

class ProgressiveBrowseTest {
    // Catch forward-first scans, skipped partial windows, and publishing only at completion.
    @Test fun publishesNewestPageBeforeReadingOlderPages() = runBlocking {
        MockWebServer().use { server ->
            val starts = mutableListOf<Int>()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    val count = field(body, "RequestedCount")
                    starts += start
                    if (start == 3) {
                        assertThat(snapshots.any { it.files.firstOrNull()?.name == "P1000103.JPG" }).isTrue()
                    }
                    return page(start, count, 103)
                }
            }
            server.start()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(starts).containsExactly(0, 53, 3, 0).inOrder()
            assertThat(snapshots.first().files.first().name).isEqualTo("P1000103.JPG")
            assertThat(snapshots.first().files).hasSize(50)
            assertThat(snapshots.last().files).hasSize(103)
            assertThat(snapshots.last().scannedCount).isEqualTo(103)
            assertThat(snapshots.last().complete).isTrue()
            assertThat(snapshots.last().files.last().name).isEqualTo("P1000001.JPG")
            assertThat(snapshots.first().files.first().thumbnailPath).endsWith("/DT11000103.JPG")
        }
    }

    // Catch throwing away already usable photos when an older page fails.
    @Test fun keepsLatestFilesWhenAnOlderPageFails() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    return if (start == 3) MockResponse().setResponseCode(503)
                    else page(start, field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Failure::class.java)
            assertThat(snapshots.last().files).hasSize(50)
            assertThat(snapshots.last().complete).isFalse()
            assertThat(snapshots.last().warning).contains("503")
        }
    }

    // Catch treating cancellation as a connection error or continuing an abandoned scan.
    @Test fun propagatesCancellationWithoutReadingAnotherPage() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            var cancelled = false
            try {
                client(server).browseNewest(onProgress = { throw CancellationException("refresh") })
            } catch (_: CancellationException) { cancelled = true }
            assertThat(cancelled).isTrue()
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    // Catch endless repeat-page loops and silently presenting an incomplete list as complete.
    @Test fun repeatedPagesStopWithPartialWarning() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    return page(0, field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Failure::class.java)
            assertThat(snapshots.last().complete).isFalse()
            assertThat(snapshots.last().files).hasSize(50)
            assertThat(server.requestCount).isEqualTo(3)
        }
    }

    @Test fun emptyCardCompletesWithoutTailRequests() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(page(0, 1, 0))
            server.start()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(snapshots.last().complete).isTrue()
            assertThat(snapshots.last().files).isEmpty()
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test fun unknownTotalLoadsProgressivelyWithExplicitWarning() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val response = page(field(body, "StartingIndex"), field(body, "RequestedCount"), 3)
                    val xml = response.getBody()!!.readUtf8().replace("<TotalMatches>3</TotalMatches>", "")
                    return MockResponse().setBody(xml)
                }
            }
            server.start()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(snapshots.last().files.first().name).isEqualTo("P1000003.JPG")
            assertThat(snapshots.last().files).hasSize(3)
            assertThat(snapshots.last().total).isNull()
            assertThat(snapshots.last().warning).isNotNull()
        }
    }

    @Test fun reusesWorkingControlUrlAfterFirst404() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path.orEmpty().startsWith("/Lumix/")) return MockResponse().setResponseCode(404)
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 3)
                }
            }
            server.start()
            val client = UpnpContentDirectoryClient(
                baseUrl = "http://127.0.0.1", soapUrlOverride = server.url("/Server0/CDS_control").toString(),
                tryLumixPrefix = true, sendProtocolInfo = false,
            )
            val result = client.browseNewest(onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(server.requestCount).isEqualTo(3)
            assertThat(server.takeRequest().path).startsWith("/Lumix/")
            assertThat(server.takeRequest().path).isEqualTo("/Server0/CDS_control")
            assertThat(server.takeRequest().path).isEqualTo("/Server0/CDS_control")
        }
    }

    @Test fun retriesExpiredAccessThenContinuesSameWindow() = runBlocking {
        MockWebServer().use { server ->
            var expires = 4
            var authCalls = 0
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    if (field(body, "RequestedCount") == 50 && expires > 0) {
                        expires--
                        return MockResponse().setResponseCode(403)
                    }
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            val result = client(server).browseNewest(onAuthRefresh = { authCalls++; true }, onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat((result as LumixResult.Success).value).hasSize(103)
            assertThat(authCalls).isEqualTo(1)
        }
    }

    @Test fun shortPagesFetchUncoveredPartOfEachWindow() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), minOf(17, field(body, "RequestedCount")), 103)
                }
            }
            server.start()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(snapshots.first().files.first().name).isEqualTo("P1000103.JPG")
            assertThat(snapshots.last().files).hasSize(103)
            assertThat(snapshots.last().complete).isTrue()
        }
    }

    // Camera counts can have different units: resource files != Browse objects.
    @Test fun usesBrowseObjectTotalRatherThanLargerContentFileCountForOffsets() = runBlocking {
        MockWebServer().use { server ->
            val starts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path.orEmpty().startsWith("/cam.cgi")) {
                        return MockResponse().setBody("<camrply><result>ok</result><total_content_number>103</total_content_number></camrply>")
                    }
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    starts += start
                    return page(start, field(body, "RequestedCount"), 50)
                }
            }
            server.start()
            val protocol = LumixHttpProtocol(baseUrl = server.url("/").toString(), upnp = client(server))
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = protocol.browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(snapshots.first().files.first().name).isEqualTo("P1000050.JPG")
            assertThat(snapshots.last().files).hasSize(50)
            assertThat(snapshots.last().total).isEqualTo(50)
            assertThat(starts).containsExactly(0, 0).inOrder()
        }
    }

    @Test fun countsRawAndJpegAsTwoFilesButOneBrowseObject() = runBlocking {
        MockWebServer().use { server ->
            val starts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    starts += start
                    val response = page(start, field(body, "RequestedCount"), 103)
                    val raw = """<res protocolInfo="http-get:*:application/octet-stream:PANASONIC.COM_PN=CAM_RAW;OriginalFileName='P1000001.RW2'" size="456">http://192.168.54.1:50001/DO11000001.RW2</res>"""
                        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                    val xml = response.getBody()!!.readUtf8().replace("&lt;/item&gt;", raw+"&lt;/item&gt;")
                        .replace(Regex("<TotalMatches>[0-9]+</TotalMatches>"), "")
                    // Distinct RAW names must correspond to their item's JPG name.
                    val fixed = xml.replace(Regex("(&lt;item.*?&lt;/item&gt;)", RegexOption.DOT_MATCHES_ALL)) { match ->
                        val name = Regex("P100([0-9]+)\\.JPG").find(match.value)!!.groupValues[1]
                        match.value.replace("P1000001.RW2", "P100$name.RW2").replace("DO11000001.RW2", "DO1100$name.RW2")
                    }
                    return MockResponse().setBody(fixed)
                }
            }
            server.start()
            val snapshots = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat((result as LumixResult.Success).value).hasSize(206)
            assertThat(starts).containsExactly(0, 0, 50, 100, 103).inOrder()
            assertThat(snapshots.last().scannedCount).isEqualTo(103)
        }
    }

    @Test fun acceptsCameraRequiringQuotedSoapActionInsteadOfRepeating500() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (!request.getHeader("SOAPAction").orEmpty().startsWith("\"")) {
                        return MockResponse().setResponseCode(500).setBody("<faultstring>SOAPAction must be quoted</faultstring>")
                    }
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 3)
                }
            }
            server.start()
            val result = client(server).browseNewest(onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat((result as LumixResult.Success).value).hasSize(3)
        }
    }

    @Test fun recoversWhenPlaybackBrowseIsNotReadyForMoreThanOneRetry() = runBlocking {
        MockWebServer().use { server ->
            var attempts = 0
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    attempts++
                    if (attempts <= 8) return MockResponse().setResponseCode(500).setBody("<faultstring>not ready</faultstring>")
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 3)
                }
            }
            server.start()
            val result = client(server).browseNewest(onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat((result as LumixResult.Success).value).hasSize(3)
        }
    }

    @Test fun initializesAdvertisedConnectionManagerBeforeBrowse() = runBlocking {
        MockWebServer().use { server ->
            var ready = false
            server.start()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path) {
                        "/description" -> MockResponse().setBody("""<root xmlns="urn:schemas-upnp-org:device-1-0"><device><serviceList><service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>${server.url("/advertised/content")}</controlURL></service><service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>${server.url("/advertised/connection")}</controlURL></service></serviceList></device></root>""")
                        "/advertised/connection" -> { ready = true; MockResponse().setBody("<ok/>") }
                        "/advertised/content" -> if (!ready) MockResponse().setResponseCode(500).setBody("<faultstring>ConnectionManager not initialized</faultstring>") else {
                            val body = request.body.readUtf8()
                            page(field(body, "StartingIndex"), field(body, "RequestedCount"), 3)
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val resolver = UpnpDescriptionResolver(
                baseUrl = "http://127.0.0.1", descriptionUrlsOverride = listOf(server.url("/description").toString()),
            )
            val browser = UpnpContentDirectoryClient(baseUrl = "http://127.0.0.1", descriptionResolver = resolver, tryLumixPrefix = false)
            val result = browser.browseNewest(onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat((result as LumixResult.Success).value).hasSize(3)
        }
    }

    @Test fun retriesInterruptedOlderPageWithoutDiscardingLoadedPhotos() = runBlocking {
        MockWebServer().use { server ->
            var interrupted = false
            val snapshots = mutableListOf<CameraBrowseProgress>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    if (start == 3 && !interrupted) {
                        interrupted = true
                        return MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    }
                    return page(start, field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            val result = client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat((result as LumixResult.Success).value).hasSize(103)
            assertThat(snapshots.first().files).hasSize(50)
            assertThat(snapshots.last().complete).isTrue()
        }
    }

    @Test fun resumesFailedOlderWindowWithoutRereadingNewestWindow() = runBlocking {
        MockWebServer().use { server ->
            var failOlder = true
            val starts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8(); val start = field(body, "StartingIndex")
                    starts += start
                    return if (start == 3 && failOlder) MockResponse().setResponseCode(503)
                    else page(start, field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            val browser = client(server)
            val initial = mutableListOf<CameraBrowseProgress>()
            assertThat(browser.browseNewest(onProgress = { initial += it })).isInstanceOf(LumixResult.Failure::class.java)
            assertThat(initial.last().files).hasSize(50)
            starts.clear(); failOlder = false
            val resumed = mutableListOf<CameraBrowseProgress>()
            assertThat(browser.browseNewest(resume = true, onProgress = { resumed += it })).isInstanceOf(LumixResult.Success::class.java)
            assertThat(starts).containsExactly(0, 3, 0).inOrder()
            assertThat(resumed.first().files).hasSize(50)
            assertThat(resumed.last().files).hasSize(103)
            assertThat(resumed.last().complete).isTrue()
        }
    }

    @Test fun changedObjectTotalDiscardsOldSessionCheckpoint() = runBlocking {
        MockWebServer().use { server ->
            var total = 103
            var fail = true
            val starts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8(); val start = field(body, "StartingIndex")
                    starts += start
                    return if (start == 3 && fail) MockResponse().setResponseCode(503)
                    else page(start, field(body, "RequestedCount"), total)
                }
            }
            server.start(); val browser = client(server)
            browser.browseNewest(onProgress = {})
            total = 52; fail = false; starts.clear()
            val result = browser.browseNewest(resume = true, onProgress = {}) as LumixResult.Success
            assertThat(result.value).hasSize(52)
            assertThat(starts).containsExactly(0, 2, 0).inOrder()
            assertThat(result.value.first().name).isEqualTo("P1000052.JPG")
        }
    }

    @Test fun explicitRefreshDoesNotReuseCheckpoint() = runBlocking {
        MockWebServer().use { server ->
            var fail = true; val starts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8(); val start = field(body, "StartingIndex"); starts += start
                    return if (start == 3 && fail) MockResponse().setResponseCode(503) else page(start, field(body, "RequestedCount"), 103)
                }
            }
            server.start(); val browser = client(server); browser.browseNewest(onProgress = {})
            fail = false; starts.clear(); browser.browseNewest(resume = false, onProgress = {})
            assertThat(starts).containsExactly(0, 53, 3, 0).inOrder()
        }
    }

    @Test fun usesSmallGrantRefreshWithNormalWindowFallbackFor701() = runBlocking {
        MockWebServer().use { server ->
            val counts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8(); val count = field(body, "RequestedCount"); counts += count
                    return if (count == 1) MockResponse().setResponseCode(500).setBody(noObjectFault())
                    else page(field(body, "StartingIndex"), count, 3)
                }
            }
            server.start(); val browser = client(server)
            assertThat(browser.browseNewest(onProgress = {})).isInstanceOf(LumixResult.Success::class.java)
            assertThat(browser.refreshAccess()).isInstanceOf(LumixResult.Success::class.java)
            assertThat(counts.first()).isEqualTo(50)
            assertThat(counts.last()).isEqualTo(50)
            assertThat(counts.takeLast(2)).containsExactly(1, 50).inOrder()
        }
    }

    @Test fun noSuchObjectIsNotRetriedAsHeaderOrBusyError() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500).setBody(noObjectFault())
            }
            server.start()
            val result = client(server).browseNewest(onProgress = {}) as LumixResult.Failure
            assertThat(result.error).isInstanceOf(LumixError.UpnpFault::class.java)
            assertThat(result.error.message).contains("701")
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test fun restoresPlaybackOnceThenRetriesSameObjectAndOffset() = runBlocking {
        MockWebServer().use { server ->
            var ready = false; var recoveries = 0; val starts = mutableListOf<Int>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8(); val start = field(body, "StartingIndex"); starts += start
                    return if (!ready) MockResponse().setResponseCode(500).setBody(noObjectFault()) else page(start, field(body, "RequestedCount"), 3)
                }
            }
            server.start()
            val result = client(server).browseNewest(onPlaybackRecovery = { recoveries++; ready = true; true }, onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Success::class.java)
            assertThat(recoveries).isEqualTo(1)
            assertThat(starts).containsExactly(0, 0, 0).inOrder()
        }
    }

    @Test fun persistent701HasOnlyOnePlaybackRecoveryAndNoRequestStorm() = runBlocking {
        MockWebServer().use { server ->
            var recoveries = 0
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500).setBody(noObjectFault())
            }
            server.start()
            val result = client(server).browseNewest(onPlaybackRecovery = { recoveries++; true }, onProgress = {})
            assertThat(result).isInstanceOf(LumixResult.Failure::class.java)
            assertThat(recoveries).isEqualTo(1)
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    @Test fun missingOlderObjectKeepsLoadedLatestPhotos() = runBlocking {
        MockWebServer().use { server ->
            val snapshots = mutableListOf<CameraBrowseProgress>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8(); val start = field(body, "StartingIndex")
                    return if (start == 3) MockResponse().setResponseCode(500).setBody(noObjectFault()) else page(start, field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            client(server).browseNewest(onProgress = { snapshots += it })
            assertThat(snapshots.last().files).hasSize(50)
            assertThat(snapshots.last().warning).contains("701")
            assertThat(snapshots.last().complete).isFalse()
        }
    }

    @Test fun expiredAccessRestoresPlaybackBeforeRetryingBrowse() = runBlocking {
        MockWebServer().use { server ->
            val playing = java.util.concurrent.atomic.AtomicBoolean(false)
            val accessCalls = java.util.concurrent.atomic.AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method == "GET") {
                        val url = request.requestUrl!!
                        if (url.queryParameter("type") == "req_acc_g") return MockResponse().setBody("err_param")
                        if (url.queryParameter("type") == "req_acc") {
                            accessCalls.incrementAndGet(); playing.set(false)
                            return MockResponse().setBody("ok,S5M2,remote,open,testsession")
                        }
                        if (url.queryParameter("value") == "playmode") playing.set(true)
                        return MockResponse().setBody("<camrply><result>ok</result></camrply>")
                    }
                    if (!playing.get()) return MockResponse().setResponseCode(403)
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 3)
                }
            }
            server.start()
            try {
                val protocol = LumixHttpProtocol(baseUrl = server.url("/").toString(), upnp = client(server))
                val result = protocol.browseNewest(onProgress = {})
                assertThat(result).isInstanceOf(LumixResult.Success::class.java)
                assertThat(accessCalls.get()).isEqualTo(1)
                assertThat(playing.get()).isTrue()
            } finally { CameraSessionStore.clear() }
        }
    }

    @Test fun preservesAdvertisedLargePreviewWithoutAddingItAsAFile() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    return page(field(body, "StartingIndex"), field(body, "RequestedCount"), 1)
                }
            }
            server.start()
            val result = client(server).browseNewest(onProgress = {}) as LumixResult.Success
            assertThat(result.value).hasSize(1)
            assertThat(result.value.single().largePreviewPath).isEqualTo("http://192.168.54.1:50001/DL11000001.JPG")
        }
    }
    @Test fun sameFileNamesInDifferentResourcesAreNotLost() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val response = page(field(body, "StartingIndex"), field(body, "RequestedCount"), 103)
                    return response.setBody(response.getBody()!!.readUtf8().replace(Regex("P100[0-9]{4}\\.JPG"), "PANA0001.JPG"))
                }
            }
            server.start()
            val result = client(server).browseNewest(onProgress = {}) as LumixResult.Success
            assertThat(result.value).hasSize(103)
            assertThat(result.value.map { it.name }.distinct()).hasSize(1)
        }
    }

    @Test fun positiveObjectCountWithoutDownloadableFilesStillAdvancesScan() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    val response = page(start, field(body, "RequestedCount"), 100)
                    if (start == 0) return response.setBody(response.getBody()!!.readUtf8()
                        .replace(Regex("&lt;res\\b.*?&lt;/res&gt;"), ""))
                    return response
                }
            }
            server.start()
            val progress = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { progress += it }) as LumixResult.Success
            assertThat(result.value).hasSize(50)
            assertThat(progress.last().scannedCount).isEqualTo(100)
            assertThat(progress.last().complete).isTrue()
        }
    }

    @Test fun transientEmptyWindowRetriesSameOffsetWithoutSkippingPhotos() = runBlocking {
        MockWebServer().use { server ->
            var interrupted = false
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    val start = field(body, "StartingIndex")
                    if (start == 53 && !interrupted) {
                        interrupted = true
                        return MockResponse().setBody("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><Result>&lt;DIDL-Lite/&gt;</Result><NumberReturned>0</NumberReturned><TotalMatches>103</TotalMatches></s:Body></s:Envelope>""")
                    }
                    return page(start, field(body, "RequestedCount"), 103)
                }
            }
            server.start()
            val progress = mutableListOf<CameraBrowseProgress>()
            val result = client(server).browseNewest(onProgress = { progress += it }) as LumixResult.Success
            assertThat(interrupted).isTrue()
            assertThat(result.value).hasSize(103)
            assertThat(progress.last().complete).isTrue()
            assertThat(progress.last().scannedCount).isEqualTo(103)
        }
    }

    private fun client(server: MockWebServer) = UpnpContentDirectoryClient(
        baseUrl = "http://127.0.0.1", soapUrlOverride = server.url("/Server0/CDS_control").toString(),
        tryLumixPrefix = false, sendProtocolInfo = false,
    )

    private fun field(body: String, name: String) = Regex("<$name>([0-9]+)</$name>")
        .find(body)!!.groupValues[1].toInt()

    private fun page(start: Int, count: Int, total: Int): MockResponse {
        val items = (start until minOf(start + count, total)).joinToString("") { index ->
            val n = (index + 1).toString().padStart(4, '0')
            """<item id="1100$n"><res protocolInfo="http-get:*:image/jpeg:PANASONIC.COM_PN=CAM_ORG;OriginalFileName='P100$n.JPG'" size="123">http://192.168.54.1:50001/DO1100$n.JPG</res><res protocolInfo="http-get:*:image/jpeg:PANASONIC.COM_PN=CAM_TN" size="5000">http://192.168.54.1:50001/DT1100$n.JPG</res><res protocolInfo="http-get:*:image/jpeg:PANASONIC.COM_PN=CAM_LRGTN" size="400000">http://192.168.54.1:50001/DL1100$n.JPG</res></item>"""
        }
        val didl = "<DIDL-Lite>$items</DIDL-Lite>".replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return MockResponse().setBody("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><Result>$didl</Result><NumberReturned>${minOf(count, maxOf(0, total-start))}</NumberReturned><TotalMatches>$total</TotalMatches></s:Body></s:Envelope>""")
    }
}
