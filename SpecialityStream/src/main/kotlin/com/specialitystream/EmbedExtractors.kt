package com.specialitystream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidoraTrack(
    @JsonProperty("kind") val kind: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("language") val language: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidoraSource(
    @JsonProperty("file_code") val fileCode: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("tracks") val tracks: List<VidoraTrack>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidoraResponse(
    @JsonProperty("result") val result: Boolean? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("sources") val sources: List<VidoraSource>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuzzServer(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("ref") val ref: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuzzSsr(
    @JsonProperty("servers") val servers: List<BuzzServer>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuzzConfig(
    @JsonProperty("t") val t: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("s") val s: Int? = null,
    @JsonProperty("e") val e: Int? = null,
    @JsonProperty("ssr") val ssr: BuzzSsr? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuzzPlayResponse(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuzzSubtitle(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("ref") val ref: String? = null
)

object EmbedExtractors {

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    )

    private fun safeBase64Decode(str: String): String? {
        return try {
            val clean = str.replace("-", "+").replace("_", "/")
            val pad = (4 - clean.length % 4) % 4
            val padded = clean + "=".repeat(pad)
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val out = ArrayList<Byte>()
            var i = 0
            while (i < padded.length) {
                val c1 = padded[i++]
                val c2 = padded[i++]
                val c3 = padded[i++]
                val c4 = padded[i++]
                val v1 = table.indexOf(c1)
                val v2 = table.indexOf(c2)
                val v3 = if (c3 == '=') -1 else table.indexOf(c3)
                val v4 = if (c4 == '=') -1 else table.indexOf(c4)
                if (v1 < 0 || v2 < 0) break
                out.add(((v1 shl 2) or (v2 shr 4)).toByte())
                if (v3 >= 0) {
                    out.add(((v2 and 0x0F shl 4) or (v3 shr 2)).toByte())
                    if (v4 >= 0) {
                        out.add(((v3 and 0x03 shl 6) or v4).toByte())
                    }
                }
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unwraps p1.hd4u.sbs/proxy URLs to resolve the direct CDN stream URL.
     * Prevents HTTP 503 errors and ExoPlayer HTTP 2004 crashes by bypassing the proxy.
     */
    private fun unwrapProxyUrl(proxyUrl: String): Pair<String, Map<String, String>>? {
        if (!proxyUrl.contains("/proxy?")) return null
        return try {
            val uri = URI(proxyUrl)
            val query = uri.rawQuery ?: return null
            val params = query.split("&").associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (if (parts.size > 1) parts[1] else "")
            }
            val rawEncodedUrl = params["url"] ?: return null
            val targetUrl = URLDecoder.decode(rawEncodedUrl, "UTF-8")

            val dataEncoded = params["data"]
            val headers = mutableMapOf<String, String>()
            if (!dataEncoded.isNullOrBlank()) {
                val unquotedData = URLDecoder.decode(dataEncoded, "UTF-8")
                val decodedData = safeBase64Decode(unquotedData)
                if (!decodedData.isNullOrBlank()) {
                    decodedData.split("|").forEach { pair ->
                        val p = pair.split("=", limit = 2)
                        if (p.size == 2) {
                            headers[p[0]] = p[1]
                        }
                    }
                }
            }
            targetUrl to headers
        } catch (_: Exception) {
            null
        }
    }

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

        // 1. VidSrc Vidora Direct API (Alpha / Beta)
        extractVidSrcVidora(idStr, season, episode, isMovie, callback, subtitleCallback)

        // 2. VidSrc Buzz (Multi-server HLS: Server VNR, VEM, SWM1, VNY)
        extractBuzzEngine("https://vidsrc.buzz", "VidSrc Buzz", idStr, season, episode, isMovie, callback, subtitleCallback)

        // 3. Videm (Multi-server HLS engine)
        extractBuzzEngine("https://videm.xyz", "Videm", idStr, season, episode, isMovie, callback, subtitleCallback)

        // 4. 2Embed Embed Links
        extract2Embed(idStr, imdbId, season, episode, isMovie, callback, subtitleCallback)

        // 5. MultiEmbed / SuperEmbed Mirrors
        extractMultiEmbed(idStr, season, episode, isMovie, callback, subtitleCallback)
    }

    private suspend fun extractVidSrcVidora(
        tmdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val refererPage = if (isMovie) {
            "https://vidsrc.pm/embed/movie/$tmdbId"
        } else {
            "https://vidsrc.pm/embed/tv/$tmdbId/$season/$episode"
        }
        val path = if (isMovie) "/v1/movie/$tmdbId" else "/v1/tv/$tmdbId/$season/$episode"
        val vidoraSources = listOf(
            "scrapify" to "Alpha",
            "vaplayer" to "Beta"
        )

        val vidoraHeaders = mapOf(
            "User-Agent" to defaultHeaders["User-Agent"]!!,
            "Referer" to refererPage,
            "x-player-key" to "f3b72e73c80c9a996574379798703796a1936efa3516a7105cb0e43048b46b5a"
        )

        for ((key, label) in vidoraSources) {
            try {
                val apiUrl = "https://vidsrc.pm/api/vidora$path?source=$key"
                val res = app.get(apiUrl, headers = vidoraHeaders, timeout = 7)
                if (res.code in 200..299) {
                    val data = try {
                        parseJson<VidoraResponse>(res.text)
                    } catch (_: Exception) {
                        null
                    }
                    val streamUrl = data?.sources?.firstOrNull()?.url
                    if (!streamUrl.isNullOrBlank()) {
                        // Attempt to unwrap direct CDN link to avoid proxy 503 / ExoPlayer 2004 errors
                        val unwrapped = unwrapProxyUrl(streamUrl)
                        if (unwrapped != null) {
                            val directUrl = unwrapped.first
                            val directHeaders = unwrapped.second.toMutableMap()
                            if (!directHeaders.containsKey("User-Agent")) {
                                directHeaders["User-Agent"] = defaultHeaders["User-Agent"]!!
                            }
                            val isM3u8 = directUrl.contains(".m3u8")
                            val cdnReferer = directHeaders["Referer"] ?: directHeaders["referer"] ?: refererPage

                            callback(
                                newExtractorLink(
                                    source = "VidSrc ($label Direct)",
                                    name = "VidSrc ($label Direct)",
                                    url = directUrl,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = cdnReferer
                                    this.headers = directHeaders
                                }
                            )
                        }

                        // Emit original link with explicit referer
                        val isM3u8Original = streamUrl.contains(".m3u8")
                        callback(
                            newExtractorLink(
                                source = "VidSrc ($label)",
                                name = "VidSrc ($label)",
                                url = streamUrl,
                                type = if (isM3u8Original) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = refererPage
                                this.headers = mapOf(
                                    "Referer" to refererPage,
                                    "User-Agent" to defaultHeaders["User-Agent"]!!
                                )
                            }
                        )

                        data.sources.firstOrNull()?.tracks?.forEach { track ->
                            val file = track.file ?: return@forEach
                            val lang = track.label ?: track.language ?: "English"
                            subtitleCallback(
                                SubtitleFile(
                                    lang = lang,
                                    url = file
                                )
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun extractBuzzEngine(
        baseUrl: String,
        sourcePrefix: String,
        tmdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val embedUrl = if (isMovie) {
            "$baseUrl/embed/movie/$tmdbId"
        } else {
            "$baseUrl/embed/tv/$tmdbId/$season/$episode"
        }

        try {
            val res = app.get(
                embedUrl,
                headers = mapOf(
                    "User-Agent" to defaultHeaders["User-Agent"]!!,
                    "Referer" to "https://www.2embed.cc/"
                ),
                timeout = 7
            )
            if (res.code !in 200..299) return

            val html = res.text
            val qMatch = Regex("""var\s+Q\s*=\s*(\{.+?\});""").find(html) ?: return
            val qJson = qMatch.groupValues[1]
            val config = try {
                parseJson<BuzzConfig>(qJson)
            } catch (_: Exception) {
                null
            } ?: return

            val servers = config.ssr?.servers ?: emptyList()
            val token = config.t ?: return

            // 1. Extract Streams for each server
            for (srv in servers) {
                val ref = srv.ref ?: continue
                val srvName = srv.name ?: "Server"
                val playUrl = "$baseUrl/pl/api.php?a=play&ref=$ref&t=$token"

                try {
                    val playRes = app.get(
                        playUrl,
                        headers = mapOf(
                            "User-Agent" to defaultHeaders["User-Agent"]!!,
                            "Referer" to embedUrl
                        ),
                        timeout = 6
                    )
                    if (playRes.code in 200..299) {
                        val playData = try {
                            parseJson<BuzzPlayResponse>(playRes.text)
                        } catch (_: Exception) {
                            null
                        }

                        val relStream = playData?.url
                        if (!relStream.isNullOrBlank()) {
                            val fullStream = if (relStream.startsWith("/")) "$baseUrl$relStream" else relStream
                            val isM3u8 = fullStream.contains(".m3u8") || playData.type == "hls"

                            callback(
                                newExtractorLink(
                                    source = sourcePrefix,
                                    name = "$sourcePrefix ($srvName)",
                                    url = fullStream,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = embedUrl
                                    this.headers = mapOf(
                                        "Referer" to embedUrl,
                                        "User-Agent" to defaultHeaders["User-Agent"]!!
                                    )
                                }
                            )
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            // 2. Extract Subtitles
            val qs = "t=${config.t}&id=${config.id}&s=${config.s ?: 0}&e=${config.e ?: 0}"
            val subsUrl = "$baseUrl/pl/api.php?a=subs&$qs"
            try {
                val subRes = app.get(
                    subsUrl,
                    headers = mapOf(
                        "User-Agent" to defaultHeaders["User-Agent"]!!,
                        "Referer" to embedUrl
                    ),
                    timeout = 6
                )
                if (subRes.code in 200..299) {
                    val subList = try {
                        parseJson<List<BuzzSubtitle>>(subRes.text)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    subList.forEach { sub ->
                        val subRef = sub.ref ?: return@forEach
                        val lang = sub.lang ?: sub.label ?: "English"
                        val fullSubUrl = "$baseUrl/pl/api.php?a=sub&ref=$subRef"
                        subtitleCallback(
                            SubtitleFile(
                                lang = lang,
                                url = fullSubUrl
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
            }

        } catch (_: Throwable) {
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

            val html = res.text
            val doc = Jsoup.parse(html)

            // Parse server buttons in dropdown onclick="go('...')"
            val goMatches = Regex("""go\(['"]([^'"]+)['"]\)""").findAll(html)
            for (m in goMatches) {
                val serverUrl = m.groupValues[1]
                if (serverUrl.contains("vidsrc.buzz") || serverUrl.contains("videm.xyz")) {
                    continue // Already handled by buzz engines
                }
                loadExtractor(serverUrl, referer = "https://www.2embed.cc/", subtitleCallback, callback)
            }

            // Standard iframe parsing
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                if (iframe == "about:blank") continue
                val fullIframe = fixUrl(iframe, "https://www.2embed.cc")
                loadExtractor(fullIframe, referer = "https://www.2embed.cc/", subtitleCallback, callback)
            }
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
            val res = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to defaultHeaders["User-Agent"]!!,
                    "Referer" to "https://google.com/"
                ),
                timeout = 5
            )
            if (res.code !in 200..299) return

            val doc = Jsoup.parse(res.text)
            val iframes = doc.select("iframe").mapNotNull { it.attr("src").ifBlank { null } }
            for (iframe in iframes) {
                val fullIframe = fixUrl(iframe, "https://multiembed.mov")
                loadExtractor(fullIframe, referer = "https://multiembed.mov/", subtitleCallback, callback)
            }
        } catch (_: Exception) {
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
