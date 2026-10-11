@file:Suppress("DEPRECATION")
package com.tamildhool

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder

object TamilDhoolExtractor {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    suspend fun resolveAndExtract(
        elementOrHtml: String,
        baseUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false
        val doc = Jsoup.parse(elementOrHtml, baseUrl)

        // 1. Gather all iframe sources
        val candidateUrls = mutableSetOf<String>()
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

        // 2. Gather links from buttons or anchors
        doc.select("a[href]").forEach { a ->
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (isVideoHostOrResolver(href)) {
                candidateUrls.add(resolveRelativeUrl(href, baseUrl))
            }
        }

        // 3. Fallback regex to capture any embed URLs hidden in javascript or text
        val urlRegex = Regex("""https?://[^\s"'<>\\]+""")
        urlRegex.findAll(elementOrHtml).forEach { match ->
            val matchedUrl = match.value
            if (isVideoHostOrResolver(matchedUrl)) {
                candidateUrls.add(matchedUrl)
            }
        }

        // 4. Process each detected URL
        for (url in candidateUrls) {
            try {
                // Priority A: Custom player & redirect resolvers (teamstoday, vibrantpulse, mediabunny, playvk)
                if (extractCustomPlayer(url, baseUrl, subtitleCallback, callback)) {
                    foundAny = true
                    continue
                }

                // Priority B: Cloudstream universal extractor engine (Dailymotion, YouTube, etc.)
                val loaded = loadExtractor(url, baseUrl, subtitleCallback, callback)
                if (loaded) {
                    foundAny = true
                } else if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                    val isM3u8 = url.contains(".m3u8", ignoreCase = true)
                    callback(
                        newExtractorLink(
                            source = "TamilDhool Direct",
                            name = if (isM3u8) "HLS Stream" else "MP4 Video",
                            url = url,
                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = baseUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    foundAny = true
                } else {
                    // Priority C: Attempt redirect chain resolution if standard extractor did not resolve
                    if (resolveRedirectChainAndExtract(url, baseUrl, subtitleCallback, callback)) {
                        foundAny = true
                    }
                }
            } catch (_: Exception) {
                // Continue trying other candidate links
            }
        }

        return foundAny
    }

    private suspend fun extractCustomPlayer(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Handle teamstoday.com redirect chain
        if (url.contains("teamstoday.com", ignoreCase = true)) {
            return resolveRedirectChainAndExtract(url, referer, subtitleCallback, callback)
        }

        // Handle vibrantpulse / mediabunny redirect or embed players
        if (url.contains("vibrantpulse", ignoreCase = true) ||
            url.contains("mediabunny", ignoreCase = true)
        ) {
            return resolveRedirectChainAndExtract(url, referer, subtitleCallback, callback)
        }

        // Handle PlayVK / VK embeds
        if (url.contains("playvk.com", ignoreCase = true) || url.contains("vk.com/video_ext.php", ignoreCase = true)) {
            return extractPlayVk(url, referer, callback)
        }

        // Handle internal TamilDhool player wrappers (e.g. player.php, embed.php, play.php)
        if (url.contains("/player.php") || url.contains("/embed.php") || url.contains("/play.php")) {
            return resolveRedirectChainAndExtract(url, referer, subtitleCallback, callback)
        }

        return false
    }

    /**
     * Follows redirect hops (HTTP 3xx, meta refresh, JS location redirect)
     * and extracts streams from the player embed page.
     */
    private suspend fun resolveRedirectChainAndExtract(
        initialUrl: String,
        initialReferer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var current = initialUrl
        var referer = initialReferer
        val visited = mutableSetOf<String>()
        var embedUrl = ""
        var finalReferer = initialReferer

        for (hop in 0 until 15) {
            if (!visited.add(current)) break // Avoid infinite redirect loops

            val response = try {
                app.get(
                    current,
                    referer = referer,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.9"
                    ),
                    allowRedirects = false
                )
            } catch (_: Exception) {
                break
            }

            val status = response.code
            val location = response.headers["Location"] ?: response.headers["location"]

            // 1. HTTP 3xx redirect
            if (status in 300..399 && !location.isNullOrBlank()) {
                val nextUrl = resolveRelativeUrl(location, current)
                referer = current
                current = nextUrl
                continue
            }

            // 2. HTTP 200 response
            if (status == 200) {
                val html = response.text

                // Check for player iframe candidate FIRST
                val iframeCandidate = findCandidateIframe(html, current)
                if (iframeCandidate != null) {
                    embedUrl = iframeCandidate
                    finalReferer = current
                    break
                }

                // Check HTML meta-refresh redirect: <meta http-equiv="refresh" content="...url=...">
                val metaMatch = Regex("""url=([^\s"'>]+)""", RegexOption.IGNORE_CASE).find(html)
                if (metaMatch != null) {
                    val rawUrl = try {
                        URLDecoder.decode(metaMatch.groupValues[1], "UTF-8")
                    } catch (_: Exception) {
                        metaMatch.groupValues[1]
                    }
                    referer = current
                    current = resolveRelativeUrl(rawUrl, current)
                    continue
                }

                // Check JavaScript location redirect: location.href = "...", location.replace("..."), etc.
                val jsMatch = Regex(
                    """location(?:\.href|\.replace\s*\(|\s*=)\s*['"]([^'"]+)['"]""",
                    RegexOption.IGNORE_CASE
                ).find(html)
                if (jsMatch != null) {
                    val rawUrl = try {
                        URLDecoder.decode(jsMatch.groupValues[1], "UTF-8")
                    } catch (_: Exception) {
                        jsMatch.groupValues[1]
                    }
                    referer = current
                    current = resolveRelativeUrl(rawUrl, current)
                    continue
                }

                // If no redirect or candidate iframe found, check if this page itself has video streams
                finalReferer = current
                embedUrl = current
                break
            }
            break
        }

        if (embedUrl.isNotBlank()) {
            return extractFromEmbedPage(embedUrl, finalReferer, subtitleCallback, callback)
        }
        return false
    }

