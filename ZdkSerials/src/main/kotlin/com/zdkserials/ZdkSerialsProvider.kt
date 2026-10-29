package com.zdkserials

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import java.net.URLEncoder

class ZdkSerialsProvider : MainAPI() {
    override var name = "ZDK Serials"
    override var mainUrl = "https://zdkserials.site"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "all"

    override val hasMainPage = true
    override val hasQuickSearch = true

    // Top row: Recent Episodes (standalone playable episodes as Movie, like in TamilDhool)
    // Channel rows: By channel with show blocks (as TvSeries, like Mahanadhi under Star Vijay)
    override val mainPage = mainPageOf(
        MainPageData(name = "Recent Episodes", data = "recent_episodes", horizontalImages = true),
        MainPageData(name = "Star Vijay", data = "channel:star-vijay", horizontalImages = true),
        MainPageData(name = "Sun TV", data = "channel:sun-tv", horizontalImages = true),
        MainPageData(name = "Zee Tamil", data = "channel:zee-tamil", horizontalImages = true),
        MainPageData(name = "Sony Tamil", data = "channel:sony-tamil", horizontalImages = true),
        MainPageData(name = "Star Maa", data = "channel:star-maa", horizontalImages = true),
        MainPageData(name = "Zee Telugu", data = "channel:zee-telugu", horizontalImages = true),
        MainPageData(name = "Sun Gemini Telugu", data = "channel:sun-gemini-telugu", horizontalImages = true),
        MainPageData(name = "Colors TV", data = "channel:colors-tv", horizontalImages = true),
        MainPageData(name = "Star Plus", data = "channel:star-plus", horizontalImages = true),
        MainPageData(name = "Zee TV", data = "channel:zee-tv", horizontalImages = true),
        MainPageData(name = "Sony TV", data = "channel:sony-tv", horizontalImages = true),
        MainPageData(name = "Sab TV", data = "channel:sab-tv", horizontalImages = true),
        MainPageData(name = "Dangal TV", data = "channel:dangal-tv", horizontalImages = true),
        MainPageData(name = "Colors Kannada", data = "channel:colors-kannada", horizontalImages = true),
        MainPageData(name = "Zee Kannada", data = "channel:zee-kannada", horizontalImages = true),
        MainPageData(name = "Sun Udaya Kannada", data = "channel:sun-udaya-kannada", horizontalImages = true),
        MainPageData(name = "Star Suvarna", data = "channel:star-suvarna", horizontalImages = true),
        MainPageData(name = "Colors Marathi", data = "channel:colors-marathi", horizontalImages = true),
        MainPageData(name = "Zee Marathi", data = "channel:zee-marathi", horizontalImages = true),
        MainPageData(name = "Star Pravah", data = "channel:star-pravah", horizontalImages = true),
        MainPageData(name = "Sun Marathi", data = "channel:sun-marathi", horizontalImages = true),
        MainPageData(name = "Colors Bangla", data = "channel:colors-bangla", horizontalImages = true),
        MainPageData(name = "Zee Bangla", data = "channel:zee-bangla", horizontalImages = true),
        MainPageData(name = "Star Jalsha", data = "channel:star-jalsha", horizontalImages = true),
        MainPageData(name = "Sun Bangla", data = "channel:sun-bangla", horizontalImages = true),
        MainPageData(name = "Zee Keralam", data = "channel:zee-keralam", horizontalImages = true),
        MainPageData(name = "Sun Surya Malayalam", data = "channel:sun-surya-malayalam", horizontalImages = true)
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val reqData = request.data

        // 1. TOP ROW ONLY: Recent Episodes (playable movies with direct playback, like TamilDhool)
        if (reqData == "recent_episodes") {
            val endpoint = "$mainUrl/wp-json/wp/v2/ps_episode?per_page=40&page=$page&_embed=1"
            val response = try {
                app.get(endpoint, headers = defaultHeaders)
            } catch (_: Exception) {
                return newHomePageResponse(
                    listOf(HomePageList(request.name, emptyList(), isHorizontalImages = true)),
                    hasNext = false
                )
            }

            val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull()
            val eps = try { parseJson<List<WpEpisode>>(response.text) } catch (_: Exception) { emptyList() }

            val items = eps.mapNotNull { ep ->
                val link = ep.link ?: return@mapNotNull null
                val title = ep.cleanTitle
                val poster = ep.posterUrl
                newMovieSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = poster
                }
            }

            val hasNext = when {
                totalPages != null -> page < totalPages
                eps.size >= 40 -> true
                else -> false
            }

            return newHomePageResponse(
                listOf(HomePageList(request.name, items, isHorizontalImages = true)),
                hasNext = hasNext
            )
        }

