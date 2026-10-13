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
        // Tamil
        MainPageData(name = "Tamil - Featured & Browse", data = "movie/browse/?lang=tamil"),
        MainPageData(name = "Tamil - Recently Added", data = "movie/results/?find=Recent&lang=tamil"),
        MainPageData(name = "Tamil - Popular (Past 30 Days)", data = "movie/results/?find=Popularity&lang=tamil&ptype=view&tp=l30d"),
        MainPageData(name = "Tamil - Top Rated (All Time)", data = "movie/results/?find=Popularity&lang=tamil&ptype=rating&tp=alltime"),
        MainPageData(name = "Tamil - Most Viewed (All Time)", data = "movie/results/?find=Popularity&lang=tamil&ptype=view&tp=alltime"),
        MainPageData(name = "Tamil - Staff Picks", data = "movie/results/?find=StaffPick&lang=tamil"),
        MainPageData(name = "Tamil - Regional Hits", data = "movie/results/?find=RegionalHit&lang=tamil"),
        MainPageData(name = "Tamil - Year 2025", data = "movie/results/?find=Year&lang=tamil&year=2025"),

        // Telugu
        MainPageData(name = "Telugu - Featured & Browse", data = "movie/browse/?lang=telugu"),
        MainPageData(name = "Telugu - Popular (Past 30 Days)", data = "movie/results/?find=Popularity&lang=telugu&ptype=view&tp=l30d"),
        MainPageData(name = "Telugu - Top Rated (All Time)", data = "movie/results/?find=Popularity&lang=telugu&ptype=rating&tp=alltime"),
        MainPageData(name = "Telugu - Most Viewed (All Time)", data = "movie/results/?find=Popularity&lang=telugu&ptype=view&tp=alltime"),
        MainPageData(name = "Telugu - Staff Picks", data = "movie/results/?find=StaffPick&lang=telugu"),
        MainPageData(name = "Telugu - Regional Hits", data = "movie/results/?find=RegionalHit&lang=telugu"),
        MainPageData(name = "Telugu - Year 2026", data = "movie/results/?find=Year&lang=telugu&year=2026"),

        // Hindi
        MainPageData(name = "Hindi - Featured & Browse", data = "movie/browse/?lang=hindi"),
        MainPageData(name = "Hindi - Recently Added", data = "movie/results/?find=Recent&lang=hindi"),
        MainPageData(name = "Hindi - Popular (Past 30 Days)", data = "movie/results/?find=Popularity&lang=hindi&ptype=view&tp=l30d"),
        MainPageData(name = "Hindi - Top Rated (All Time)", data = "movie/results/?find=Popularity&lang=hindi&ptype=rating&tp=alltime"),
        MainPageData(name = "Hindi - Most Viewed (All Time)", data = "movie/results/?find=Popularity&lang=hindi&ptype=view&tp=alltime"),
        MainPageData(name = "Hindi - Staff Picks", data = "movie/results/?find=StaffPick&lang=hindi"),
        MainPageData(name = "Hindi - Regional Hits", data = "movie/results/?find=RegionalHit&lang=hindi"),
        MainPageData(name = "Hindi - Year 2026", data = "movie/results/?find=Year&lang=hindi&year=2026"),

        // Malayalam
        MainPageData(name = "Malayalam - Featured & Browse", data = "movie/browse/?lang=malayalam"),
        MainPageData(name = "Malayalam - Recently Added", data = "movie/results/?find=Recent&lang=malayalam"),
        MainPageData(name = "Malayalam - Popular (Past 30 Days)", data = "movie/results/?find=Popularity&lang=malayalam&ptype=view&tp=l30d"),
        MainPageData(name = "Malayalam - Top Rated (All Time)", data = "movie/results/?find=Popularity&lang=malayalam&ptype=rating&tp=alltime"),
        MainPageData(name = "Malayalam - Staff Picks", data = "movie/results/?find=StaffPick&lang=malayalam"),
        MainPageData(name = "Malayalam - Regional Hits", data = "movie/results/?find=RegionalHit&lang=malayalam"),

        // Kannada
        MainPageData(name = "Kannada - Featured & Browse", data = "movie/browse/?lang=kannada"),
        MainPageData(name = "Kannada - Recently Added", data = "movie/results/?find=Recent&lang=kannada"),
        MainPageData(name = "Kannada - Popular (Past 30 Days)", data = "movie/results/?find=Popularity&lang=kannada&ptype=view&tp=l30d"),
        MainPageData(name = "Kannada - Top Rated (All Time)", data = "movie/results/?find=Popularity&lang=kannada&ptype=rating&tp=alltime"),
        MainPageData(name = "Kannada - Staff Picks", data = "movie/results/?find=StaffPick&lang=kannada"),
        MainPageData(name = "Kannada - Regional Hits", data = "movie/results/?find=RegionalHit&lang=kannada"),

        // Bengali, Marathi, Punjabi
        MainPageData(name = "Bengali - Recently Added", data = "movie/results/?find=Recent&lang=bengali"),
        MainPageData(name = "Bengali - Popular", data = "movie/results/?find=Popularity&lang=bengali&ptype=view&tp=alltime"),
        MainPageData(name = "Marathi - Recently Added", data = "movie/results/?find=Recent&lang=marathi"),
        MainPageData(name = "Marathi - Popular", data = "movie/results/?find=Popularity&lang=marathi&ptype=view&tp=alltime"),
        MainPageData(name = "Punjabi - Recently Added", data = "movie/results/?find=Recent&lang=punjabi"),
        MainPageData(name = "Punjabi - Popular", data = "movie/results/?find=Popularity&lang=punjabi&ptype=view&tp=alltime")
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

        fun extractLanguage(url: String, textContext: String? = null, fallback: String = "Tamil"): String {
            val urlLang = Regex("""[?&]lang=([a-zA-Z]+)""").find(url)?.groupValues?.get(1)?.trim()
            if (!urlLang.isNullOrBlank()) {
                return urlLang.lowercase().replaceFirstChar { it.uppercase() }
            }
            if (!textContext.isNullOrBlank()) {
                val supported = listOf("Tamil", "Telugu", "Hindi", "Malayalam", "Kannada", "Bengali", "Marathi", "Punjabi")
                for (l in supported) {
                    if (Regex("""\b$l\b""", RegexOption.IGNORE_CASE).containsMatchIn(textContext)) {
                        return l
                    }
                }
            }
            return fallback.lowercase().replaceFirstChar { it.uppercase() }
        }

        suspend fun extractEinthusanLinks(
            moviePageUrl: String,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            val fixedUrl = fixUrl(moviePageUrl)
            val movieIdMatch = Regex("""/movie/watch/([^/?]+)""").find(fixedUrl)
            val movieId = movieIdMatch?.groupValues?.get(1) ?: return false

            var standardFound = false
            var uhdFound = false

            // 1. Direct CDN MP4 extraction FIRST for all movies:
            // UHD: https://cdn2.einthusan.io/etv/content/B$movieId.mp4
            // HD:  https://cdn2.einthusan.io/etv/content/D$movieId.mp4
            val cdnHosts = listOf("cdn2", "cdn1")
            for (cdn in cdnHosts) {
                if (!uhdFound) {
                    val uhdUrl = "https://$cdn.einthusan.io/etv/content/B$movieId.mp4"
                    try {
                        val head = app.head(uhdUrl, headers = DEFAULT_HEADERS, timeout = 4)
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
                        }
                    } catch (_: Exception) {}
                }

                if (!standardFound) {
                    val hdUrl = "https://$cdn.einthusan.io/etv/content/D$movieId.mp4"
                    try {
                        val head = app.head(hdUrl, headers = DEFAULT_HEADERS, timeout = 4)
                        if (head.code in 200..299) {
                            callback(
                                newExtractorLink(
                                    source = "Einthusan HD",
                                    name = "Einthusan - 1080p Full HD (Direct MP4)",
                                    url = hdUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://einthusan.tv/"
                                    this.quality = Qualities.P1080.value
                                }
                            )
                            standardFound = true
                        }
                    } catch (_: Exception) {}
                }

                if (uhdFound && standardFound) break
            }

            // If direct links were found on CDN, return them immediately
            if (uhdFound || standardFound) {
                return true
            }

            // 2. Fallback: Fetch movie watch page to acquire CSRF tokens, pingables and AJAX session links
            try {
                val pageResp = app.get(fixedUrl, headers = DEFAULT_HEADERS, timeout = 30)
                val pageDoc = pageResp.document
                val cookies = pageResp.cookies
                val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

                var lang = Regex("""[?&]lang=([a-zA-Z]+)""").find(fixedUrl)?.groupValues?.get(1)?.lowercase()
                if (lang.isNullOrBlank()) {
                    lang = extractLanguage(fixedUrl, pageDoc.text()).lowercase()
                }

                val rawPageId = pageDoc.selectFirst("html")?.attr("data-pageid")?.ifBlank { null }
                    ?: Regex("""data-pageid="([^"]+)"""").find(pageResp.text)?.groupValues?.get(1)
                val pageId = rawPageId?.let { org.jsoup.parser.Parser.unescapeEntities(it, true) }

                val rawEjpingables = pageDoc.selectFirst("section#UIVideoPlayer")?.attr("data-ejpingables")?.ifBlank { null }
                    ?: Regex("""data-ejpingables="([^"]+)"""").find(pageResp.text)?.groupValues?.get(1)
                val ejpingables = rawEjpingables?.let { org.jsoup.parser.Parser.unescapeEntities(it, true) }

                if (!pageId.isNullOrBlank() && !ejpingables.isNullOrBlank()) {
                    val ajaxUrl = "https://einthusan.tv/ajax/movie/watch/$movieId/?lang=$lang"
                    val ajaxHeaders = mutableMapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to fixedUrl,
                        "Origin" to "https://einthusan.tv"
                    )
                    if (cookieHeader.isNotBlank()) {
                        ajaxHeaders["Cookie"] = cookieHeader
                    }

                    val payload = mapOf(
                        "xEvent" to "UIVideoPlayer.PingOutcome",
                        "xJson" to """{"EJOutcomes":"$ejpingables","NativeHLS":false}""",
                        "gorilla.csrf.Token" to pageId
                    )

                    val ajaxResp = app.post(
                        ajaxUrl,
                        headers = ajaxHeaders,
                        data = payload,
                        cookies = cookies,
                        timeout = 30
                    )

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

            // 3. Fallback direct link attempt for any movie ID if still not found
            if (!standardFound && !uhdFound) {
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
            return newHomePageResponse(emptyList(), hasNext = false)
        }

        val doc = response.document
        val langFromReq = Regex("""lang=([a-zA-Z]+)""").find(request.data)?.groupValues?.get(1)
        val movies = parseMovieList(doc, defaultLanguage = langFromReq)

        if (movies.isEmpty()) {
            return newHomePageResponse(emptyList(), hasNext = false)
        }

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
        val languages = listOf("tamil", "telugu", "hindi", "malayalam", "kannada", "bengali", "marathi", "punjabi")

        val results = languages.amap { lang ->
            val searchUrl = "$mainUrl/movie/results/?query=$encoded&lang=$lang"
            val doc = try {
                app.get(searchUrl, headers = DEFAULT_HEADERS).document
            } catch (_: Exception) {
                null
            } ?: return@amap emptyList()

            parseMovieList(doc, defaultLanguage = lang)
        }.flatten()

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val fixedUrl = fixUrl(url)
        val doc = app.get(fixedUrl, headers = DEFAULT_HEADERS).document

        val summary = doc.selectFirst("section#UIMovieSummary")
        val rawTitle = summary?.selectFirst("a.title h2, a.title h3, a.title, h1, h2, h3")?.text()?.trim()
            ?: doc.selectFirst("h1, h2, h3")?.text()?.trim()
            ?: "Einthusan Movie"

        val yearText = summary?.selectFirst(".info p")?.text()
        val scrapedYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(yearText ?: "")?.value?.toIntOrNull()
        val scrapedPlot = summary?.selectFirst("p.synopsis")?.text()?.trim()

        val scrapedCover = summary?.selectFirst("a#movie_cover_link img, .block1 img, img")?.attr("src")?.let { fixUrl(it) }
            ?: doc.selectFirst("img.cover, a#movie_cover_link img, img")?.attr("src")?.let { fixUrl(it) }

        val displayLang = extractLanguage(fixedUrl, yearText ?: doc.text())
        val isoLang = TmdbHelper.toIso6391(displayLang)

        val scrapedActors = summary?.select(".professionals .prof")?.mapNotNull { prof ->
            val actorName = prof.selectFirst("p")?.text()?.trim() ?: return@mapNotNull null
            val role = prof.selectFirst("label")?.text()?.trim() ?: "Cast"
            ActorData(Actor(actorName), roleString = role)
        } ?: emptyList()

        val scrapedTags = summary?.select(".info span, .info p")?.map { it.text().trim() }?.filter { it.isNotBlank() }

        // Fetch rich TMDB metadata: transparent logo, 4K/HD backdrop, posters, trailers, cast pictures & ratings
        val tmdb = try {
            TmdbHelper.getMovieMetadata(rawTitle, scrapedYear, isoLang)
        } catch (_: Exception) {
            null
        }

        val baseTitle = tmdb?.title ?: TmdbHelper.cleanMovieTitle(rawTitle)
        val finalTitle = if (baseTitle.contains(displayLang, ignoreCase = true)) {
            baseTitle
        } else {
            "$baseTitle ($displayLang)"
        }

        val posterCandidate = tmdb?.getPosterUrl(isoLang) ?: scrapedCover
        val finalPoster = TmdbHelper.getPosterWithLanguage(posterCandidate, displayLang)
        val finalBackdrop = tmdb?.getBackdropUrl() ?: scrapedCover
        val finalLogo = tmdb?.getLogoUrl(isoLang)
        val basePlot = tmdb?.overview?.ifBlank { null } ?: scrapedPlot
        val finalYear = tmdb?.getYear() ?: scrapedYear
        val finalPlot = buildString {
            append("Language: $displayLang • Year: ${finalYear ?: "N/A"}")
            if (!basePlot.isNullOrBlank()) {
                append("\n\n")
                append(basePlot)
            }
        }
        val finalTags = tmdb?.genres?.mapNotNull { it.name }?.ifEmpty { null } ?: scrapedTags
        val finalActors = tmdb?.let { TmdbHelper.toActors(it) }?.ifEmpty { null } ?: scrapedActors
        val finalTrailers = tmdb?.let { TmdbHelper.toTrailers(it) }?.toMutableList() ?: mutableListOf()

        // Fetch real recommendations from Einthusan in the same language (only movies that exist on Einthusan)
        val finalRecs = try {
            val recUrl = "$mainUrl/movie/results/?find=Popularity&lang=${displayLang.lowercase()}&ptype=view&tp=l30d"
            val recDoc = app.get(recUrl, headers = DEFAULT_HEADERS).document
            parseMovieList(recDoc, defaultLanguage = displayLang)
                .filter { it.url != fixedUrl }
                .take(15)
        } catch (_: Exception) {
            emptyList()
        }

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

    private suspend fun parseMovieList(
        doc: org.jsoup.nodes.Document,
        defaultLanguage: String? = null
    ): List<SearchResponse> {
        val movieElements = doc.select("section#UIMovieSummary li, div.tabview li").ifEmpty {
            doc.select("li:has(a[href*=/movie/watch/])")
        }

        val items = movieElements.amap { li ->
            val watchLinkTag = li.selectFirst("a[href*=/movie/watch/]") ?: return@amap null
            val rawHref = watchLinkTag.attr("href")
            if (rawHref.isBlank()) return@amap null

            // Ensure URL preserves language so language variants are separate content items
            val normalizedHref = if (defaultLanguage != null && (rawHref.endsWith("?lang=") || !rawHref.contains("lang="))) {
                if (rawHref.endsWith("?lang=")) "${rawHref}$defaultLanguage" else "$rawHref?lang=$defaultLanguage"
            } else {
                rawHref
            }
            val movieUrl = fixUrl(normalizedHref)

            val titleTag = li.selectFirst("a.title h2, a.title h3, a.title, h2, h3")
            val title = titleTag?.text()?.trim() ?: return@amap null
            if (title.isBlank()) return@amap null

            val cleanTitle = TmdbHelper.cleanMovieTitle(title)
            val infoText = li.selectFirst(".info p")?.text()
            val cardYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(infoText ?: "")?.value?.toIntOrNull()

            val cardLang = extractLanguage(movieUrl, infoText, defaultLanguage ?: "Tamil")
            val isoLang = TmdbHelper.toIso6391(cardLang)

            val displayTitle = if (cleanTitle.contains(cardLang, ignoreCase = true)) {
                cleanTitle
            } else {
                "$cleanTitle ($cardLang)"
            }

            val scrapedCover = li.selectFirst("a#movie_cover_link img, .block1 img, img")?.attr("src")?.let { fixUrl(it) }

            // Resolve TMDB poster with language badge, cleanly falling back to Einthusan native cover
            val tmdb = try {
                TmdbHelper.getMovieMetadata(cleanTitle, cardYear, isoLang)
            } catch (_: Exception) {
                null
            }
            val posterCandidate = tmdb?.getPosterUrl(isoLang) ?: scrapedCover
            val finalPosterUrl = TmdbHelper.getPosterWithLanguage(posterCandidate, cardLang)

            newMovieSearchResponse(displayTitle, movieUrl, TvType.Movie) {
                this.posterUrl = finalPosterUrl
                this.year = cardYear
            }
        }.filterNotNull()

        return items.distinctBy { it.url }
    }
}
