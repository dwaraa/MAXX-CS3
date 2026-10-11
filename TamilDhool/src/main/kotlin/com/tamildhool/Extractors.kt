package com.tamildhool

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

object TamilDhoolExtractor {

    suspend fun resolveAndExtract(
        elementOrHtml: String,
        baseUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false
        val doc = org.jsoup.Jsoup.parse(elementOrHtml, baseUrl)

        // 1. Gather all iframe sources
        val iframeUrls = mutableSetOf<String>()
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").ifBlank {
                iframe.attr("data-src").ifBlank {
                    iframe.attr("data-lazy-src")
                }
            }
            if (src.isNotBlank() && !src.startsWith("about:")) {
                iframeUrls.add(normalizeUrl(src, baseUrl))
            }
        }

        // 2. Gather links from buttons or anchors
        doc.select("a[href]").forEach { a ->
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (isVideoHost(href)) {
                iframeUrls.add(normalizeUrl(href, baseUrl))
            }
        }

        // 3. Fallback regex to capture any embed URLs hidden in javascript or text
        val urlRegex = Regex("""https?://[^\s"'<>\\]+""")
        urlRegex.findAll(elementOrHtml).forEach { match ->
            val matchedUrl = match.value
            if (isVideoHost(matchedUrl)) {
                iframeUrls.add(matchedUrl)
            }
        }

        // 4. Process each detected URL
        for (url in iframeUrls) {
            try {
                if (extractCustomPlayer(url, baseUrl, subtitleCallback, callback)) {
                    foundAny = true
                    continue
                }

                // Use Cloudstream universal extractor engine
                val loaded = loadExtractor(url, baseUrl, subtitleCallback, callback)
                if (loaded) {
                    foundAny = true
                } else if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
                    callback(
                        ExtractorLink(
                            source = "TamilDhool Direct",
                            name = if (url.contains(".m3u8")) "HLS Stream" else "MP4 Video",
                            url = url,
                            referer = baseUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = url.contains(".m3u8")
                        )
                    )
                    foundAny = true
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
        // Handle PlayVK / VK embeds
        if (url.contains("playvk.com", ignoreCase = true) || url.contains("vk.com/video_ext.php", ignoreCase = true)) {
            return extractPlayVk(url, referer, callback)
        }

        // Handle internal TamilDhool player wrappers (e.g. player.php, embed.php)
        if (url.contains("/player.php") || url.contains("/embed.php") || url.contains("/play.php")) {
            return extractInternalPlayer(url, referer, subtitleCallback, callback)
        }

        return false
    }

    private suspend fun extractPlayVk(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val res = app.get(url, referer = referer).text

            // Check for direct video qualities in VK player JS
            // e.g. "url720":"https:\/\/..." or "url480":"https:\/\/..."
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
                    callback(
                        ExtractorLink(
                            source = "PlayVK",
                            name = "VK Video ${quality}p",
                            url = streamUrl,
                            referer = url,
                            quality = quality,
                            isM3u8 = streamUrl.contains(".m3u8")
                        )
                    )
                    extracted = true
                }
            }

            // Check for HLS source
            val hlsMatch = Regex("""\"hls\":\"(https?:[^\"]+)\"""").find(res)
            if (hlsMatch != null) {
                val hlsUrl = hlsMatch.groupValues[1].replace("\\/", "/")
                callback(
                    ExtractorLink(
                        source = "PlayVK HLS",
                        name = "VK Adaptive HLS",
                        url = hlsUrl,
                        referer = url,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
                extracted = true
            }

            extracted
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun extractInternalPlayer(
        playerUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val html = app.get(playerUrl, referer = referer).text
            resolveAndExtract(html, playerUrl, subtitleCallback, callback)
        } catch (_: Exception) {
            false
        }
    }

    private fun isVideoHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("dailymotion.com") ||
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
                lower.contains(".m3u8") ||
                lower.contains(".mp4")
    }

    private fun normalizeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val uri = java.net.URI(baseUrl)
                "${uri.scheme}://${uri.host}$url"
            }
            !url.startsWith("http://") && !url.startsWith("https://") -> {
                "https://$url"
            }
            else -> url
        }
    }
}
