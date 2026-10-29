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

    override val mainPage = mainPageOf(
        MainPageData(name = "Latest Episodes", data = "api:ps_episode?per_page=30", horizontalImages = true),
        MainPageData(name = "Tamil Episodes", data = "lang:tamil", horizontalImages = true),
        MainPageData(name = "Telugu Episodes", data = "lang:telugu", horizontalImages = true),
        MainPageData(name = "Hindi Episodes", data = "lang:hindi", horizontalImages = true),
        MainPageData(name = "Kannada Episodes", data = "lang:kannada", horizontalImages = true),
        MainPageData(name = "Marathi Episodes", data = "lang:marathi", horizontalImages = true),
        MainPageData(name = "Bengali Episodes", data = "lang:bengali", horizontalImages = true),
        MainPageData(name = "All TV Shows", data = "api:ps_tvshow?per_page=30", horizontalImages = true),
        MainPageData(name = "All Channels", data = "api:ps_channel?per_page=30", horizontalImages = true)
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val reqData = request.data

        if (reqData.startsWith("api:")) {
            val apiPath = reqData.removePrefix("api:")
            val endpoint = "$mainUrl/wp-json/wp/v2/$apiPath&page=$page&_embed=1"

            val response = try {
                app.get(endpoint, headers = defaultHeaders)
            } catch (_: Exception) {
                null
            }

            if (response == null || response.code !in 200..299) {
                return newHomePageResponse(
                    listOf(HomePageList(request.name, emptyList(), isHorizontalImages = true)),
                    hasNext = false
                )
            }

            val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull()
                ?: response.headers["x-wp-totalpages"]?.toIntOrNull()

            val items = mutableListOf<SearchResponse>()
            var fetchedCount = 0

            when {
                apiPath.startsWith("ps_episode") -> {
                    val eps = try { parseJson<List<WpEpisode>>(response.text) } catch (_: Exception) { emptyList() }
                    fetchedCount = eps.size
                    eps.forEach { ep ->
                        val link = ep.link ?: return@forEach
                        val title = ep.cleanTitle
                        val poster = ep.posterUrl
                        items.add(
                            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                                this.posterUrl = poster
                            }
                        )
                    }
                }
                apiPath.startsWith("ps_tvshow") -> {
                    val shows = try { parseJson<List<WpShow>>(response.text) } catch (_: Exception) { emptyList() }
                    fetchedCount = shows.size
                    shows.forEach { show ->
                        val link = show.link ?: return@forEach
                        val title = show.cleanTitle
                        val poster = show.posterUrl
                        items.add(
                            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                                this.posterUrl = poster
                            }
                        )
                    }
                }
                apiPath.startsWith("ps_channel") -> {
                    val channels = try { parseJson<List<WpChannel>>(response.text) } catch (_: Exception) { emptyList() }
                    fetchedCount = channels.size
                    channels.forEach { ch ->
                        val link = ch.link ?: return@forEach
                        val title = ch.cleanTitle
                        val poster = ch.posterUrl
                        items.add(
                            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                                this.posterUrl = poster
                            }
                        )
                    }
                }
            }

            val hasNext = when {
                totalPages != null -> page < totalPages
                fetchedCount >= 30 -> true
                else -> false
            }

            return newHomePageResponse(
                listOf(HomePageList(request.name, items, isHorizontalImages = true)),
                hasNext = hasNext
            )
        } else if (reqData.startsWith("lang:")) {
            val langSlug = reqData.removePrefix("lang:")
            val items = mutableListOf<SearchResponse>()
            var hasNext = false

            if (page == 1) {
                try {
                    val doc = app.get("$mainUrl/$langSlug/latest-episodes/", headers = defaultHeaders).document
                    doc.select("a.zdk-card, .zdk-card").forEach { card ->
                        val link = if (card.tagName() == "a") card.attr("abs:href") else card.selectFirst("a")?.attr("abs:href")
                        if (link.isNullOrBlank()) return@forEach
                        val title = cleanCardTitle(card.text())
                        val poster = card.selectFirst("img")?.attr("abs:src")
                            ?: card.selectFirst("img")?.attr("src")
                        items.add(
                            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                                this.posterUrl = poster
                            }
                        )
                    }
                    hasNext = items.isNotEmpty()
                } catch (_: Exception) {}
            } else {
                try {
                    val offset = (page - 1) * 40
                    val formData = mapOf(
                        "action" to "zdk_load_more",
                        "offset" to offset.toString(),
                        "lang" to langSlug
                    )
                    val ajaxHeaders = defaultHeaders + mapOf("X-Requested-With" to "XMLHttpRequest")
                    val resText = app.post("$mainUrl/wp-admin/admin-ajax.php", headers = ajaxHeaders, data = formData).text
                    val res = parseJson<ZdkLoadMoreResponse>(resText)
                    if (!res.html.isNullOrBlank()) {
                        val doc = Jsoup.parse(res.html, mainUrl)
                        doc.select("a.zdk-card, .zdk-card").forEach { card ->
                            val link = if (card.tagName() == "a") card.attr("abs:href") else card.selectFirst("a")?.attr("abs:href")
                            if (link.isNullOrBlank()) return@forEach
                            val title = cleanCardTitle(card.text())
                            val poster = card.selectFirst("img")?.attr("abs:src")
                                ?: card.selectFirst("img")?.attr("src")
                            items.add(
                                newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                                    this.posterUrl = poster
                                }
                            )
                        }
                    }
                    hasNext = res.more == true
                } catch (_: Exception) {}
            }

            return newHomePageResponse(
                listOf(HomePageList(request.name, items, isHorizontalImages = true)),
                hasNext = hasNext
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

        // 1. Search Shows via REST API
        try {
            val showEndpoint = "$mainUrl/wp-json/wp/v2/ps_tvshow?search=$encoded&_embed=1&per_page=30"
            val showRes = app.get(showEndpoint, headers = defaultHeaders)
            if (showRes.code in 200..299) {
                val shows = parseJson<List<WpShow>>(showRes.text)
                shows.forEach { show ->
                    val link = show.link ?: return@forEach
                    results.add(
                        newTvSeriesSearchResponse(show.cleanTitle, link, TvType.TvSeries) {
                            this.posterUrl = show.posterUrl
                        }
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Search Episodes via REST API
        try {
            val epEndpoint = "$mainUrl/wp-json/wp/v2/ps_episode?search=$encoded&_embed=1&per_page=30"
            val epRes = app.get(epEndpoint, headers = defaultHeaders)
            if (epRes.code in 200..299) {
                val eps = parseJson<List<WpEpisode>>(epRes.text)
                eps.forEach { ep ->
                    val link = ep.link ?: return@forEach
                    results.add(
                        newTvSeriesSearchResponse(ep.cleanTitle, link, TvType.TvSeries) {
                            this.posterUrl = ep.posterUrl
                        }
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback to website search if REST API found nothing
        if (results.isEmpty()) {
            try {
                val doc = app.get("$mainUrl/?s=$encoded", headers = defaultHeaders).document
                doc.select("a.zdk-card, .zdk-card").forEach { card ->
                    val link = if (card.tagName() == "a") card.attr("abs:href") else card.selectFirst("a")?.attr("abs:href")
                    if (link.isNullOrBlank()) return@forEach
                    val title = cleanCardTitle(card.text())
                    val poster = card.selectFirst("img")?.attr("abs:src")
                        ?: card.selectFirst("img")?.attr("src")
                    results.add(
                        newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                            this.posterUrl = poster
                        }
                    )
                }
            } catch (_: Exception) {}
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        // 1. If URL is an episode watch page, redirect to its parent show page to get the full series
        if (url.contains("/season-") && url.contains("/watch/")) {
            val showUrl = url.substringBefore("/season-") + "/"
            try {
                return loadShow(showUrl, requestedEpisodeUrl = url)
            } catch (_: Exception) {
                // If loading show failed, fallback to loading single episode below
            }
        }

        // 2. If URL is a channel page, load the channel and its show cards
        if (url.contains("/channel/")) {
            return loadChannel(url)
        }

        // 3. Load show page
        return loadShow(url)
    }

    private suspend fun loadShow(url: String, requestedEpisodeUrl: String? = null): LoadResponse {
        val cleanUrl = if (url.endsWith("/")) url else "$url/"
        val doc = app.get(cleanUrl, headers = defaultHeaders).document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "ZDK Show"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst(".zdk-show-poster img")?.attr("abs:src")
            ?: doc.selectFirst(".zdk-wrap img")?.attr("abs:src")
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")

        val showSlug = cleanUrl.trimEnd('/').substringAfterLast('/')
        val episodes = mutableListOf<Episode>()

        // Check for multiple seasons in <select id="zdk-season">
        val seasonSelect = doc.selectFirst("select#zdk-season")
        val seasonOptions = seasonSelect?.select("option") ?: emptyList()

        if (seasonOptions.size > 1) {
            val selectedSeasonVal = seasonSelect?.selectFirst("option[selected]")?.attr("value")
                ?: seasonOptions.firstOrNull()?.attr("value") ?: "1"

            // Parse default rendered season from current page
            doc.select("a.zdk-ep").forEach { a ->
                parseEpisodeLink(a, poster)?.let { episodes.add(it) }
            }

            // Fetch other seasons via zdk_load_more
            val ajaxHeaders = defaultHeaders + mapOf("X-Requested-With" to "XMLHttpRequest")
            seasonOptions.forEach { opt ->
                val seasonVal = opt.attr("value")
                if (seasonVal != selectedSeasonVal && seasonVal.isNotBlank()) {
                    try {
                        val formData = mapOf(
                            "action" to "zdk_load_more",
                            "offset" to "0",
                            "show" to showSlug,
                            "season" to seasonVal,
                            "order" to "desc"
                        )
                        val resText = app.post("$mainUrl/wp-admin/admin-ajax.php", headers = ajaxHeaders, data = formData).text
                        val res = parseJson<ZdkLoadMoreResponse>(resText)
                        if (!res.html.isNullOrBlank()) {
                            val epDoc = Jsoup.parse(res.html, mainUrl)
                            epDoc.select("a.zdk-ep").forEach { a ->
                                parseEpisodeLink(a, poster)?.let { episodes.add(it) }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            // Single season: parse episodes directly from page
            doc.select("a.zdk-ep").forEach { a ->
                parseEpisodeLink(a, poster)?.let { episodes.add(it) }
            }

            // If load more button is present, fetch the next batch (up to 80 episodes)
            val loadMoreBtn = doc.selectFirst("#zdk-load-more")
            if (loadMoreBtn != null) {
                try {
                    val seasonVal = loadMoreBtn.attr("data-season").ifBlank { "1" }
                    val formData = mapOf(
                        "action" to "zdk_load_more",
                        "offset" to "40",
                        "show" to showSlug,
                        "season" to seasonVal,
                        "order" to "desc"
                    )
                    val ajaxHeaders = defaultHeaders + mapOf("X-Requested-With" to "XMLHttpRequest")
                    val resText = app.post("$mainUrl/wp-admin/admin-ajax.php", headers = ajaxHeaders, data = formData).text
                    val res = parseJson<ZdkLoadMoreResponse>(resText)
                    if (!res.html.isNullOrBlank()) {
                        val epDoc = Jsoup.parse(res.html, mainUrl)
                        epDoc.select("a.zdk-ep").forEach { a ->
                            parseEpisodeLink(a, poster)?.let { episodes.add(it) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Ensure requested episode is included if not present
        if (requestedEpisodeUrl != null && episodes.none { it.data == requestedEpisodeUrl }) {
            val reqEpNum = Regex("""/season-\d+/(\d+)/watch/""").find(requestedEpisodeUrl)?.groupValues?.get(1)?.toIntOrNull()
            val reqSeasonNum = Regex("""/season-(\d+)/""").find(requestedEpisodeUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            episodes.add(
                0,
                newEpisode(requestedEpisodeUrl) {
                    this.name = if (reqEpNum != null) "Episode $reqEpNum" else "Episode"
                    this.episode = reqEpNum
                    this.season = reqSeasonNum
                    this.posterUrl = poster
                    this.data = requestedEpisodeUrl
                }
            )
        }

        // Fallback: if no episodes found, add a single placeholder episode
        if (episodes.isEmpty()) {
            val watchNowLink = doc.selectFirst("a.zdk-watchnow")?.attr("abs:href") ?: url
            episodes.add(
                newEpisode(watchNowLink) {
                    this.name = title
                    this.posterUrl = poster
                    this.data = watchNowLink
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
            val cardTitle = cleanCardTitle(card.text())
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
        val htmlContent = try {
            app.get(data, headers = defaultHeaders).text
        } catch (_: Exception) {
            return false
        }

        return ZdkExtractor.resolveAndExtract(
            htmlOrUrl = htmlContent,
            baseUrl = mainUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
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

        val seasonNum = Regex("""/season-(\d+)/""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val epNum = Regex("""/season-\d+/(\d+)/watch/""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(?:Episode|Ep|E)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(epName)?.groupValues?.get(1)?.toIntOrNull()

        return newEpisode(epHref) {
            this.name = displayName
            this.posterUrl = epThumb
            this.data = epHref
            this.season = seasonNum
            this.episode = epNum
        }
    }

    private fun cleanCardTitle(text: String): String {
        return text.replace(Regex("""\s+"""), " ")
            .replace(Regex("""^\d{1,2}\s+[A-Za-z]{3}\s*"""), "") // Remove leading "29 Oct"
            .trim()
            .ifBlank { text.trim() }
    }
}

