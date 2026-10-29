@file:Suppress("DEPRECATION")
package com.zdkserials

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

object ZdkExtractor {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    suspend fun resolveAndExtract(
        htmlOrUrl: String,
        baseUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false
        val candidateUrls = mutableSetOf<String>()

        if (htmlOrUrl.startsWith("http://") || htmlOrUrl.startsWith("https://")) {
            candidateUrls.add(htmlOrUrl)
        } else {
            val doc = Jsoup.parse(htmlOrUrl, baseUrl)

            // 1. Iframes (default player frame or other iframes)
            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").ifBlank {
                    iframe.attr("data-src").ifBlank {
                        iframe.attr("data-lazy-src")
                    }
                }
                if (src.isNotBlank() && !src.startsWith("about:")) {
                    candidateUrls.add(resolveRelativeUrl(src, baseUrl))
                }
            }

            // 2. Server switch buttons (.zdk-server data-src)
            doc.select(".zdk-server, button[data-src], [data-server]").forEach { el ->
                val src = el.attr("data-src").ifBlank { el.attr("data-server") }
                if (src.isNotBlank()) {
                    candidateUrls.add(resolveRelativeUrl(src, baseUrl))
                }
            }

            // 3. Anchor links
            doc.select("a[href]").forEach { a ->
                val href = a.attr("abs:href").ifBlank { a.attr("href") }
                if (isVideoHost(href)) {
                    candidateUrls.add(resolveRelativeUrl(href, baseUrl))
                }
            }
        }

        for (url in candidateUrls) {
            try {
                // Priority A: UpBolt
                if (url.contains("upbolt", ignoreCase = true)) {
                    if (extractUpbolt(url, callback)) {
                        foundAny = true
                        continue
                    }
                }

                // Priority B: WexioStream
                if (url.contains("wexiostream", ignoreCase = true)) {
                    if (extractWexioStream(url, callback)) {
                        foundAny = true
                        continue
                    }
                }

                // Priority C: Built-in CloudStream extractors
                val loaded = loadExtractor(url, baseUrl, subtitleCallback, callback)
                if (loaded) {
                    foundAny = true
                } else if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                    val isM3u8 = url.contains(".m3u8", ignoreCase = true)
                    callback(
                        newExtractorLink(
                            source = "ZDK Direct",
                            name = if (isM3u8) "Direct HLS" else "Direct MP4",
                            url = url,
                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = baseUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    foundAny = true
                }
            } catch (_: Exception) {
                // Keep trying other candidates
            }
        }

        return foundAny
    }

    private suspend fun extractUpbolt(
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val fileCode = url.substringAfterLast("/e/").substringBefore("/").substringBefore("?")
            if (fileCode.isBlank()) return false

            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to url,
                "Origin" to "https://upbolt.to"
            )

            val formData = mapOf(
                "op" to "embed",
                "file_code" to fileCode,
                "auto" to "1",
                "referer" to "https://zdkserials.site/"
            )

            val response = app.post("https://upbolt.to/dl", headers = headers, data = formData).text
            val m3u8Match = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(response)
            val m3u8Url = m3u8Match?.value ?: return false

            var generated = false
            try {
                val links = M3u8Helper.generateM3u8(
                    source = "UpBolt",
                    streamUrl = m3u8Url,
                    referer = "https://upbolt.to/",
                    headers = mapOf("Referer" to "https://upbolt.to/")
                )
                if (links.isNotEmpty()) {
                    links.forEach(callback)
                    generated = true
                }
            } catch (_: Exception) {}

            if (!generated) {
                callback(
                    newExtractorLink(
                        source = "UpBolt",
                        name = "UpBolt HLS",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://upbolt.to/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun extractWexioStream(
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val token = url.substringAfterLast("/e/").substringBefore("/").substringBefore("?")
            if (token.isBlank()) return false

            // Step 1: GET embed page to find any initial context token
            val embedRes = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "https://zdkserials.site/")).text
            val tokenMatch = Regex(""""token"\s*:\s*"([^"]+)"""").find(embedRes)
            val initialEmbedContext = tokenMatch?.groupValues?.get(1)

            val pageViewId = UUID.randomUUID().toString()
            val idempotencyKey = UUID.randomUUID().toString()

            // Step 2: POST /watch-sessions
            val sessionHeaders = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Content-Type" to "application/json",
                "Idempotency-Key" to idempotencyKey,
                "Origin" to "https://wexiostream.com",
                "Referer" to url
            )
            if (initialEmbedContext != null) {
                sessionHeaders["X-Embed-Context"] = initialEmbedContext
            }

            val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()
            val sessionPayload = """{"schema_version":1,"page_view_id":"$pageViewId","surface":"embed","source_origin":"https://zdkserials.site"}"""
            val sessionRes = app.post(
                "https://wexiostream.com/api/media/$token/watch-sessions",
                headers = sessionHeaders,
                requestBody = sessionPayload.toRequestBody(jsonMedia)
            ).text
            val sessionId = Regex(""""session_id"\s*:\s*"([^"]+)"""").find(sessionRes)?.groupValues?.get(1) ?: return false
            val embedContext = Regex(""""embed_context_token"\s*:\s*"([^"]+)"""").find(sessionRes)?.groupValues?.get(1) ?: initialEmbedContext

            // Step 3: POST /playback-grant
            val grantHeaders = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Content-Type" to "application/json",
                "Idempotency-Key" to UUID.randomUUID().toString(),
                "Origin" to "https://wexiostream.com",
                "Referer" to url
            )
            if (embedContext != null) {
                grantHeaders["X-Embed-Context"] = embedContext
            }

            val grantPayload = """{"schema_version":1,"page_view_id":"$pageViewId","surface":"embed"}"""
            val grantRes = app.post(
                "https://wexiostream.com/api/media/$token/watch-sessions/$sessionId/playback-grant",
                headers = grantHeaders,
                requestBody = grantPayload.toRequestBody(jsonMedia)
            ).text
            val hlsUrl = Regex(""""hls_url"\s*:\s*"([^"]+)"""").find(grantRes)?.groupValues?.get(1) ?: return false

            var generated = false
            try {
                val links = M3u8Helper.generateM3u8(
                    source = "WexioStream",
                    streamUrl = hlsUrl,
                    referer = "https://wexiostream.com/",
                    headers = mapOf("Referer" to "https://wexiostream.com/")
                )
                if (links.isNotEmpty()) {
                    links.forEach(callback)
                    generated = true
                }
            } catch (_: Exception) {}

            if (!generated) {
                callback(
                    newExtractorLink(
                        source = "WexioStream",
                        name = "WexioStream HLS",
                        url = hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://wexiostream.com/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveRelativeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl.trimEnd('/') + url
            !url.startsWith("http://") && !url.startsWith("https://") -> baseUrl.trimEnd('/') + "/" + url
            else -> url
        }
    }

    private fun isVideoHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("upbolt") ||
                lower.contains("wexio") ||
                lower.contains("stream") ||
                lower.contains("embed") ||
                lower.contains(".m3u8") ||
                lower.contains(".mp4")
    }
}
