package com.tamilultra

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

class TamilUltraProvider : MainAPI() {
    override var mainUrl = "https://tamilultra.co.uk"
    override var name = "TamilUltra"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "ta"
    override val supportedTypes = setOf(TvType.Live, TvType.Movie)

    override val mainPage = mainPageOf(
        MainPageData(name = "All Live Channels", data = "movies?per_page=30", horizontalImages = false),
        MainPageData(name = "Sports Live", data = "movies?genres=174&per_page=30", horizontalImages = false),
        MainPageData(name = "Tamil Entertainment", data = "movies?genres=160&per_page=30", horizontalImages = false),
        MainPageData(name = "Tamil News", data = "movies?genres=158&per_page=30", horizontalImages = false),
        MainPageData(name = "Tamil Movies Live", data = "movies?genres=270&per_page=30", horizontalImages = false),
        MainPageData(name = "Tamil Kids", data = "movies?genres=269&per_page=30", horizontalImages = false),
        MainPageData(name = "Tamil Music", data = "movies?genres=268&per_page=30", horizontalImages = false),
        MainPageData(name = "Tamil Infotainment", data = "movies?genres=271&per_page=30", horizontalImages = false)
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val endpoint = "$mainUrl/wp-json/wp/v2/${request.data}&page=$page&_embed=1"
        val response = try {
            app.get(endpoint, headers = defaultHeaders)
        } catch (_: Exception) {
            null
        }

        if (response == null || response.code !in 200..299) {
            return newHomePageResponse(
                listOf(HomePageList(request.name, emptyList(), isHorizontalImages = false)),
                hasNext = false
            )
        }

        val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull()
            ?: response.headers["x-wp-totalpages"]?.toIntOrNull()

        val movies = try {
            parseJson<List<WpMovieItem>>(response.text)
        } catch (_: Exception) {
            emptyList()
        }

        val homeItems = movies.mapNotNull { toSearchResponse(it) }

        val hasNext = when {
            totalPages != null -> page < totalPages
            movies.size >= 30 -> true
            else -> false
        }

        return newHomePageResponse(
            listOf(HomePageList(request.name, homeItems, isHorizontalImages = false)),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "$mainUrl/wp-json/wp/v2/movies?search=$encoded&_embed=1&per_page=30"
        val response = try {
            app.get(endpoint, headers = defaultHeaders)
        } catch (_: Exception) {
            return emptyList()
        }
        if (response.code !in 200..299) return emptyList()

        val movies = try {
            parseJson<List<WpMovieItem>>(response.text)
        } catch (_: Exception) {
            emptyList()
        }

        return movies.mapNotNull { toSearchResponse(it) }
    }

    private fun toSearchResponse(item: WpMovieItem): SearchResponse? {
        val title = cleanTitle(item.title?.rendered)
        val url = item.link ?: return null
        if (title.isBlank()) return null

        val poster = item.embedded?.featuredMedia?.firstOrNull()?.sourceUrl

        return newLiveSearchResponse(title, url, TvType.Live) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = defaultHeaders)
        val document = Jsoup.parse(response.text)

        val rawTitle = document.selectFirst("h1")?.text()
            ?: document.selectFirst(".data h1")?.text()
            ?: document.title().substringBefore("–").substringBefore("-")
        val title = cleanTitle(rawTitle)

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".poster img")?.attr("src")

        val plot = document.selectFirst(".wp-content p")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val tags = document.select(".sgeneros a").map { it.text().trim() }.filter { it.isNotBlank() }

        val optionElements = document.select("li.dooplay_player_option")
        val options = mutableListOf<PlayerOption>()
        var postId: Long? = null

        for (el in optionElements) {
            val pId = el.attr("data-post").toLongOrNull()
            if (pId != null && postId == null) {
                postId = pId
            }
            val nume = el.attr("data-nume").ifBlank { "1" }
            val serverTitle = el.selectFirst(".title")?.text()?.trim()
                ?: el.text().trim().ifBlank { "Server $nume" }
            options.add(PlayerOption(nume, serverTitle))
        }

        if (postId == null) {
            val bodyClass = document.body().className()
            val match = Regex("postid-(\\d+)").find(bodyClass)
                ?: Regex("post-(\\d+)").find(response.text)
            postId = match?.groupValues?.get(1)?.toLongOrNull()
        }

        if (postId == null) {
            return null
        }

        if (options.isEmpty()) {
            options.add(PlayerOption("1", "Stream 1"))
        }

        val passData = TamilUltraPassData(
            postId = postId,
            options = options,
            pageUrl = url
        )

