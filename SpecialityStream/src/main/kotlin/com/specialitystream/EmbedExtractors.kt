package com.specialitystream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

object EmbedExtractors {

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    )

    suspend fun extractAll(
        tmdbId: Long,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val idStr = tmdbId.toString()
        val isMovie = season == null || episode == null

        // 1. VidSrc Networks
        extractVidSrc(idStr, imdbId, season, episode, isMovie, callback, subtitleCallback)

        // 2. 2Embed
        extract2Embed(idStr, imdbId, season, episode, isMovie, callback, subtitleCallback)

        // 3. AutoEmbed
        extractAutoEmbed(idStr, season, episode, isMovie, callback, subtitleCallback)

        // 4. MultiEmbed / SuperEmbed
        extractMultiEmbed(idStr, season, episode, isMovie, callback, subtitleCallback)

        // 5. SmashyStream
        extractSmashyStream(idStr, season, episode, isMovie, callback, subtitleCallback)

        // 6. MoviesAPI
        extractMoviesApi(idStr, season, episode, isMovie, callback, subtitleCallback)
    }

    private suspend fun extractVidSrc(
        tmdbId: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val domains = listOf(
            "https://vidsrc.net",
            "https://vidsrc.pm",
            "https://vidsrc.cc/v2",
            "https://vidsrc.xyz",
            "https://vidsrc.vip"
        )

        for (domain in domains) {
            val url = if (isMovie) {
                "$domain/embed/movie/$tmdbId"
            } else {
                "$domain/embed/tv/$tmdbId/$season/$episode"
            }

            try {
                val res = app.get(url, headers = defaultHeaders, timeout = 6)
                if (res.code !in 200..299) continue

                val doc = Jsoup.parse(res.text)

                // Check for embedded iframes
                val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
                for (iframe in iframes) {
                    val fullIframe = fixUrl(iframe, domain)
                    loadExtractor(fullIframe, referer = "$domain/", subtitleCallback, callback)
                }

                // Check for direct streams or scripts in page
                extractDirectMediaRegex(res.text, "VidSrc", domain, callback)
            } catch (_: Exception) {
                // Continue to next mirror
            }
        }
    }

    private suspend fun extract2Embed(
        tmdbId: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val targetId = imdbId ?: tmdbId
        val url = if (isMovie) {
            "https://www.2embed.cc/embed/$targetId"
        } else {
            "https://www.2embed.cc/embedtv/$targetId&s=$season&e=$episode"
        }

        try {
            val res = app.get(url, headers = defaultHeaders, timeout = 6)
            if (res.code !in 200..299) return

            val doc = Jsoup.parse(res.text)
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                val fullIframe = fixUrl(iframe, "https://www.2embed.cc")
                loadExtractor(fullIframe, referer = "https://www.2embed.cc/", subtitleCallback, callback)
            }
            extractDirectMediaRegex(res.text, "2Embed", "https://www.2embed.cc/", callback)
        } catch (_: Exception) {
        }
    }

    private suspend fun extractAutoEmbed(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val url = if (isMovie) {
            "https://player.autoembed.cc/embed/movie/$tmdbId"
        } else {
            "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode"
        }

        try {
            val res = app.get(url, headers = defaultHeaders, timeout = 6)
            if (res.code !in 200..299) return

            val doc = Jsoup.parse(res.text)
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                val fullIframe = fixUrl(iframe, "https://player.autoembed.cc")
                loadExtractor(fullIframe, referer = "https://player.autoembed.cc/", subtitleCallback, callback)
            }
            extractDirectMediaRegex(res.text, "AutoEmbed", "https://player.autoembed.cc/", callback)
        } catch (_: Exception) {
        }
    }

    private suspend fun extractMultiEmbed(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val url = if (isMovie) {
            "https://multiembed.mov/?video_id=$tmdbId&tmdb=1"
        } else {
            "https://multiembed.mov/?video_id=$tmdbId&tmdb=1&s=$season&e=$episode"
        }

        try {
            val res = app.get(url, headers = defaultHeaders, timeout = 6)
            if (res.code !in 200..299) return

            val doc = Jsoup.parse(res.text)
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                val fullIframe = fixUrl(iframe, "https://multiembed.mov")
                loadExtractor(fullIframe, referer = "https://multiembed.mov/", subtitleCallback, callback)
            }
            extractDirectMediaRegex(res.text, "MultiEmbed", "https://multiembed.mov/", callback)
        } catch (_: Exception) {
        }
    }

    private suspend fun extractSmashyStream(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val url = if (isMovie) {
            "https://embed.smashystream.com/playere.php?tmdb=$tmdbId"
        } else {
            "https://embed.smashystream.com/playere.php?tmdb=$tmdbId&season=$season&episode=$episode"
        }

        try {
            val res = app.get(url, headers = defaultHeaders, timeout = 6)
            if (res.code !in 200..299) return

            val doc = Jsoup.parse(res.text)
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                val fullIframe = fixUrl(iframe, "https://embed.smashystream.com")
                loadExtractor(fullIframe, referer = "https://embed.smashystream.com/", subtitleCallback, callback)
            }
            extractDirectMediaRegex(res.text, "SmashyStream", "https://embed.smashystream.com/", callback)
        } catch (_: Exception) {
        }
    }

    private suspend fun extractMoviesApi(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val url = if (isMovie) {
            "https://moviesapi.club/movie/$tmdbId"
        } else {
            "https://moviesapi.club/tv/$tmdbId-$season-$episode"
        }

        try {
            val res = app.get(url, headers = defaultHeaders, timeout = 6)
            if (res.code !in 200..299) return

            val doc = Jsoup.parse(res.text)
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                val fullIframe = fixUrl(iframe, "https://moviesapi.club")
                loadExtractor(fullIframe, referer = "https://moviesapi.club/", subtitleCallback, callback)
            }
            extractDirectMediaRegex(res.text, "MoviesAPI", "https://moviesapi.club/", callback)
        } catch (_: Exception) {
        }
    }

    private suspend fun extractDirectMediaRegex(
        html: String,
        sourceName: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val regex = Regex("""(https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*)""")
        regex.findAll(html).forEach { match ->
            val mediaUrl = match.groupValues[1]
            val isM3u8 = mediaUrl.contains(".m3u8")
            callback(
                newExtractorLink(
                    source = sourceName,
                    name = "$sourceName Stream",
                    url = mediaUrl,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer
                }
            )
        }
    }

    private fun fixUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            !url.startsWith("http") -> "$baseUrl/$url"
            else -> url
        }
    }
}
