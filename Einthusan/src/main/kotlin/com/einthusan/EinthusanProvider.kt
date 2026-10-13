package com.einthusan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

class EinthusanProvider : MainAPI() {
    override var name = "Einthusan"
    override var mainUrl = "https://einthusan.tv"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "ta"

    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        MainPageData(name = "Tamil - Recently Added", data = "movie/results/?find=Recent&lang=tamil"),
        MainPageData(name = "Tamil - Popular (Past 30 Days)", data = "movie/results/?find=Popularity&lang=tamil&ptype=View&tp=l30d"),
        MainPageData(name = "Tamil - Top Rated", data = "movie/results/?find=Popularity&lang=tamil&ptype=Rating"),
        MainPageData(name = "Tamil - Staff Picks", data = "movie/results/?find=StaffPick&lang=tamil"),
        MainPageData(name = "Tamil - Regional Hits", data = "movie/results/?find=RegionalHit&lang=tamil"),
        MainPageData(name = "Telugu - Popular", data = "movie/results/?find=Popularity&lang=telugu&ptype=View&tp=l30d"),
        MainPageData(name = "Telugu - Recent", data = "movie/results/?find=Recent&lang=telugu"),
        MainPageData(name = "Hindi - Popular", data = "movie/results/?find=Popularity&lang=hindi&ptype=View&tp=l30d"),
        MainPageData(name = "Hindi - Recent", data = "movie/results/?find=Recent&lang=hindi"),
        MainPageData(name = "Malayalam - Popular", data = "movie/results/?find=Popularity&lang=malayalam&ptype=View&tp=l30d"),
        MainPageData(name = "Malayalam - Recent", data = "movie/results/?find=Recent&lang=malayalam"),
        MainPageData(name = "Kannada - Popular", data = "movie/results/?find=Popularity&lang=kannada&ptype=View&tp=l30d"),
        MainPageData(name = "Kannada - Recent", data = "movie/results/?find=Recent&lang=kannada")
    )

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        val DEFAULT_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9"
        )

        fun fixUrl(url: String): String {
            return when {
                url.startsWith("//") -> "https:$url"
                url.startsWith("/") -> "https://einthusan.tv$url"
                else -> url
            }
        }

        fun decodeObfuscated(encoded: String): String? {
            if (encoded.length < 13) return null
            return try {
                val len = encoded.length
                val rearranged = encoded.substring(0, 10) + encoded.last() + encoded.substring(12, len - 1)
                val padding = (4 - (rearranged.length % 4)) % 4
                val padded = rearranged + "=".repeat(padding)
                val decodedBytes = try {
                    android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
                } catch (_: Throwable) {
                    java.util.Base64.getDecoder().decode(padded)
                }
                String(decodedBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun extractEinthusanLinks(
            moviePageUrl: String,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            val fixedUrl = fixUrl(moviePageUrl)
            val movieIdMatch = Regex("""/movie/watch/([^/?]+)""").find(fixedUrl)
            val movieId = movieIdMatch?.groupValues?.get(1) ?: return false
            val langMatch = Regex("""[?&]lang=([^&]+)""").find(fixedUrl)
            val lang = langMatch?.groupValues?.get(1) ?: "tamil"

            var uhdFound = false
            var standardFound = false

            // 1. Direct UHD (4K) Link Detection on CDNs (Static unsigned MP4 with 'B' prefix)
            for (cdn in listOf("cdn2", "cdn1")) {
                val uhdUrl = "https://$cdn.einthusan.io/etv/content/B$movieId.mp4"
                try {
                    val head = app.head(uhdUrl, headers = DEFAULT_HEADERS, timeout = 10)
                    if (head.code in 200..299) {
                        callback(
                            newExtractorLink(
                                source = "Einthusan 4K UHD",
                                name = "Einthusan - 4K UHD (Direct MP4)",
                                url = uhdUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "https://einthusan.tv/"
                                this.quality = Qualities.P2160.value
                            }
                        )
                        uhdFound = true
                        break
                    }
                } catch (_: Exception) {}
            }

            // 2. Extract Page Parameters (data-pageid & data-ejpingables) and POST to AJAX endpoint
            try {
                val pageResp = app.get(fixedUrl, headers = DEFAULT_HEADERS)
                val pageDoc = pageResp.document

                val pageId = pageDoc.selectFirst("html")?.attr("data-pageid")?.ifBlank { null }
                    ?: Regex("""data-pageid="([^"]+)"""").find(pageResp.text)?.groupValues?.get(1)?.let {
                        org.jsoup.parser.Parser.unescapeEntities(it, true)
                    }

                val ejpingables = pageDoc.selectFirst("section#UIVideoPlayer")?.attr("data-ejpingables")?.ifBlank { null }
                    ?: Regex("""data-ejpingables="([^"]+)"""").find(pageResp.text)?.groupValues?.get(1)?.let {
                        org.jsoup.parser.Parser.unescapeEntities(it, true)
                    }

                if (!pageId.isNullOrBlank() && !ejpingables.isNullOrBlank()) {
                    val ajaxUrl = "https://einthusan.tv/ajax/movie/watch/$movieId/?lang=$lang"
                    val ajaxHeaders = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to fixedUrl,
                        "Origin" to "https://einthusan.tv"
                    )
                    val payload = mapOf(
                        "xEvent" to "UIVideoPlayer.PingOutcome",
                        "xJson" to """{"EJOutcomes":"$ejpingables","NativeHLS":false}""",
                        "gorilla.csrf.Token" to pageId
                    )

                    val ajaxResp = app.post(ajaxUrl, headers = ajaxHeaders, data = payload, timeout = 30)
                    if (ajaxResp.code in 200..299) {
                        val jsonTree = parseJson<Map<String, Any>>(ajaxResp.text)
                        val dataObj = jsonTree["Data"] as? Map<*, *>
                        val ejLinks = dataObj?.get("EJLinks") as? String

                        if (!ejLinks.isNullOrBlank() && ejLinks.length >= 13) {
                            val decodedJson = decodeObfuscated(ejLinks)
                            if (decodedJson != null) {
                                val linksObj = parseJson<Map<String, Any>>(decodedJson)
                                val datacenter = linksObj["Datacenter"] as? String ?: "CDN"

                                // HLS Adaptive Stream
                                val hls = linksObj["HLSLink"] as? String
                                if (!hls.isNullOrBlank()) {
                                    callback(
                                        newExtractorLink(
                                            source = "Einthusan HLS",
                                            name = "Einthusan - Adaptive HLS ($datacenter)",
                                            url = hls,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.referer = "https://einthusan.tv/"
                                            this.quality = Qualities.Unknown.value
                                        }
                                    )
                                    standardFound = true
                                }

                                // MP4 Full HD 1080p Stream
                                val mp4 = linksObj["MP4Link"] as? String
                                if (!mp4.isNullOrBlank()) {
                                    callback(
                                        newExtractorLink(
                                            source = "Einthusan HD",
                                            name = "Einthusan - 1080p Full HD ($datacenter)",
                                            url = mp4,
                                            type = ExtractorLinkType.VIDEO
                                        ) {
                                            this.referer = "https://einthusan.tv/"
                                            this.quality = Qualities.P1080.value
                                        }
                                    )
                                    standardFound = true
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Fallback CDN link for numeric IDs if AJAX did not return links
            if (!standardFound && !uhdFound && movieId.all { it.isDigit() }) {
                val fallbackUrl = "https://cdn2.einthusan.io/etv/content/D$movieId.mp4"
                callback(
                    newExtractorLink(
                        source = "Einthusan Fallback",
                        name = "Einthusan - HD Direct (Fallback)",
                        url = fallbackUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://einthusan.tv/"
                        this.quality = Qualities.P1080.value
                    }
                )
                return true
            }

            return uhdFound || standardFound
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val separator = if (request.data.contains("?")) "&" else "?"
        val url = "$mainUrl/${request.data}${separator}page=$page"

        val response = try {
            app.get(url, headers = DEFAULT_HEADERS)
        } catch (_: Exception) {
            null
        }

        if (response == null || response.code !in 200..299) {
            return newHomePageResponse(
                listOf(HomePageList(request.name, emptyList())),
                hasNext = false
            )
        }

        val doc = response.document
        val movies = parseMovieList(doc)

        val hasNext = movies.isNotEmpty() && (
            doc.selectFirst("a.next, .pagination a, div.numerical-nav a") != null ||
            movies.size >= 8
        )

        return newHomePageResponse(
            listOf(HomePageList(request.name, movies)),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/movie/results/?query=$encoded"

        val doc = try {
            app.get(searchUrl, headers = DEFAULT_HEADERS).document
        } catch (_: Exception) {
            return emptyList()
        }

        return parseMovieList(doc)
    }

    override suspend fun load(url: String): LoadResponse {
        val fixedUrl = fixUrl(url)
        val doc = app.get(fixedUrl, headers = DEFAULT_HEADERS).document

        val summary = doc.selectFirst("section#UIMovieSummary")
        val rawTitle = summary?.selectFirst("a.title h3")?.text()?.trim()
            ?: summary?.selectFirst("a.title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: "Einthusan Movie"

        val yearText = summary?.selectFirst(".info p")?.text()
        val scrapedYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(yearText ?: "")?.value?.toIntOrNull()
        val scrapedPoster = summary?.selectFirst(".block1 img, img")?.attr("src")?.let { fixUrl(it) }
        val scrapedPlot = summary?.selectFirst("p.synopsis")?.text()?.trim()

        val scrapedActors = summary?.select(".professionals .prof")?.mapNotNull { prof ->
            val actorName = prof.selectFirst("p")?.text()?.trim() ?: return@mapNotNull null
            val role = prof.selectFirst("label")?.text()?.trim() ?: "Cast"
            ActorData(Actor(actorName), roleString = role)
        } ?: emptyList()

        val scrapedTags = summary?.select(".info span, .info p")?.map { it.text().trim() }?.filter { it.isNotBlank() }

        // Fetch rich TMDB metadata: transparent logo, 4K/HD backdrop, posters, trailers, cast pictures & ratings
        val tmdb = try {
            TmdbHelper.getMovieMetadata(rawTitle, scrapedYear, lang)
        } catch (_: Exception) {
            null
        }

        val finalTitle = tmdb?.title ?: TmdbHelper.cleanMovieTitle(rawTitle)
        val finalPoster = tmdb?.getPosterUrl() ?: scrapedPoster
        val finalBackdrop = tmdb?.getBackdropUrl() ?: scrapedPoster
        val finalLogo = tmdb?.getLogoUrl(lang)
        val finalPlot = tmdb?.overview?.ifBlank { null } ?: scrapedPlot
        val finalYear = tmdb?.getYear() ?: scrapedYear
        val finalTags = tmdb?.genres?.mapNotNull { it.name }?.ifEmpty { null } ?: scrapedTags
        val finalActors = tmdb?.let { TmdbHelper.toActors(it) }?.ifEmpty { null } ?: scrapedActors
        val finalTrailers = tmdb?.let { TmdbHelper.toTrailers(it) }?.toMutableList() ?: mutableListOf()
        val finalRecs = tmdb?.let { TmdbHelper.toRecommendations(it, this) } ?: emptyList()

        return newMovieLoadResponse(finalTitle, fixedUrl, TvType.Movie, fixedUrl) {
            this.posterUrl = finalPoster
            this.backgroundPosterUrl = finalBackdrop
            this.logoUrl = finalLogo
            this.plot = finalPlot
            this.year = finalYear
            this.tags = finalTags
            this.actors = finalActors
            this.trailers = finalTrailers
            this.recommendations = finalRecs

            if (tmdb != null) {
                if (tmdb.runtime != null && tmdb.runtime > 0) {
                    this.duration = tmdb.runtime
                }
                if (tmdb.voteAverage != null && tmdb.voteAverage > 0.0) {
                    this.score = Score.from10(tmdb.voteAverage)
                }
                val syncMap = mutableMapOf<String, String>()
                syncMap["tmdb"] = tmdb.id.toString()
                if (!tmdb.imdbId.isNullOrBlank()) {
                    syncMap["imdb"] = tmdb.imdbId
                }
                this.syncData = syncMap
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return extractEinthusanLinks(data, callback)
    }

    private fun parseMovieList(doc: org.jsoup.nodes.Document): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        val movieElements = doc.select("section#UIMovieSummary li, div.tabview li").ifEmpty {
            doc.select("li:has(a[href*=/movie/watch/])")
        }

        for (li in movieElements) {
            val watchLinkTag = li.selectFirst("a[href*=/movie/watch/]") ?: continue
            val rawHref = watchLinkTag.attr("href")
            if (rawHref.isBlank()) continue
            val movieUrl = fixUrl(rawHref)

            val titleTag = li.selectFirst("a.title h3")
                ?: li.selectFirst("a.title")
                ?: li.selectFirst("h3")
            val title = titleTag?.text()?.trim() ?: continue
            if (title.isBlank()) continue

            val imgTag = li.selectFirst(".block1 img, img")
            val posterUrl = imgTag?.attr("src")?.let { fixUrl(it) }

            val cleanTitle = TmdbHelper.cleanMovieTitle(title)
            val infoText = li.selectFirst(".info p")?.text()
            val cardYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(infoText ?: "")?.value?.toIntOrNull()

            items.add(
                newMovieSearchResponse(cleanTitle, movieUrl, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = cardYear
                }
            )
        }

        return items.distinctBy { it.url }
    }
}
