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

    // Main page categories powered by WordPress REST API
    override val mainPage = mainPageOf(
        "posts?per_page=20" to "Latest Episodes (சமீபத்தியவை)",
        "posts?search=Sun+TV&per_page=20" to "Sun TV Serials (சன் டிவி)",
        "posts?search=Vijay+TV&per_page=20" to "Vijay TV Serials (விஜய் டிவி)",
        "posts?search=Zee+Tamil&per_page=20" to "Zee Tamil Serials (ஜீ தமிழ்)",
        "posts?search=Colors+Tamil&per_page=20" to "Colors Tamil (கலர்ஸ் தமிழ்)",
        "posts?search=Kalaignar+TV&per_page=20" to "Kalaignar TV (கலைஞர் டிவி)",
        "posts?search=Web+Series&per_page=20" to "Tamil Web Series (வலைத் தொடர்)",
        "posts?search=Show&per_page=20" to "TV Shows & Reality (நிகழ்ச்சிகள்)"
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val endpoint = "$mainUrl/wp-json/wp/v2/${request.data}&page=$page&_embed=1"
        val posts = fetchPosts(endpoint)

        val items = posts.mapNotNull { post ->
            post.toSearchResponse()
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "$mainUrl/wp-json/wp/v2/posts?search=$encoded&_embed=1&per_page=20"
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
            val title = post.cleanTitle
            val poster = post.posterUrl
            val plot = post.excerpt?.rendered?.let { Jsoup.parse(it).text() }
            val year = post.date?.take(4)?.toIntOrNull()

            val episodes = mutableListOf<Episode>()

            // Try to fetch related episodes from the show's category
            val categoryId = post.categories?.lastOrNull()
            if (categoryId != null) {
                try {
                    val catEndpoint = "$mainUrl/wp-json/wp/v2/posts?categories=$categoryId&per_page=50&_embed=1"
                    val catPosts = fetchPosts(catEndpoint)
                    if (catPosts.isNotEmpty()) {
                        catPosts.forEachIndexed { index, p ->
                            episodes.add(
                                newEpisode(p.link ?: "$mainUrl/?p=${p.id}") {
                                    this.name = p.cleanTitle
                                    this.posterUrl = p.posterUrl
                                    this.episode = catPosts.size - index
                                }
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Ignore category fetch error and use current post
                }
            }

            // Fallback: If no episodes from category, add this post as single episode
            if (episodes.isEmpty()) {
                episodes.add(
                    newEpisode(post.link ?: url) {
                        this.name = title
                        this.posterUrl = poster
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }

        // 4. Fallback to scraping webpage directly if REST API call didn't yield post
        val doc = app.get(url, headers = defaultHeaders).document
        val title = doc.selectFirst("h1.entry-title, h1")?.text() ?: "TamilDhool Video"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst(".entry-content img")?.attr("abs:src")
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")

        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            listOf(
                newEpisode(url) {
                    this.name = title
                    this.posterUrl = poster
                }
            )
        ) {
            this.posterUrl = poster
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
            // Check if slug can fetch the post JSON for cleaner content
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

        // Extract using TamilDhoolExtractor
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
        val titleText = cleanTitle.ifBlank { return null }

        return newTvSeriesSearchResponse(titleText, linkUrl, TvType.TvSeries) {
            this.posterUrl = this@toSearchResponse.posterUrl
        }
    }
}