        // 2. CHANNEL ROWS: Grouped by channel, each item is a SHOW (single block as TvSeries)
        if (reqData.startsWith("channel:")) {
            val channelSlug = reqData.removePrefix("channel:")
            val channelUrl = "$mainUrl/channel/$channelSlug/"

            if (page > 1) {
                return newHomePageResponse(
                    listOf(HomePageList(request.name, emptyList(), isHorizontalImages = true)),
                    hasNext = false
                )
            }

            val items = try {
                val doc = app.get(channelUrl, headers = defaultHeaders).document
                doc.select("a.zdk-card, .zdk-card").mapNotNull { card ->
                    val link = if (card.tagName() == "a") card.attr("abs:href") else card.selectFirst("a")?.attr("abs:href")
                    if (link.isNullOrBlank()) return@mapNotNull null

                    val showTitle = card.selectFirst(".zdk-ttl")?.text()?.trim()
                        ?: card.selectFirst("h3")?.text()?.trim()
                        ?: card.text().trim()

                    val poster = card.selectFirst("img")?.attr("abs:src")
                        ?: card.selectFirst("img")?.attr("src")

                    // Individual show represented as TvSeries
                    newTvSeriesSearchResponse(showTitle, link, TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                }.distinctBy { it.url }
            } catch (_: Exception) {
                emptyList()
            }

            return newHomePageResponse(
                listOf(HomePageList(request.name, items, isHorizontalImages = true)),
                hasNext = false
            )
        }

        return newHomePageResponse(
            listOf(HomePageList(request.name, emptyList(), isHorizontalImages = true)),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<SearchResponse>()

        // 1. Search Shows via REST API (returns clean show series blocks)
        try {
            val showUrl = "$mainUrl/wp-json/wp/v2/ps_tvshow?search=$encoded&per_page=20&_embed=1"
            val showRes = app.get(showUrl, headers = defaultHeaders)
            if (showRes.code in 200..299) {
                val shows = parseJson<List<WpShow>>(showRes.text)
                shows.forEach { s ->
                    val link = s.link ?: return@forEach
                    results.add(
                        newTvSeriesSearchResponse(s.cleanTitle, link, TvType.TvSeries) {
                            this.posterUrl = s.posterUrl
                        }
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Search Episodes via REST API (represented as standalone playable episodes)
        try {
            val epUrl = "$mainUrl/wp-json/wp/v2/ps_episode?search=$encoded&per_page=20&_embed=1"
            val epRes = app.get(epUrl, headers = defaultHeaders)
            if (epRes.code in 200..299) {
                val eps = parseJson<List<WpEpisode>>(epRes.text)
                eps.forEach { ep ->
                    val link = ep.link ?: return@forEach
                    results.add(
                        newMovieSearchResponse(ep.cleanTitle, link, TvType.Movie) {
                            this.posterUrl = ep.posterUrl
                        }
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: Search website HTML if API results were sparse
        if (results.isEmpty()) {
            try {
                val searchHtml = app.get("$mainUrl/?s=$encoded", headers = defaultHeaders).document
                searchHtml.select("a.zdk-card, .zdk-card").forEach { card ->
                    val link = if (card.tagName() == "a") card.attr("abs:href") else card.selectFirst("a")?.attr("abs:href")
                    if (link.isNullOrBlank()) return@forEach
                    val cardTitle = card.selectFirst(".zdk-ttl")?.text()?.trim()
                        ?: card.selectFirst("h3")?.text()?.trim()
                        ?: card.text().trim()
                    val poster = card.selectFirst("img")?.attr("abs:src")
                        ?: card.selectFirst("img")?.attr("src")

                    val isShow = link.contains("/show/") && !link.contains("/watch/")
                    if (isShow) {
                        results.add(
                            newTvSeriesSearchResponse(cardTitle, link, TvType.TvSeries) {
                                this.posterUrl = poster
                            }
                        )
                    } else {
                        results.add(
                            newMovieSearchResponse(cardTitle, link, TvType.Movie) {
                                this.posterUrl = poster
                            }
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return results.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = if (url.endsWith("/")) url else "$url/"

        // 1. If direct episode watch URL (e.g. from Recent Episodes or search)
        if (cleanUrl.contains("/watch/") || Regex("""/season-\d+/\d+/""").containsMatchIn(cleanUrl)) {
            val doc = try {
                app.get(cleanUrl, headers = defaultHeaders).document
            } catch (_: Exception) {
                null
            }

            val title = doc?.selectFirst("h1")?.text()?.trim()
                ?: doc?.title()?.substringBefore("-")?.trim()
                ?: "Episode"
            val poster = doc?.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc?.selectFirst(".zdk-show-poster img")?.attr("abs:src")
            val plot = doc?.selectFirst("meta[property=og:description]")?.attr("content")

            val cleanTitle = title.replace(Regex("""\s+"""), " ").trim()

            // As Movie, Cloudstream opens player directly without season/episode split
            val singleEpisode = newEpisode(cleanUrl) {
                this.name = cleanTitle
                this.posterUrl = poster
                this.data = cleanUrl
            }

            return newTvSeriesLoadResponse(cleanTitle, cleanUrl, TvType.Movie, listOf(singleEpisode)) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.plot = plot
            }
        }

        // 2. If channel page URL: load channel show cards
        if (cleanUrl.contains("/channel/")) {
            return loadChannel(cleanUrl)
        }

        // 3. Show page (e.g. /show/mahanadhi/): load as TvSeries with all series episodes (no seasons, accurate count)
        return loadShow(cleanUrl)
    }

    private suspend fun loadShow(url: String): LoadResponse {
        val cleanUrl = if (url.endsWith("/")) url else "$url/"
        val doc = app.get(cleanUrl, headers = defaultHeaders).document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Show"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst(".zdk-show-poster img")?.attr("abs:src")
            ?: doc.selectFirst(".zdk-wrap img")?.attr("abs:src")
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")

        val showSlug = cleanUrl.trimEnd('/').substringAfterLast('/')
        val episodes = mutableListOf<Episode>()

        // 1. Parse any episodes already embedded in initial HTML
        doc.select("a.zdk-ep").forEach { a ->
            parseEpisodeLink(a, poster)?.let { episodes.add(it) }
        }

        // 2. Fetch all episodes using zdk_load_more WITHOUT specifying season.
        // Indian serials do not have true seasons. Querying without season retrieves
        // all available episodes in chronological descending order and prevents wrong episode counts.
        val ajaxHeaders = defaultHeaders + mapOf("X-Requested-With" to "XMLHttpRequest")
        var offset = 0
        while (offset < 1000) { // Safety cap
            try {
                val formData = mapOf(
                    "action" to "zdk_load_more",
                    "offset" to offset.toString(),
                    "show" to showSlug,
                    "order" to "desc"
                )
                val resText = app.post("$mainUrl/wp-admin/admin-ajax.php", headers = ajaxHeaders, data = formData).text
                val res = parseJson<ZdkLoadMoreResponse>(resText)
                var batchAdded = 0
                if (!res.html.isNullOrBlank()) {
                    val epDoc = Jsoup.parse(res.html, mainUrl)
                    epDoc.select("a.zdk-ep").forEach { a ->
                        parseEpisodeLink(a, poster)?.let {
                            episodes.add(it)
                            batchAdded++
                        }
                    }
                }
                if (res.more != true || batchAdded == 0) break
                offset += (res.count ?: batchAdded)
            } catch (_: Exception) {
                break
            }
        }

        // Deduplicate episodes by watch URL
        val uniqueEpisodes = episodes.distinctBy { it.data }

        // Fallback: if no episodes found, add watch now button link
        val finalEpisodes = if (uniqueEpisodes.isEmpty()) {
            val watchNowLink = doc.selectFirst("a.zdk-watchnow")?.attr("abs:href") ?: url
            listOf(
                newEpisode(watchNowLink) {
                    this.name = title
                    this.posterUrl = poster
                    this.data = watchNowLink
                    this.episode = 1
                }
            )
        } else {
            uniqueEpisodes
        }

        // Return TvSeries response without season categorization
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.plot = plot
        }
    }

    private suspend fun loadChannel(url: String): LoadResponse {
        val doc = app.get(url, headers = defaultHeaders).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Channel"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")

        val episodes = mutableListOf<Episode>()
        doc.select("a.zdk-card, .zdk-card").forEachIndexed { idx, card ->
            val link = if (card.tagName() == "a") card.attr("abs:href") else card.selectFirst("a")?.attr("abs:href")
            if (link.isNullOrBlank()) return@forEachIndexed
            val cardTitle = card.selectFirst(".zdk-ttl")?.text()?.trim()
                ?: card.selectFirst("h3")?.text()?.trim()
                ?: card.text().trim()
            val cardPoster = card.selectFirst("img")?.attr("abs:src")
                ?: card.selectFirst("img")?.attr("src") ?: poster

            episodes.add(
                newEpisode(link) {
                    this.name = cardTitle
                    this.posterUrl = cardPoster
                    this.data = link
                    this.episode = idx + 1
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1. If data is directly an embed URL (e.g. upbolt.to/e/... or wexiostream.com/e/...)
        if (data.contains("upbolt", ignoreCase = true) || data.contains("wexio", ignoreCase = true)) {
            return ZdkExtractor.resolveAndExtract(
                htmlOrUrl = data,
                baseUrl = mainUrl,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }

        // 2. Fetch watch page HTML
        val htmlContent = try {
            app.get(data, headers = defaultHeaders).text
        } catch (_: Exception) {
            return false
        }

        // 3. Resolve and extract links with multiple checks (iframes, servers, regex, direct m3u8)
        val extracted = ZdkExtractor.resolveAndExtract(
            htmlOrUrl = htmlContent,
            baseUrl = data,
            subtitleCallback = subtitleCallback,
            callback = callback
        )

        if (extracted) return true

        // 4. Fallback: if data was a show page URL where play was clicked directly,
        // find watch-now or first episode link and extract from it
        val doc = Jsoup.parse(htmlContent, data)
        val watchLink = doc.selectFirst("a.zdk-watchnow")?.attr("abs:href")
            ?: doc.selectFirst("a.zdk-ep")?.attr("abs:href")
        if (!watchLink.isNullOrBlank() && watchLink != data) {
            return loadLinks(watchLink, isCasting, subtitleCallback, callback)
        }

        return false
    }

    private fun parseEpisodeLink(a: org.jsoup.nodes.Element, defaultPoster: String?): Episode? {
        val epHref = a.attr("abs:href").ifBlank { a.attr("href") }
        if (epHref.isBlank()) return null

        val epName = a.selectFirst(".zdk-ep-info .n")?.text()?.trim() ?: a.text().trim()
        val epDate = a.selectFirst(".zdk-ep-info .m")?.text()?.trim()
        val displayName = if (!epDate.isNullOrBlank()) "$epName ($epDate)" else epName

        val epThumb = a.selectFirst(".zdk-ep-thumb img")?.attr("abs:src")
            ?: a.selectFirst("img")?.attr("abs:src")
            ?: a.selectFirst("img")?.attr("src")
            ?: defaultPoster

        val epNum = Regex("""/season-\d+/(\d+)/watch/""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(?:Episode|Ep|E)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(epName)?.groupValues?.get(1)?.toIntOrNull()

        // NOTE: We deliberately DO NOT set `this.season = ...`!
        // Leaving season as null tells CloudStream not to create season tabs,
        // displaying all episodes in a single clean flat series list matching the website.
        return newEpisode(epHref) {
            this.name = displayName
            this.posterUrl = epThumb
            this.data = epHref
            this.episode = epNum
        }
    }
}