    private fun findCandidateIframe(html: String, currentUrl: String): String? {
        val doc = Jsoup.parse(html, currentUrl)
        for (iframe in doc.select("iframe")) {
            val src = iframe.attr("src").ifBlank {
                iframe.attr("data-src").ifBlank {
                    iframe.attr("data-lazy-src")
                }
            }
            if (src.isNotBlank() && !src.startsWith("about:")) {
                val lower = src.lowercase()
                if (lower.contains("vimeo") ||
                    lower.contains("jwplayer") ||
                    lower.contains("vibrantpulse") ||
                    lower.contains("player.") ||
                    lower.contains("embed.") ||
                    lower.contains("mediabunny") ||
                    lower.contains("dailymotion") ||
                    lower.contains("playvk")
                ) {
                    return resolveRelativeUrl(src, currentUrl)
                }
            }
        }
        return null
    }

    /**
     * Inspects the resolved player embed page and extracts stream links (JWPlayer, HLS, MP4, etc.).
     */
    private suspend fun extractFromEmbedPage(
        embedUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false

        // 1. Try standard Cloudstream extractors first
        try {
            if (loadExtractor(embedUrl, referer, subtitleCallback, callback)) {
                return true
            }
        } catch (_: Exception) {
            // Proceed with custom extraction
        }

        // 2. Fetch the embed page HTML
        val embedHtml = try {
            app.get(
                embedUrl,
                referer = referer,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
            ).text
        } catch (_: Exception) {
            return false
        }

        // 3. JWPlayer playlist variable: playlist = "..." or playlist: "..."
        val playlistMatch = Regex("""playlist\s*(?:=|\:)\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(embedHtml)
        if (playlistMatch != null) {
            val streamUrl = playlistMatch.groupValues[1].replace("\\/", "/")
            val resolvedStream = resolveRelativeUrl(streamUrl, embedUrl)
            callback(
                newExtractorLink(
                    source = "JWPlayer HLS",
                    name = "Tamil Stream (Adaptive HLS)",
                    url = resolvedStream,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = embedUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            found = true
        }

        // 4. JWPlayer file / source / src: "..." or sources: [{ file: "..." }]
        val fileMatch = Regex("""(?:file|source|src)\s*:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(embedHtml)
        if (fileMatch != null) {
            val streamUrl = fileMatch.groupValues[1].replace("\\/", "/")
            if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://") || streamUrl.startsWith("/")) {
                val resolvedStream = resolveRelativeUrl(streamUrl, embedUrl)
                val isM3u8 = resolvedStream.contains(".m3u8", ignoreCase = true)
                callback(
                    newExtractorLink(
                        source = if (isM3u8) "JWPlayer HLS" else "JWPlayer MP4",
                        name = if (isM3u8) "Tamil Stream (HLS)" else "Tamil Stream (MP4)",
                        url = resolvedStream,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = embedUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            }
        }

        // 5. Direct .m3u8 or .mp4 streams in scripts/html
        val directStreamRegex = Regex("""https?://[^\s"'<>\)]+\.(?:m3u8|mp4)""", RegexOption.IGNORE_CASE)
        directStreamRegex.findAll(embedHtml).forEach { match ->
            val streamUrl = match.value.replace("\\/", "/")
            val isM3u8 = streamUrl.contains(".m3u8", ignoreCase = true)
            callback(
                newExtractorLink(
                    source = if (isM3u8) "Direct HLS" else "Direct Video",
                    name = if (isM3u8) "HLS Video" else "MP4 Video",
                    url = streamUrl,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = embedUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            found = true
        }

        // 6. Nested Vimeo embed
        val vimeoMatch = Regex("""player\.vimeo\.com/video/(\d+)""", RegexOption.IGNORE_CASE).find(embedHtml)
        if (vimeoMatch != null) {
            val vimeoUrl = "https://player.vimeo.com/video/${vimeoMatch.groupValues[1]}"
            if (loadExtractor(vimeoUrl, embedUrl, subtitleCallback, callback)) {
                found = true
            }
        }

        // 7. Nested Dailymotion embed
        val dailyMatch = Regex("""dailymotion\.com/(?:embed/)?video/([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(embedHtml)
        if (dailyMatch != null) {
            val dailyUrl = "https://www.dailymotion.com/embed/video/${dailyMatch.groupValues[1]}"
            if (loadExtractor(dailyUrl, embedUrl, subtitleCallback, callback)) {
                found = true
            }
        }

        // 8. Nested PlayVK embed
        if (embedHtml.contains("playvk.com") || embedHtml.contains("vk.com/video_ext.php")) {
            val vkMatch = Regex("""https?://(?:playvk\.com|vk\.com/video_ext\.php)[^\s"'<>]+""").find(embedHtml)
            if (vkMatch != null) {
                if (extractPlayVk(vkMatch.value, embedUrl, callback)) {
                    found = true
                }
            }
        }

        return found
    }

    private suspend fun extractPlayVk(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val res = app.get(url, referer = referer).text

            val qualities = listOf(
                "url1080" to Qualities.P1080.value,
                "url720" to Qualities.P720.value,
                "url480" to Qualities.P480.value,
                "url360" to Qualities.P360.value,
                "url240" to Qualities.P240.value
            )

            var extracted = false
            for ((key, quality) in qualities) {
                val match = Regex("""\"$key\":\"(https?:[^\"]+)\"""").find(res)
                if (match != null) {
                    val streamUrl = match.groupValues[1].replace("\\/", "/")
                    val isM3u8 = streamUrl.contains(".m3u8", ignoreCase = true)
                    callback(
                        newExtractorLink(
                            source = "PlayVK",
                            name = "VK Video ${quality}p",
                            url = streamUrl,
                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = url
                            this.quality = quality
                        }
                    )
                    extracted = true
                }
            }

            // Check for HLS source
            val hlsMatch = Regex("""\"hls\":\"(https?:[^\"]+)\"""").find(res)
            if (hlsMatch != null) {
                val hlsUrl = hlsMatch.groupValues[1].replace("\\/", "/")
                callback(
                    newExtractorLink(
                        source = "PlayVK HLS",
                        name = "VK Adaptive HLS",
                        url = hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
                extracted = true
            }

            extracted
        } catch (_: Exception) {
            false
        }
    }

    private fun isVideoHostOrResolver(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("teamstoday.com") ||
                lower.contains("vibrantpulse") ||
                lower.contains("mediabunny") ||
                lower.contains("dailymotion.com") ||
                lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("playvk.com") ||
                lower.contains("vk.com") ||
                lower.contains("streamwish") ||
                lower.contains("filelions") ||
                lower.contains("vidguard") ||
                lower.contains("voe.sx") ||
                lower.contains("dood") ||
                lower.contains("streamtape") ||
                lower.contains("player.php") ||
                lower.contains("embed.php") ||
                lower.contains("play.php") ||
                lower.contains(".m3u8") ||
                lower.contains(".mp4")
    }

    private fun resolveRelativeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> {
                try {
                    val base = URI(baseUrl)
                    base.resolve(url).toString()
                } catch (_: Exception) {
                    if (url.startsWith("/")) {
                        val base = URI(baseUrl)
                        "${base.scheme}://${base.host}$url"
                    } else {
                        url
                    }
                }
            }
        }
    }
}
