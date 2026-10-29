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
        val candidateUrls = LinkedHashSet<String>()

        if (htmlOrUrl.startsWith("http://") || htmlOrUrl.startsWith("https://")) {
            candidateUrls.add(htmlOrUrl)
            // If it's a zdkserials watch page or show page, fetch its HTML to find embeds
            if (htmlOrUrl.contains("zdkserials.site", ignoreCase = true)) {
                try {
                    val pageHtml = app.get(htmlOrUrl, headers = mapOf("User-Agent" to USER_AGENT)).text
                    extractCandidateUrlsFromHtml(pageHtml, htmlOrUrl, candidateUrls)
                } catch (_: Exception) {}
            }
        } else {
            extractCandidateUrlsFromHtml(htmlOrUrl, baseUrl, candidateUrls)
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

                // Priority C: Direct HLS or MP4 streams
                if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                    val isM3u8 = url.contains(".m3u8", ignoreCase = true)
                    val streamHeaders = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to baseUrl
                    )
                    if (isM3u8) {
                        try {
                            val links = M3u8Helper.generateM3u8(
                                source = "ZDK Direct",
                                streamUrl = url,
                                referer = baseUrl,
                                headers = streamHeaders
                            )
                            if (links.isNotEmpty()) {
                                links.forEach(callback)
                                foundAny = true
                            }
                        } catch (_: Exception) {}
                    }
                    callback(
                        newExtractorLink(
                            source = "ZDK Direct",
                            name = if (isM3u8) "Direct HLS" else "Direct MP4",
                            url = url,
                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = baseUrl
                            this.quality = Qualities.Unknown.value
                            this.headers = streamHeaders
                        }
                    )
                    foundAny = true
                    continue
                }

                // Priority D: Standard CloudStream extractors
                val loaded = loadExtractor(url, baseUrl, subtitleCallback, callback)
                if (loaded) {
                    foundAny = true
                    continue
                }

                // Priority E: If it's a third-party embed page, fetch and look for nested player
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        val nestedHtml = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to baseUrl)).text
                        val nestedCandidates = LinkedHashSet<String>()
                        extractCandidateUrlsFromHtml(nestedHtml, url, nestedCandidates)
                        for (nested in nestedCandidates) {
                            if (nested == url) continue
                            if (nested.contains("upbolt", ignoreCase = true) && extractUpbolt(nested, callback)) {
                                foundAny = true
                            } else if (nested.contains("wexiostream", ignoreCase = true) && extractWexioStream(nested, callback)) {
                                foundAny = true
                            } else if (loadExtractor(nested, url, subtitleCallback, callback)) {
                                foundAny = true
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                // Keep trying other candidates
            }
        }

        return foundAny
    }

    private fun extractCandidateUrlsFromHtml(html: String, baseUrl: String, candidates: MutableSet<String>) {
        val doc = Jsoup.parse(html, baseUrl)

        // 1. Iframes (default player frame or other iframes)
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").ifBlank {
                iframe.attr("data-src").ifBlank {
                    iframe.attr("data-lazy-src")
                }
            }
            if (src.isNotBlank() && !src.startsWith("about:")) {
                candidates.add(resolveRelativeUrl(src, baseUrl))
            }
        }

        // 2. Player container or server switch buttons (#zdk-frame, .zdk-server, data-src)
        doc.select("#zdk-frame, .zdk-server, button[data-src], [data-server]").forEach { el ->
            val src = el.attr("src").ifBlank {
                el.attr("data-src").ifBlank { el.attr("data-server") }
            }
            if (src.isNotBlank() && !src.startsWith("about:")) {
                candidates.add(resolveRelativeUrl(src, baseUrl))
            }
        }

        // 3. Anchor links to video hosts
        doc.select("a[href]").forEach { a ->
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (isVideoHost(href)) {
                candidates.add(resolveRelativeUrl(href, baseUrl))
            }
        }

        // 4. Regex scan for UpBolt / WexioStream / stream links anywhere in HTML/scripts
        val upboltRegex = Regex("""https?://(?:www\.)?upbolt\.(?:to|com|cc)/e/[a-zA-Z0-9_-]+""", RegexOption.IGNORE_CASE)
        upboltRegex.findAll(html).forEach { match ->
            candidates.add(match.value)
        }

        val wexioRegex = Regex("""https?://(?:www\.)?wexiostream\.com/e/[a-zA-Z0-9_-]+""", RegexOption.IGNORE_CASE)
        wexioRegex.findAll(html).forEach { match ->
            candidates.add(match.value)
        }

        val m3u8Regex = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*""", RegexOption.IGNORE_CASE)
        m3u8Regex.findAll(html).forEach { match ->
            candidates.add(match.value)
        }
    }

    private suspend fun extractUpbolt(
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val fileCode = url.substringAfterLast("/e/").substringBefore("/").substringBefore("?")
            if (fileCode.isBlank()) return false

            // Step 1: GET embed page to capture anti-bot / initial cookies (__ddg...)
            val getHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://zdkserials.site/"
            )
            val getRes = try {
                app.get(url, headers = getHeaders)
            } catch (_: Exception) { null }

            val cookies = mutableMapOf<String, String>()
            getRes?.cookies?.forEach { (k, v) -> cookies[k] = v }

            // Extract any Set-Cookie headers as backup
            getRes?.headers?.values("Set-Cookie")?.forEach { cookieHeaderVal ->
                val cookiePair = cookieHeaderVal.substringBefore(";").trim()
                if (cookiePair.contains("=")) {
                    val k = cookiePair.substringBefore("=").trim()
                    val v = cookiePair.substringAfter("=").trim()
                    if (k.isNotBlank()) cookies[k] = v
                }
            }

            // Step 2: POST /dl with captured cookies
            val postHeaders = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to url,
                "Origin" to "https://upbolt.to"
            )
            val cookieStr1 = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookieStr1.isNotBlank()) {
                postHeaders["Cookie"] = cookieStr1
            }

            val formData = mapOf(
                "op" to "embed",
                "file_code" to fileCode,
                "auto" to "1",
                "referer" to "https://zdkserials.site/"
            )

            val postRes = app.post("https://upbolt.to/dl", headers = postHeaders, data = formData)
            postRes.cookies.forEach { (k, v) -> cookies[k] = v }
            postRes.headers.values("Set-Cookie").forEach { cookieHeaderVal ->
                val cookiePair = cookieHeaderVal.substringBefore(";").trim()
                if (cookiePair.contains("=")) {
                    val k = cookiePair.substringBefore("=").trim()
                    val v = cookiePair.substringAfter("=").trim()
                    if (k.isNotBlank()) cookies[k] = v
                }
            }

            val responseText = postRes.text
            val m3u8Match = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(responseText)
            val m3u8Url = m3u8Match?.value ?: return false

            // Step 3: Construct comprehensive headers including the session cookies
            // Without these cookies ExoPlayer gets HTTP 403 Forbidden (Remote Error 2004)
            val fullCookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val streamHeaders = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://upbolt.to/",
                "Origin" to "https://upbolt.to"
            )
            if (fullCookieHeader.isNotBlank()) {
                streamHeaders["Cookie"] = fullCookieHeader
            }

            var generated = false
            try {
                val links = M3u8Helper.generateM3u8(
                    source = "UpBolt",
                    streamUrl = m3u8Url,
                    referer = "https://upbolt.to/",
                    headers = streamHeaders
                )
                if (links.isNotEmpty()) {
                    links.forEach { link ->
                        callback(
                            newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = link.url,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "https://upbolt.to/"
                                this.quality = link.quality
                                this.headers = streamHeaders
                            }
                        )
                    }
                    generated = true
                }
            } catch (_: Exception) {}

            // ALWAYS also emit direct master m3u8 as Auto quality
            callback(
                newExtractorLink(
                    source = "UpBolt",
                    name = "UpBolt - Auto",
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://upbolt.to/"
                    this.quality = Qualities.Unknown.value
                    this.headers = streamHeaders
                }
            )
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

            val streamHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://wexiostream.com/",
                "Origin" to "https://wexiostream.com"
            )

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
                    headers = streamHeaders
                )
                if (links.isNotEmpty()) {
                    links.forEach { link ->
                        callback(
                            newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = link.url,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "https://wexiostream.com/"
                                this.quality = link.quality
                                this.headers = streamHeaders
                            }
                        )
                    }
                    generated = true
                }
            } catch (_: Exception) {}

            callback(
                newExtractorLink(
                    source = "WexioStream",
                    name = "WexioStream - Auto",
                    url = hlsUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://wexiostream.com/"
                    this.quality = Qualities.Unknown.value
                    this.headers = streamHeaders
                }
            )
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
                lower.contains("player") ||
                lower.contains(".m3u8") ||
                lower.contains(".mp4")
    }
}
