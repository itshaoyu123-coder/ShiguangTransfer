package com.lumalink.g100d.protocol

import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element

/**
 * Resolves the real ContentDirectory control URL from the camera's UPnP device
 * description. Some firmware returns 403 for the guessed /Server0/CDS_control
 * path, so we probe the description documents first. All requests are GETs.
 */
class UpnpDescriptionResolver(
    baseUrl: String,
    private val descriptionUrlsOverride: List<String>? = null,
    private val client: OkHttpClient = defaultClient(),
    private val logger: LumixLogger? = null,
    private val ssdp: UpnpSsdpDiscovery? = UpnpSsdpDiscovery(),
    private val xmlFactory: () -> DocumentBuilderFactory = { DocumentBuilderFactory.newInstance() },
) {
    private val host = baseUrl.toHttpUrl().host
    var connectionManagerControlUrl: String? = null
        private set

    suspend fun resolve(): LumixResult<String> = withContext(Dispatchers.IO) {
        if (!HostGuard.isLocalHost(host)) {
            return@withContext LumixResult.Failure(
                LumixError.ReadOnlyViolation("non-local UPnP host: $host"),
            )
        }
        val candidates = descriptionUrlsOverride ?: listOf(
            "http://$host:60606/Server0/description.xml",
            "http://$host:60606/Server0/",
            "http://$host:60606/description.xml",
            "http://$host:60606/",
        )
        var lastError: LumixError? = null
        for (url in candidates) {
            val request = try {
                Request.Builder().url(url).get().build()
            } catch (error: IllegalArgumentException) {
                continue
            }
            if (!HostGuard.isLocalHost(request.url.host)) continue
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    logger?.log(
                        LumixHttpLogEntry(
                            timestamp = System.currentTimeMillis(),
                            method = "GET",
                            url = url,
                            statusCode = response.code,
                            durationMs = 0,
                        ),
                    )
                    logger?.logBody(
                        url,
                        "HTTP ${response.code} headers: ${response.headers}\n$body",
                    )
                    if (!response.isSuccessful) {
                        lastError = LumixError.ProtocolMismatch(
                            "UPnP description HTTP ${response.code} at $url",
                        )
                        return@use
                    }
                    parseServices(body, url)?.let { services ->
                        connectionManagerControlUrl = services.connectionManager
                        return@withContext LumixResult.Success(services.contentDirectory)
                    }
                    lastError = LumixError.ProtocolMismatch(
                        "No ContentDirectory controlURL in $url",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = LumixError.Offline(
                    error.message ?: "UPnP description failed",
                    error,
                )
            }
        }
        if (descriptionUrlsOverride == null && ssdp != null) {
            when (val location = ssdp.discoverLocation()) {
                is LumixResult.Success -> {
                    logger?.logBody("upnp-ssdp", "LOCATION=${location.value}")
                    val request = runCatching {
                        Request.Builder().url(location.value).get().build()
                    }.getOrNull()
                    if (request != null && HostGuard.isLocalHost(request.url.host)) {
                        try {
                            client.newCall(request).execute().use { response ->
                                val body = response.body?.string().orEmpty()
                                logger?.logBody(
                                    location.value,
                                    "HTTP ${response.code} headers: ${response.headers}\n$body",
                                )
                                if (response.isSuccessful) {
                                    parseServices(body, location.value)?.let { services ->
                                        connectionManagerControlUrl = services.connectionManager
                                        return@withContext LumixResult.Success(services.contentDirectory)
                                    }
                                    lastError = LumixError.ProtocolMismatch("No ContentDirectory controlURL in ${location.value}")
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            lastError = LumixError.Offline(
                                error.message ?: "SSDP description failed",
                                error,
                            )
                        }
                    }
                }

                is LumixResult.Failure -> {
                    logger?.logBody("upnp-ssdp", "failed: ${location.error.message}")
                }
            }
        }

        LumixResult.Failure(
            lastError ?: LumixError.ProtocolMismatch("UPnP description unavailable"),
        )
    }

    private data class Services(val contentDirectory: String, val connectionManager: String?)

    private fun parseServices(xml: String, baseUrl: String): Services? {
        if (CameraXml.hasUnsafeDeclaration(xml)) return null
        val document = CameraXml.parse(xml, xmlFactory)
        fun textTag(block: String, name: String): String? = Regex(
            """<(?:[\w-]+:)?${Regex.escape(name)}\b[^>]*>\s*([^<]+?)\s*</(?:[\w-]+:)?${Regex.escape(name)}>""",
            RegexOption.IGNORE_CASE,
        ).find(block)?.groupValues?.getOrNull(1)?.trim()?.replace("&amp;", "&")
        val urlBase = document?.getElementsByTagName("*")?.asElementSequence()
            ?.firstOrNull { it.localName == "URLBase" || it.tagName == "URLBase" }
            ?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: textTag(xml, "URLBase")
        val rootBase = urlBase ?: baseUrl
        val services = document?.getElementsByTagName("*")?.asElementSequence()
            ?.filter { it.localName == "service" || it.tagName == "service" || it.tagName.endsWith(":service") }
            ?.mapNotNull { service ->
                val type = childText(service, "serviceType") ?: return@mapNotNull null
                val control = childText(service, "controlURL") ?: return@mapNotNull null
                type to control
            }?.toList().orEmpty().toMutableList()
        if (services.isEmpty()) {
            // This fallback must remain reachable when the platform XML provider fails.
            Regex("""<(?:[\w-]+:)?service\b[^>]*>(.*?)</(?:[\w-]+:)?service>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(xml).forEach { match ->
                    val type = textTag(match.groupValues[1], "serviceType")
                    val control = textTag(match.groupValues[1], "controlURL")
                    if (type != null && control != null) services += type to control
                }
        }
        fun controlFor(serviceName: String): String? {
            val path = services.firstOrNull { it.first.contains(":$serviceName:", ignoreCase = true) }?.second ?: return null
            return runCatching { rootBase.toHttpUrl().resolve(path)?.takeIf { HostGuard.isLocalHost(it.host) }?.toString() }.getOrNull()
        }
        val content = controlFor("ContentDirectory") ?: return null
        return Services(content, controlFor("ConnectionManager"))
    }

    private fun childText(element: Element, name: String): String? {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.localName == name || child.tagName == name ||
                child.tagName.endsWith(":$name")
            ) {
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun org.w3c.dom.NodeList.asElementSequence(): Sequence<Element> =
        (0 until length).asSequence().mapNotNull { item(it) as? Element }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
