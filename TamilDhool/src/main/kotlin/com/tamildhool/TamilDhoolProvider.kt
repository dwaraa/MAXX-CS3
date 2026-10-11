package com.tamildhool

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.net.URLEncoder
import org.jsoup.Jsoup

class TamilDhoolProvider : MainAPI() {
    override var name = "TamilDhool"
    override var mainUrl = "https://www.tamildhool.tech"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "ta"

    override val hasMainPage = true
    override val hasQuickSearch = true

    // Configure all homescreen sections with horizontalImages = true for landscape card posters
    override val mainPage = mainPageOf(
        MainPageData(name = "Latest Episodes", data = "posts?per_page=20", horizontalImages = true),
        MainPageData(name = "Sun TV Serials", data = "posts?search=Sun+TV+Serial&per_page=20", horizontalImages = true),
        MainPageData(name = "Sun TV Shows", data = "posts?search=Sun+TV+Show&per_page=20", horizontalImages = true),
        MainPageData(name = "Vijay TV Serials", data = "posts?search=Vijay+TV+Serial&per_page=20", horizontalImages = true),
        MainPageData(name = "Vijay TV Shows", data = "posts?search=Vijay+TV+Show&per_page=20", horizontalImages = true),
        MainPageData(name = "Zee Tamil Serials", data = "posts?search=Zee+Tamil+Serial&per_page=20", horizontalImages = true),
        MainPageData(name = "Zee Tamil Shows", data = "posts?search=Zee+Tamil+Show&per_page=20", horizontalImages = true),
        MainPageData(name = "Colors Tamil Serials", data = "posts?search=Colors+Tamil+Serial&per_page=20", horizontalImages = true),
        MainPageData(name = "Colors Tamil Shows", data = "posts?search=Colors+Tamil+Show&per_page=20", horizontalImages = true),
        MainPageData(name = "Kalaignar TV Serials", data = "posts?search=Kalaignar+TV+Serial&per_page=20", horizontalImages = true),
        MainPageData(name = "Kalaignar TV Shows", data = "posts?search=Kalaignar+TV+Show&per_page=20", horizontalImages = true),
        MainPageData(name = "Tamil Web Series", data = "posts?search=Web+Series&per_page=20", horizontalImages = true)
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

        // If request failed or invalid page error from WordPress
        if (response == null || response.code !in 200..299) {
            return newHomePageResponse(
                listOf(HomePageList(request.name, emptyList(), isHorizontalImages = true)),
                hasNext = false
            )
        }

        // Extract pagination headers provided by WordPress REST API
        val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull()
            ?: response.headers["x-wp-totalpages"]?.toIntOrNull()

        val posts = try {
            parseJson<List<WpPost>>(response.text)
        } catch (_: Exception) {
            emptyList()
        }

        // Calculate hasNext using official WordPress REST API total pages count
        val hasNext = when {
            totalPages != null -> page < totalPages
            posts.size >= 20 -> true
            else -> false
        }

        val items = posts.mapNotNull { it.toSearchResponse() }

        // Always return HomePageList with isHorizontalImages = true for landscape card rendering
        return newHomePageResponse(
            listOf(HomePageList(request.name, items, isHorizontalImages = true)),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "$mainUrl/wp-json/wp/v2/posts?search=$encoded&_embed=1&per_page=30"
        val posts = fetchPosts(endpoint)

        return posts.mapNotNull { post ->
            post.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        var post: WpPost? = null

        // 1. If URL is directly a REST API endpoint
        if (url.contains("/wp-json/wp/v2/posts/")) {
            post = fetchSinglePost(url)
        }

        // 2. If URL is a standard post webpage link, query by slug
        if (post == null && url.startsWith(mainUrl)) {
            val slug = url.trimEnd('/').substringAfterLast('/')
            if (slug.isNotBlank()) {
                val slugEndpoint = "$mainUrl/wp-json/wp/v2/posts?slug=$slug&_embed=1"
                val results = fetchPosts(slugEndpoint)
                post = results.firstOrNull()
            }
        }

        // 3. If post successfully retrieved from WordPress REST API
        if (post != null) {
            val title = if (post.isPromoOnly) post.cleanTitle else post.pureShowTitle
            val poster = post.posterUrl
            val plot = post.excerpt?.rendered?.let { Jsoup.parse(it).text() }
            val year = post.date?.take(4)?.toIntOrNull()

            // 1. If it is an individual promo or teaser, treat as single video
            if (post.isPromoOnly) {
                val singleEpisode = newEpisode(post.link ?: url) {
                    this.name = post.cleanTitle
                    this.posterUrl = poster
                    this.data = post.link ?: url
                }
                return newTvSeriesLoadResponse(title, url, TvType.Movie, listOf(singleEpisode)) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = plot
                    this.year = year
                }
            }

            // 2. For serials and recurring shows, fetch episodes specifically for THIS show
            val episodes = mutableListOf<Episode>()
            val showName = post.pureShowTitle

            val currentEpisode = newEpisode(post.link ?: url) {
                val epDate = post.displayDate
                val epDay = Regex("""\b(?:Day\s*\d+)\b""", RegexOption.IGNORE_CASE).find(post.cleanTitle)?.value
                this.name = when {
                    epDay != null && epDate.isNotBlank() -> "$epDate ($epDay)"
                    epDate.isNotBlank() -> epDate
                    epDay != null -> epDay
                    else -> "Latest Episode"
                }
                this.posterUrl = poster
                this.data = post.link ?: url
            }

            try {
                // Search specifically for this exact show name to avoid unrelated category mix-ups
                val encodedShow = URLEncoder.encode(showName, "UTF-8")
                val searchEndpoint = "$mainUrl/wp-json/wp/v2/posts?search=$encodedShow&per_page=100&_embed=1&orderby=date&order=desc"
                val searchResponse = app.get(searchEndpoint, headers = defaultHeaders)
                if (searchResponse.code in 200..299) {
                    val foundPosts = parseJson<List<WpPost>>(searchResponse.text)

                    // Strict filter: must match the show name and NOT be a promo
                    val matchingPosts = foundPosts.filter { p ->
                        !p.isPromoOnly && (
                            p.pureShowTitle.equals(showName, ignoreCase = true) ||
                            p.cleanTitle.contains(showName, ignoreCase = true)
                        )
                    }.sortedByDescending { it.date ?: "" }

                    if (matchingPosts.isNotEmpty()) {
                        matchingPosts.forEachIndexed { index, p ->
                            val epDate = p.displayDate
                            val epDay = Regex("""\b(?:Day\s*\d+)\b""", RegexOption.IGNORE_CASE).find(p.cleanTitle)?.value
                            val epTitle = when {
                                epDay != null && epDate.isNotBlank() -> "$epDate ($epDay)"
                                epDate.isNotBlank() -> epDate
                                epDay != null -> epDay
                                else -> "Episode ${matchingPosts.size - index}"
                            }
                            episodes.add(
                                newEpisode(p.link ?: "$mainUrl/?p=${p.id}") {
                                    this.name = epTitle
                                    this.posterUrl = p.posterUrl
                                    this.data = p.link ?: "$mainUrl/?p=${p.id}"
                                    this.episode = matchingPosts.size - index
                                }
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore search error and fallback
            }

            // Fallback: If show search yielded no episodes, query category but strictly filter by show name
            if (episodes.isEmpty()) {
                val categoryId = post.categories?.lastOrNull()
                if (categoryId != null) {
                    try {
                        val catEndpoint = "$mainUrl/wp-json/wp/v2/posts?categories=$categoryId&per_page=100&_embed=1&orderby=date&order=desc"
                        val catResponse = app.get(catEndpoint, headers = defaultHeaders)
                        if (catResponse.code in 200..299) {
                            val catPosts = parseJson<List<WpPost>>(catResponse.text)
                            val matchingPosts = catPosts.filter { p ->
                                !p.isPromoOnly && (
                                    p.pureShowTitle.equals(showName, ignoreCase = true) ||
                                    p.cleanTitle.contains(showName, ignoreCase = true)
                                )
                            }.sortedByDescending { it.date ?: "" }

                            matchingPosts.forEachIndexed { index, p ->
                                val epDate = p.displayDate
                                val epDay = Regex("""\b(?:Day\s*\d+)\b""", RegexOption.IGNORE_CASE).find(p.cleanTitle)?.value
                                val epTitle = when {
                                    epDay != null && epDate.isNotBlank() -> "$epDate ($epDay)"
                                    epDate.isNotBlank() -> epDate
                                    epDay != null -> epDay
                                    else -> "Episode ${matchingPosts.size - index}"
                                }
                                episodes.add(
                                    newEpisode(p.link ?: "$mainUrl/?p=${p.id}") {
                                        this.name = epTitle
                                        this.posterUrl = p.posterUrl
                                        this.data = p.link ?: "$mainUrl/?p=${p.id}"
                                        this.episode = matchingPosts.size - index
                                    }
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Guarantee current episode is in list
            if (episodes.isEmpty()) {
                episodes.add(currentEpisode)
            } else {
                val currentUrl = post.link ?: url
                val alreadyContains = episodes.any { it.data == currentUrl }
                if (!alreadyContains) {
                    episodes.add(0, currentEpisode)
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.plot = plot
                this.year = year
            }
        }

        // 4. Fallback to scraping webpage directly if REST API call didn't yield post
        val doc = app.get(url, headers = defaultHeaders).document
        val rawTitle = doc.selectFirst("h1.entry-title, h1")?.text() ?: "TamilDhool Video"
        val fallbackDate = extractDateFromText(rawTitle)
        val isPromo = rawTitle.contains("promo", ignoreCase = true) || rawTitle.contains("trailer", ignoreCase = true)
        val title = if (isPromo) rawTitle else cleanShowTitleFromText(rawTitle)
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst(".entry-content img")?.attr("abs:src")
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")

        return newTvSeriesLoadResponse(
            title,
            url,
            if (isPromo) TvType.Movie else TvType.TvSeries,
            listOf(
                newEpisode(url) {
                    this.name = if (isPromo) rawTitle else fallbackDate.ifBlank { "Episode 1" }
                    this.posterUrl = poster
                    this.data = url
                }
            )
        ) {
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
        // Fetch HTML content from either REST API post endpoint or post webpage
        val htmlContent = if (data.contains("/wp-json/wp/v2/posts/")) {
            val post = fetchSinglePost(data)
            post?.content?.rendered ?: ""
        } else {
            val slug = data.trimEnd('/').substringAfterLast('/')
            val wpContent = try {
                val slugEndpoint = "$mainUrl/wp-json/wp/v2/posts?slug=$slug"
                val posts = fetchPosts(slugEndpoint)
                posts.firstOrNull()?.content?.rendered
            } catch (_: Exception) {
                null
            }

            wpContent ?: app.get(data, headers = defaultHeaders).text
        }

        if (htmlContent.isBlank()) return false

        return TamilDhoolExtractor.resolveAndExtract(
            elementOrHtml = htmlContent,
            baseUrl = mainUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

    private suspend fun fetchPosts(url: String): List<WpPost> {
        return try {
            val res = app.get(url, headers = defaultHeaders).text
            parseJson<List<WpPost>>(res)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSinglePost(url: String): WpPost? {
        return try {
            val res = app.get(url, headers = defaultHeaders).text
            parseJson<WpPost>(res)
        } catch (_: Exception) {
            null
        }
    }

    private fun WpPost.toSearchResponse(): SearchResponse? {
        val linkUrl = link ?: return null
        val titleText = homeCardTitle.ifBlank { return null }

        return if (isStandalone) {
            newMovieSearchResponse(titleText, linkUrl, TvType.Movie) {
                this.posterUrl = this@toSearchResponse.posterUrl
            }
        } else {
            newTvSeriesSearchResponse(titleText, linkUrl, TvType.TvSeries) {
                this.posterUrl = this@toSearchResponse.posterUrl
            }
        }
    }

    private fun extractDateFromText(text: String): String {
        val dmyRegex = Regex("""\b(\d{1,2})[-./](\d{1,2})[-./](\d{2,4})\b""")
        val dmyMatch = dmyRegex.find(text)
        if (dmyMatch != null) {
            val d = dmyMatch.groupValues[1].padStart(2, '0')
            val m = dmyMatch.groupValues[2].padStart(2, '0')
            val y = dmyMatch.groupValues[3].let { if (it.length == 2) "20$it" else it }
            return "$d-$m-$y"
        }
        return ""
    }

    private fun cleanShowTitleFromText(raw: String): String {
        var text = raw.replace(Regex("""\b\d{1,2}[-./]\d{1,2}[-./]\d{2,4}\b"""), " ")
        text = text.replace(Regex("""\b(?:Episode|Epi|Ep)\s*\.?\s*\d+\b""", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("""\b(?:Day)\s*\.?\s*\d+\b""", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("""\b(?:Part)\s*\.?\s*\d+\b""", RegexOption.IGNORE_CASE), " ")

        val channelPatterns = listOf(
            """(?:Sun|Vijay|Star\s*Vijay|Zee\s*Tamil|Colors\s*Tamil|Kalaignar|Jaya|Raj|Polimer)\s*TV\s*(?:Serial|Show|Reality\s*Show)?""",
            """(?:Sun|Vijay|Zee|Colors|Kalaignar)\s*(?:Serial|Show)""",
            """(?:TV\s*)?(?:Serial|Show|Reality\s*Show)""",
            """(?:சன்|விஜய்|ஜீ|கலர்ஸ்|கலைஞர்)\s*(?:டிவி)?\s*(?:சீரியல்|நிகழ்ச்சி)?""",
            """(?:Special\s*Episode|Mega\s*Episode|Maha\s*Sangamam|Special\s*Show|Special)""",
            """(?:Watch\s*Online|Full\s*Episode|Today\s*Episode|Online)"""
        )
        for (p in channelPatterns) {
            text = text.replace(Regex("""(?i)\b$p\b"""), " ")
        }
        return text.replace('–', '-').replace('—', '-')
            .replace(Regex("""[\[\]\(\)]"""), " ")
            .replace(Regex("""[\s\-_|–—:]+$"""), "")
            .replace(Regex("""^[\s\-_|–—:]+"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .ifBlank { raw }
    }
}