        return newLiveStreamLoadResponse(title, url, passData.toJson()) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val passData = try {
            parseJson<TamilUltraPassData>(data)
        } catch (_: Exception) {
            val singleId = data.toLongOrNull() ?: return false
            TamilUltraPassData(postId = singleId, options = listOf(PlayerOption("1", "Stream 1")))
        }

        var foundLink = false

        for (opt in passData.options) {
            val dooUrl = "$mainUrl/wp-json/dooplayer/v2/${passData.postId}/movie/${opt.nume}"
            val res = try {
                app.get(
                    dooUrl,
                    referer = passData.pageUrl ?: "$mainUrl/",
                    headers = defaultHeaders
                )
            } catch (_: Exception) {
                continue
            }

            if (res.code !in 200..299) continue
            val doo = try {
                parseJson<DooPlayerResponse>(res.text)
            } catch (_: Exception) {
                continue
            }

            val embed = doo.embedUrl ?: continue
            if (embed.isBlank() || embed == "null" || embed == "false") continue

            val fullEmbed = when {
                embed.startsWith("http://") || embed.startsWith("https://") -> embed
                embed.startsWith("/") -> "$mainUrl$embed"
                else -> "$mainUrl/$embed"
            }

            val linkSuccess = extractPlayerLink(fullEmbed, opt.title, passData.pageUrl, callback, subtitleCallback)
            if (linkSuccess) foundLink = true
        }

        return foundLink
    }

    private suspend fun extractPlayerLink(
        embedUrl: String,
        serverName: String,
        refererUrl: String?,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        return try {
            val query = if (embedUrl.contains("?")) embedUrl.substringAfter("?") else ""

            when {
                // 1. DRM DASH Player
                embedUrl.contains("playerdrm.php") -> {
                    val params = parseQueryParams(query)
                    val rawStreamUrl = params["url"] ?: return false
                    val streamUrl = try { URLDecoder.decode(rawStreamUrl, "UTF-8") } catch (_: Exception) { rawStreamUrl }
                    val keyId = params["keyid"]
                    val key = params["key"]

                    if (!keyId.isNullOrBlank() && !key.isNullOrBlank()) {
                        val cleanKeyId = keyId.trim().replace(" ", "")
                        val cleanKey = key.trim().replace(" ", "")
                        callback(
                            newDrmExtractorLink(
                                source = name,
                                name = "$name - $serverName (DASH DRM)",
                                url = streamUrl,
                                type = ExtractorLinkType.DASH,
                                uuid = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")
                            ) {
                                this.referer = "$mainUrl/"
                                this.kid = cleanKeyId
                                this.key = cleanKey
                            }
                        )
                        true
                    } else {
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name - $serverName (DASH)",
                                url = streamUrl,
                                type = if (streamUrl.contains(".mpd")) ExtractorLinkType.DASH else ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$mainUrl/"
                            }
                        )
                        true
                    }
                }

                // 2. Player.php (HLS streams or tokenized master)
                embedUrl.contains("player.php") -> {
                    val rawTarget = embedUrl.substringAfter("player.php?").trim()
                    if (rawTarget.isBlank()) return false

                    when {
                        rawTarget.startsWith("http://") || rawTarget.startsWith("https://") -> {
                            val isM3u8 = rawTarget.contains(".m3u8")
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $serverName",
                                    url = rawTarget,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "$mainUrl/"
                                }
                            )
                            true
                        }
                        rawTarget.contains("master.m3u8") || rawTarget.contains(".m3u8") -> {
                            val targetUrl = if (rawTarget.startsWith("/")) "$mainUrl$rawTarget" else "$mainUrl/$rawTarget"
                            val streamRes = app.get(targetUrl, referer = "$mainUrl/", headers = defaultHeaders)
                            val finalStreamUrl = streamRes.url
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $serverName",
                                    url = finalStreamUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "$mainUrl/"
                                }
                            )
                            true
                        }
                        else -> {
                            val targetUrl = if (rawTarget.startsWith("/")) "$mainUrl$rawTarget" else "$mainUrl/$rawTarget"
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $serverName",
                                    url = targetUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "$mainUrl/"
                                }
                            )
                            true
                        }
                    }
                }

                // 3. Fallback to generic extractor or direct m3u8
                embedUrl.contains(".m3u8") -> {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name - $serverName",
                            url = embedUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                        }
                    )
                    true
                }

                else -> {
                    loadExtractor(embedUrl, referer = refererUrl ?: "$mainUrl/", subtitleCallback, callback)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isBlank()) return map
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = parts[1]
            }
        }
        return map
    }

    private fun cleanTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title
            .replace("&#8211;", "-")
            .replace("&#8212;", "-")
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&quot;", "\"")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("<[^>]*>"), "")
            .trim()
    }
}
