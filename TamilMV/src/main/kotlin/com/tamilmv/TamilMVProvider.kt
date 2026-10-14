package com.tamilmv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class TamilMVProvider : MainAPI() {
    override var name = "1TamilMV"
    override var mainUrl = "https://www.1tamilmv.fi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent)
    override var lang = "ta"
    override val hasMainPage = true
    override val hasQuickSearch = true

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val DEFAULT_HEADERS = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9"
        )
    }

    @Volatile
    private var currentDomain: String = mainUrl

    override val mainPage = mainPageOf(
        MainPageData(name = "Latest Releases (Week)", data = ""),
        MainPageData(name = "Tamil WEB-HD / BluRay", data = "forums/forum/11-web-hd-itunes-hd-bluray"),
        MainPageData(name = "Tamil Web Series & TV", data = "forums/forum/19-web-series-tv-shows"),
        MainPageData(name = "Tamil PreDVD / DVDScr", data = "forums/forum/10-predvd-dvdscr-cam-tc"),
        MainPageData(name = "Tamil HD-Rips", data = "forums/forum/12-hd-rips-dvd-rips-br-rips"),
        MainPageData(name = "Telugu WEB-HD / BluRay", data = "forums/forum/24-web-hd-itunes-hd-bluray"),
        MainPageData(name = "Telugu Web Series & TV", data = "forums/forum/33-web-series-tv-shows"),
        MainPageData(name = "Telugu PreDVD / DVDScr", data = "forums/forum/23-predvd-dvdscr-cam-tc"),
        MainPageData(name = "Hindi WEB-HD / BluRay", data = "forums/forum/58-web-hd-itunes-hd-bluray"),
        MainPageData(name = "Hindi Web Series & TV", data = "forums/forum/66-web-series-tv-shows"),
        MainPageData(name = "Hindi PreDVD / DVDScr", data = "forums/forum/57-predvd-dvdscr-cam-tc"),
        MainPageData(name = "Malayalam WEB-HD / BluRay", data = "forums/forum/36-web-hd-itunes-hd-bluray"),
        MainPageData(name = "Malayalam Web Series", data = "forums/forum/44-web-series-tv-shows"),
        MainPageData(name = "Kannada Releases", data = "forums/forum/69-web-hd-itunes-hd-bluray"),
        MainPageData(name = "English Movies", data = "forums/forum/49-web-hd-itunes-hd-bluray"),
        MainPageData(name = "English Web Series", data = "forums/forum/55-web-series-tv-shows")
    )

    private fun updateDomain(newUrl: String?) {
        if (!newUrl.isNullOrBlank()) {
            val trimmed = newUrl.trimEnd('/')
            val match = Regex("""^(https?://[^/]+)""").find(trimmed)
            if (match != null) {
                currentDomain = match.groupValues[1]
            }
        }
    }

    private fun fixUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$currentDomain$url"
            !url.startsWith("http") -> "$currentDomain/$url"
            else -> url
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (request.data.isBlank()) {
            if (page > 1) {
                return newHomePageResponse(
                    listOf(HomePageList(request.name, emptyList(), isHorizontalImages = false)),
                    hasNext = false
                )
            }
            "$currentDomain/"
        } else {
            val sectionPath = request.data.trim('/')
            if (page <= 1) {
                "$currentDomain/index.php?/$sectionPath/"
            } else {
                "$currentDomain/index.php?/$sectionPath/page/$page/"
            }
        }

        val res = try {
            app.get(targetUrl, headers = DEFAULT_HEADERS)
        } catch (_: Exception) {
            null
        }

        if (res == null || res.code !in 200..299) {
            return newHomePageResponse(
                listOf(HomePageList(request.name, emptyList(), isHorizontalImages = false)),
                hasNext = false
            )
        }

        updateDomain(res.url)
        val doc = res.document

        val topicLinks = mutableListOf<Pair<String, String>>()
        val seenUrls = mutableSetOf<String>()

        if (request.data.isBlank()) {
            // Parse "Week Releases" widget on home page
            val weekWidgets = doc.select("li.ipsWidget:has(h3:contains(Week Releases)), .ipsWidget:has(h2:contains(Week Releases)), [data-blocktitle*='Week Releases']")
            for (widget in weekWidgets) {
                for (a in widget.select("a[href*=/forums/topic/]")) {
                    val title = a.text().trim()
                    val href = fixUrl(a.attr("href").substringBefore("&do=findComment"))
                    if (isValidTopicTitle(title) && seenUrls.add(href)) {
                        topicLinks.add(title to href)
                    }
                }
            }
            // Fallback if widget not matched
            if (topicLinks.isEmpty()) {
                for (a in doc.select("a[href*=/forums/topic/]")) {
                    val title = a.text().trim()
                    val href = fixUrl(a.attr("href").substringBefore("&do=findComment"))
                    if (isValidTopicTitle(title) && seenUrls.add(href)) {
                        topicLinks.add(title to href)
                    }
                }
            }
        } else {
            // Parse topic items in forum listing
            val dataItems = doc.select("li.ipsDataItem")
            for (item in dataItems) {
                val a = item.selectFirst("span.ipsType_break a, h4.ipsDataItem_title a, a[data-ipstooltip]") ?: continue
                val title = a.text().trim()
                val href = fixUrl(a.attr("href").substringBefore("&do=findComment"))
                if (isValidTopicTitle(title) && seenUrls.add(href)) {
                    topicLinks.add(title to href)
                }
            }
        }

        val homeItems = topicLinks.amap { (title, href) ->
            toSearchResponse(title, href, defaultCategory = request.name)
        }.filterNotNull()

        val hasNext = if (request.data.isBlank()) {
            false
        } else {
            doc.selectFirst("li.ipsPagination_next:not(.ipsPagination_inactive) a, a[rel=next]") != null
        }

        return newHomePageResponse(
            listOf(HomePageList(request.name, homeItems, isHorizontalImages = false)),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$currentDomain/index.php?/search/&q=$encoded&type=forums_topic"

        val res = try {
            app.get(searchUrl, headers = DEFAULT_HEADERS)
        } catch (_: Exception) {
            return emptyList()
        }

        if (res.code !in 200..299) return emptyList()
        updateDomain(res.url)
        val doc = res.document

        val topicLinks = mutableListOf<Pair<String, String>>()
        val seenUrls = mutableSetOf<String>()

        val streamItems = doc.select("li.ipsStreamItem")
        for (item in streamItems) {
            val a = item.selectFirst("a[href*=/forums/topic/]") ?: continue
            val title = a.text().trim()
            val href = fixUrl(a.attr("href").substringBefore("&do=findComment"))
            if (isValidTopicTitle(title) && seenUrls.add(href)) {
                topicLinks.add(title to href)
            }
        }

        if (topicLinks.isEmpty()) {
            for (a in doc.select("a[href*=/forums/topic/]")) {
                val title = a.text().trim()
                val href = fixUrl(a.attr("href").substringBefore("&do=findComment"))
                if (isValidTopicTitle(title) && seenUrls.add(href)) {
                    topicLinks.add(title to href)
                }
            }
        }

        return topicLinks.amap { (title, href) ->
            toSearchResponse(title, href)
        }.filterNotNull()
    }

    private fun isValidTopicTitle(title: String): Boolean {
        if (title.length < 3) return false
        val lower = title.lowercase()
        return !lower.startsWith("languages") &&
                !lower.startsWith("rules") &&
                !lower.startsWith("security alert") &&
                !lower.startsWith("telegram") &&
                !lower.contains("members lounge")
    }

    private fun parseQuality(rawTitle: String): SearchQuality {
        val upper = rawTitle.uppercase()
        return when {
            upper.contains("4K") || upper.contains("2160P") || upper.contains("UHD") -> SearchQuality.FourK
            upper.contains("BLURAY") || upper.contains("BR-RIP") || upper.contains("BRRIP") -> SearchQuality.BlueRay
            upper.contains("HDR") -> SearchQuality.HDR
            upper.contains("1080P") || upper.contains("WEB-DL") || upper.contains("WEBDL") -> SearchQuality.HD
            upper.contains("720P") || upper.contains("HDRIP") -> SearchQuality.HD
            upper.contains("PREDVD") || upper.contains("DVDSCR") || upper.contains("HQ") -> SearchQuality.HQ
            upper.contains("CAM") || upper.contains("TC") -> SearchQuality.Cam
            upper.contains("DVD") -> SearchQuality.DVD
            upper.contains("480P") -> SearchQuality.SD
            else -> SearchQuality.HD
        }
    }

    private fun extractLanguages(rawTitle: String, defaultCategory: String? = null): String {
        val bracketMatch = Regex("""\[\s*([A-Za-z\s\+\,\/]+(?:Tam|Tel|Hin|Mal|Kan|Eng|Tamil|Telugu|Hindi|Malayalam|Kannada|English)[A-Za-z\s\+\,\/]*)\s*\]""", RegexOption.IGNORE_CASE).find(rawTitle)
        if (bracketMatch != null) {
            val group = bracketMatch.groupValues[1]
            val tokens = group.split(Regex("""[\+\,\/]""")).map { it.trim() }.filter { it.isNotBlank() }
            val found = mutableListOf<String>()
            for (token in tokens) {
                val lower = token.lowercase()
                when {
                    lower.contains("tam") -> if (!found.contains("Tamil")) found.add("Tamil")
                    lower.contains("tel") -> if (!found.contains("Telugu")) found.add("Telugu")
                    lower.contains("hin") -> if (!found.contains("Hindi")) found.add("Hindi")
                    lower.contains("mal") -> if (!found.contains("Malayalam")) found.add("Malayalam")
                    lower.contains("kan") -> if (!found.contains("Kannada")) found.add("Kannada")
                    lower.contains("eng") -> if (!found.contains("English")) found.add("English")
                }
            }
            if (found.isNotEmpty()) {
                return formatLanguageList(found)
            }
        }

        val found = mutableListOf<String>()
        val lower = rawTitle.lowercase()
        if (Regex("""\b(tam|tamil)\b""").containsMatchIn(lower)) found.add("Tamil")
        if (Regex("""\b(tel|telugu)\b""").containsMatchIn(lower)) found.add("Telugu")
        if (Regex("""\b(hin|hindi)\b""").containsMatchIn(lower)) found.add("Hindi")
        if (Regex("""\b(mal|malayalam)\b""").containsMatchIn(lower)) found.add("Malayalam")
        if (Regex("""\b(kan|kannada)\b""").containsMatchIn(lower)) found.add("Kannada")
        if (Regex("""\b(eng|english|hollywood)\b""").containsMatchIn(lower)) found.add("English")

        if (found.isNotEmpty()) {
            return formatLanguageList(found)
        }

        val catLower = (defaultCategory ?: "").lowercase()
        return when {
            catLower.contains("telugu") -> "Telugu"
            catLower.contains("hindi") -> "Hindi"
            catLower.contains("malayalam") -> "Malayalam"
            catLower.contains("kannada") -> "Kannada"
            catLower.contains("english") -> "English"
            else -> "Tamil"
        }
    }

    private fun formatLanguageList(langs: List<String>): String {
        if (langs.isEmpty()) return "Tamil"
        if (langs.size == 1) return langs[0]

        val fullJoined = langs.joinToString(" + ")
        if (fullJoined.length <= 25) return fullJoined

        val shortMap = mapOf(
            "Tamil" to "Tam",
            "Telugu" to "Tel",
            "Hindi" to "Hin",
            "Malayalam" to "Mal",
            "Kannada" to "Kan",
            "English" to "Eng"
        )
        val shortJoined = langs.map { shortMap[it] ?: it }.joinToString(" + ")
        return if (shortJoined.length <= 35) shortJoined else "Multi Audio"
    }

    private fun getPrimaryIsoLang(languages: String): String {
        val first = languages.split("+").firstOrNull()?.trim() ?: languages
        return TmdbHelper.toIso6391(first)
    }

    private fun parseTitleAndYear(rawTitle: String): Pair<String, Int?> {
        val pattern = Regex("""^(?:\[[^\]]+\]\s*)?(.*?)(?:\s*\((\d{4})\)|\s+(\d{4})\b)""")
        val match = pattern.find(rawTitle)
        val extractedName = match?.groupValues?.get(1)?.trim() ?: rawTitle
        val extractedYear = match?.groupValues?.get(2)?.toIntOrNull() ?: match?.groupValues?.get(3)?.toIntOrNull()

        var clean = extractedName
            .replace(Regex("""(?i)\b(?:Tamil|Telugu|Hindi|Malayalam|Kannada|English|Eng)\b.*"""), "")
            .replace(Regex("""(?i)\b(?:TRUE|WEB-DL|WEBDL|HDRip|PreDVD|DVDScr|CAM|TC|BluRay|HDTV)\b.*"""), "")
            .trimEnd('-', ':', ' ', '|', '[', '(')

        if (clean.isBlank()) {
            clean = TmdbHelper.cleanTitle(rawTitle)
        }

        return clean to extractedYear
    }

    private fun isTvSeries(rawTitle: String, url: String): Boolean {
        val lower = "$rawTitle $url".lowercase()
        return lower.contains(Regex("""\b(s\d+|ep\s*\(?[\d\-]+\)?|season|episode|web-series|tv-shows)\b"""))
    }

    private suspend fun toSearchResponse(
        rawTitle: String,
        href: String,
        defaultCategory: String? = null
    ): SearchResponse? {
        val (cleanTitle, year) = parseTitleAndYear(rawTitle)
        if (cleanTitle.isBlank()) return null

        val languages = extractLanguages(rawTitle, defaultCategory)
        val isoLang = getPrimaryIsoLang(languages)
        val isTv = isTvSeries(rawTitle, href)
        val quality = parseQuality(rawTitle)

        val tmdb = try {
            TmdbHelper.getMetadata(cleanTitle, year, isoLang, isTv)
        } catch (_: Exception) {
            null
        }

        val displayTitle = tmdb?.getResolvedTitle() ?: cleanTitle
        val resolvedYear = tmdb?.getYear() ?: year
        val posterCandidate = tmdb?.getPosterUrl(isoLang)
        val finalPoster = TmdbHelper.getPosterWithLanguage(posterCandidate, languages)

        return if (isTv) {
            newTvSeriesSearchResponse(displayTitle, href, TvType.TvSeries) {
                this.posterUrl = finalPoster
                this.year = resolvedYear
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(displayTitle, href, TvType.Movie) {
                this.posterUrl = finalPoster
                this.year = resolvedYear
                this.quality = quality
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url, headers = DEFAULT_HEADERS)
        updateDomain(res.url)
        val doc = res.document

        val pageTitle = doc.selectFirst("h1.ipsType_pageTitle, h1")?.text()?.trim() ?: doc.title()
        val (cleanTitle, parsedYear) = parseTitleAndYear(pageTitle)

        val languages = extractLanguages(pageTitle)
        val isoLang = getPrimaryIsoLang(languages)
        val isTv = isTvSeries(pageTitle, url)

        val commentWrap = doc.selectFirst("div[data-role=commentContent], div.cPost_contentWrap")

        // Extract scraped poster image from first post
        val scrapedPoster = commentWrap?.select("img")?.mapNotNull { img ->
            val src = img.attr("src").ifBlank { img.attr("data-src") }
            if (src.isBlank()) null else fixUrl(src)
        }?.firstOrNull { src ->
            !src.contains("torrent", ignoreCase = true) &&
                    !src.contains("torrborder", ignoreCase = true) &&
                    !src.contains("avatar", ignoreCase = true) &&
                    !src.contains("giphy", ignoreCase = true) &&
                    !src.endsWith(".gif", ignoreCase = true)
        }

        // Query TMDB metadata
        val tmdb = try {
            TmdbHelper.getMetadata(cleanTitle, parsedYear, isoLang, isTv)
        } catch (_: Exception) {
            null
        }

        val baseTitle = tmdb?.getResolvedTitle() ?: cleanTitle
        val finalTitle = if (baseTitle.contains(languages, ignoreCase = true)) {
            baseTitle
        } else {
            "$baseTitle ($languages)"
        }

        val posterCandidate = tmdb?.getPosterUrl(isoLang) ?: scrapedPoster
        val finalPoster = TmdbHelper.getPosterWithLanguage(posterCandidate, languages)
        val finalBackdrop = tmdb?.getBackdropUrl() ?: scrapedPoster
        val finalLogo = tmdb?.getLogoUrl(isoLang)
        val basePlot = tmdb?.overview?.ifBlank { null }
        val finalYear = tmdb?.getYear() ?: parsedYear

        val finalPlot = buildString {
            append("Language: $languages • Year: ${finalYear ?: "N/A"}")
            if (!basePlot.isNullOrBlank()) {
                append("\n\n")
                append(basePlot)
            }
        }

        val finalTags = tmdb?.genres?.mapNotNull { it.name }?.ifEmpty { null }
        val finalActors = tmdb?.let { TmdbHelper.toActors(it) }?.ifEmpty { null }
        val finalTrailers = tmdb?.let { TmdbHelper.toTrailers(it) }?.toMutableList() ?: mutableListOf()
        val finalRecs = tmdb?.let { TmdbHelper.toRecommendations(it, this) } ?: emptyList()

        val syncMap = mutableMapOf<String, String>()
        if (tmdb != null) {
            syncMap["tmdb"] = tmdb.id.toString()
            val imdb = tmdb.getResolvedImdbId()
            if (!imdb.isNullOrBlank()) {
                syncMap["imdb"] = imdb
            }
        }

        if (isTv) {
            val episodes = parseEpisodes(pageTitle, url, finalPoster, commentWrap)
            return newTvSeriesLoadResponse(finalTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = finalBackdrop
                this.logoUrl = finalLogo
                this.plot = finalPlot
                this.year = finalYear
                this.tags = finalTags
                this.actors = finalActors
                this.trailers = finalTrailers
                this.recommendations = finalRecs
                this.syncData = syncMap
                if (tmdb?.voteAverage != null && tmdb.voteAverage > 0.0) {
                    this.score = Score.from10(tmdb.voteAverage)
                }
            }
        } else {
            return newMovieLoadResponse(finalTitle, url, TvType.Movie, url) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = finalBackdrop
                this.logoUrl = finalLogo
                this.plot = finalPlot
                this.year = finalYear
                this.tags = finalTags
                this.actors = finalActors
                this.trailers = finalTrailers
                this.recommendations = finalRecs
                this.syncData = syncMap
                if (tmdb?.runtime != null && tmdb.runtime > 0) {
                    this.duration = tmdb.runtime
                }
                if (tmdb?.voteAverage != null && tmdb.voteAverage > 0.0) {
                    this.score = Score.from10(tmdb.voteAverage)
                }
            }
        }
    }

    private fun parseEpisodes(pageTitle: String, topicUrl: String, poster: String?, commentWrap: Element?): List<Episode> {
        val seasonMatch = Regex("""(?i)\bS(\d{1,2})\b""").find(pageTitle)
        val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

        // 1. Check if the post contains individual episode magnets (e.g. EP01, EP02...)
        val discoveredEps = mutableSetOf<Int>()
        if (commentWrap != null) {
            for (a in commentWrap.select("a[href^=magnet:]")) {
                val dn = extractQueryParam(a.attr("href"), "dn") ?: ""
                if (!dn.contains(Regex("""(?i)\bEP\s*\(?\d+\s*[-–]\s*\d+\)?"""))) {
                    val m = Regex("""(?i)\bEP\s*0*(\d+)\b""").find(dn)
                    if (m != null) {
                        m.groupValues[1].toIntOrNull()?.let { discoveredEps.add(it) }
                    }
                }
            }
        }

        if (discoveredEps.size > 1) {
            return discoveredEps.sorted().map { epNum ->
                newEpisode("$topicUrl#ep=$epNum&season=$seasonNum&startEp=1") {
                    this.name = "Episode $epNum"
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = poster
                }
            }
        }

        // 2. Check for range in title or text like EP (01-06) or EP(01-10)
        val rangeMatch = Regex("""(?i)\bEP\s*\(?(\d+)\s*[-–]\s*(\d+)\)?""").find(pageTitle)
            ?: commentWrap?.let { Regex("""(?i)\bEP\s*\(?(\d+)\s*[-–]\s*(\d+)\)?""").find(it.text()) }

        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: 1
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
            if (end >= start && end - start < 100) {
                return (start..end).map { epNum ->
                    newEpisode("$topicUrl#ep=$epNum&season=$seasonNum&startEp=$start") {
                        this.name = "Episode $epNum"
                        this.season = seasonNum
                        this.episode = epNum
                        this.posterUrl = poster
                    }
                }
            }
        }

        // 3. Check for single episode like EP05 or EP 38
        val singleMatch = Regex("""(?i)\bEP\s*0*(\d+)\b""").find(pageTitle)
        if (singleMatch != null) {
            val epNum = singleMatch.groupValues[1].toIntOrNull() ?: 1
            return listOf(
                newEpisode("$topicUrl#ep=$epNum&season=$seasonNum&startEp=$epNum") {
                    this.name = "Episode $epNum"
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = poster
                }
            )
        }

        // 4. Default single episode for the whole batch
        return listOf(
            newEpisode("$topicUrl#ep=1&season=$seasonNum&startEp=1") {
                this.name = "Full Season / Release"
                this.season = seasonNum
                this.episode = 1
                this.posterUrl = poster
            }
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val topicUrl = data.substringBefore("#")
        val fragment = data.substringAfter("#", "")
        val params = fragment.split("&").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()

        val targetEp = params["ep"]?.toIntOrNull()
        val startEp = params["startEp"]?.toIntOrNull() ?: 1

        val res = try {
            app.get(topicUrl, headers = DEFAULT_HEADERS)
        } catch (_: Exception) {
            return false
        }

        if (res.code !in 200..299) return false
        val doc = res.document
        val commentWrap = doc.selectFirst("div[data-role=commentContent], div.cPost_contentWrap") ?: doc

        var foundLinks = false
        val seenUrls = mutableSetOf<String>()

        // 1. Process all Magnet links
        val allMagnets = commentWrap.select("a[href^=magnet:]").mapNotNull { a ->
            val url = a.attr("href").trim()
            if (url.isBlank()) null else {
                val dn = extractQueryParam(url, "dn") ?: ""
                Triple(a, url, dn)
            }
        }

        val epPattern = if (targetEp != null) Regex("""(?i)\bEP\s*0*${targetEp}\b""") else null
        val individualMagnets = if (epPattern != null) {
            allMagnets.filter { (_, _, dn) ->
                !dn.contains(Regex("""(?i)\bEP\s*\(?\d+\s*[-–]\s*\d+\)?""")) && epPattern.containsMatchIn(dn)
            }
        } else emptyList()

        val magnetsToProcess = if (individualMagnets.isNotEmpty()) {
            individualMagnets
        } else {
            allMagnets
        }

        val isBatchEpisode = targetEp != null && individualMagnets.isEmpty()
        val fileIndex = if (targetEp != null) maxOf(0, targetEp - startEp) else 0

        for ((_, magnetUrl, _) in magnetsToProcess) {
            if (!seenUrls.add(magnetUrl)) continue

            val (linkName, quality) = parseMagnetMetadata(magnetUrl)

            if (isBatchEpisode) {
                val indexedMagnetUrl = if (magnetUrl.contains("index=")) magnetUrl else "$magnetUrl&index=$fileIndex"
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$linkName [Ep $targetEp]",
                        url = indexedMagnetUrl,
                        type = ExtractorLinkType.MAGNET
                    ) {
                        this.referer = topicUrl
                        this.quality = quality
                    }
                )

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$linkName (Full Pack)",
                        url = magnetUrl,
                        type = ExtractorLinkType.MAGNET
                    ) {
                        this.referer = topicUrl
                        this.quality = quality
                    }
                )
            } else {
                callback(
                    newExtractorLink(
                        source = name,
                        name = linkName,
                        url = magnetUrl,
                        type = ExtractorLinkType.MAGNET
                    ) {
                        this.referer = topicUrl
                        this.quality = quality
                    }
                )
            }
            foundLinks = true
        }

        // 2. Process all Torrent attachment links
        val torrentLinks = commentWrap.select("a[href*=attachment.php], a[href*=.torrent]")
        for (a in torrentLinks) {
            val rawHref = a.attr("href").trim()
            if (rawHref.isBlank()) continue
            val torrentUrl = fixUrl(rawHref)
            if (!seenUrls.add(torrentUrl)) continue

            val torrentText = a.text().trim()
            val (linkName, quality) = parseTorrentMetadata(torrentText, torrentUrl)

            if (isBatchEpisode) {
                val indexedTorrentUrl = if (torrentUrl.contains("index=")) torrentUrl else "$torrentUrl#index=$fileIndex"
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$linkName [Ep $targetEp]",
                        url = indexedTorrentUrl,
                        type = ExtractorLinkType.TORRENT
                    ) {
                        this.referer = topicUrl
                        this.quality = quality
                    }
                )
            } else {
                callback(
                    newExtractorLink(
                        source = name,
                        name = linkName,
                        url = torrentUrl,
                        type = ExtractorLinkType.TORRENT
                    ) {
                        this.referer = topicUrl
                        this.quality = quality
                    }
                )
            }
            foundLinks = true
        }

        // 3. Process Direct Links (Cyberloom / MessyCloud / JuicyBits direct video streaming)
        val allATags = commentWrap.select("a")
        val directElements = allATags.filter { a ->
            val href = a.attr("href").trim()
            val text = a.text().trim().uppercase()
            (text.contains("DIRECT") || href.contains("cyberloom")) &&
                    !href.startsWith("magnet:") &&
                    !href.contains("attachment.php") &&
                    !href.endsWith(".torrent")
        }

        val directCandidates = directElements.mapNotNull { d ->
            val rawHref = d.attr("href").trim()
            if (rawHref.isBlank()) return@mapNotNull null
            val directUrl = fixUrl(rawHref)

            // Find preceding metadata (torrent attachment or magnet) to determine file name & resolution
            val dIdx = allATags.indexOf(d)
            var nameCandidate = ""
            for (i in (dIdx - 1) downTo 0) {
                val prev = allATags[i]
                val prevHref = prev.attr("href").trim()
                val prevText = prev.text().trim()
                if (prevHref.contains("attachment.php") || prevHref.endsWith(".torrent")) {
                    nameCandidate = prevText.ifBlank { prevHref.substringAfterLast("/") }
                    break
                } else if (prevHref.startsWith("magnet:")) {
                    val dn = extractQueryParam(prevHref, "dn")
                    if (!dn.isNullOrBlank()) {
                        nameCandidate = try {
                            URLDecoder.decode(dn, "UTF-8")
                        } catch (_: Exception) {
                            dn
                        }
                        break
                    }
                }
            }

            // If in a TV series, filter by target episode if individual episode releases exist
            if (epPattern != null && nameCandidate.isNotBlank()) {
                val isRange = nameCandidate.contains(Regex("""(?i)\bEP\s*\(?\d+\s*[-–]\s*\d+\)?"""))
                if (!isRange && !epPattern.containsMatchIn(nameCandidate)) {
                    return@mapNotNull null
                }
            }

            directUrl to nameCandidate
        }

        if (directCandidates.isNotEmpty()) {
            directCandidates.amap { (directUrl, nameCandidate) ->
                if (!seenUrls.add(directUrl)) return@amap

                val resolvedUrl = resolveDirectLink(directUrl)
                if (!resolvedUrl.isNullOrBlank()) {
                    val (linkName, quality) = formatLinkLabel(nameCandidate, null, prefix = "[Direct]")
                    val displayName = if (isBatchEpisode) "$linkName [Ep $targetEp]" else linkName
                    callback(
                        newExtractorLink(
                            source = name,
                            name = displayName,
                            url = resolvedUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = ""
                            this.quality = quality
                        }
                    )
                    foundLinks = true
                } else {
                    if (loadExtractor(directUrl, topicUrl, subtitleCallback, callback)) {
                        foundLinks = true
                    }
                }
            }
        }

        // 4. Process any other stream/online embed links (e.g., doodstream, streamtape, mixdrop)
        val onlineLinks = commentWrap.select("a[href]").filter { a ->
            val href = a.attr("href").trim()
            val text = a.text().trim().uppercase()
            (text.contains("STREAM") || text.contains("ONLINE") || text.contains("WATCH") || href.contains("/d/")) &&
                    !href.startsWith("magnet:") &&
                    !href.contains("attachment.php") &&
                    !href.endsWith(".torrent") &&
                    !href.contains("cyberloom") &&
                    !seenUrls.contains(href)
        }
        for (a in onlineLinks) {
            val href = fixUrl(a.attr("href").trim())
            if (seenUrls.add(href)) {
                if (loadExtractor(href, topicUrl, subtitleCallback, callback)) {
                    foundLinks = true
                }
            }
        }

        return foundLinks
    }

    private suspend fun resolveDirectLink(directUrl: String): String? {
        return try {
            val r1 = app.get(directUrl, headers = DEFAULT_HEADERS)
            val stackmintHtml = if (r1.text.contains("id=\"cta\"") || r1.text.contains("out?t=")) {
                r1.text
            } else {
                val loc = r1.headers["Location"]
                if (!loc.isNullOrBlank()) {
                    app.get(fixUrl(loc), headers = DEFAULT_HEADERS).text
                } else {
                    r1.text
                }
            }
            val stackmintUrl = r1.url

            val ctaUrl = r1.document.selectFirst("#cta, a.btn[href*=out?t=]")?.attr("href")
                ?: Regex("""(?i)href=["'](https?://[^"']+/out\?t=[^"']+)["']""").find(stackmintHtml)?.groupValues?.get(1)
                ?: return null

            val headersWithRef = DEFAULT_HEADERS.toMutableMap().apply {
                this["Referer"] = stackmintUrl
            }
            val r2 = app.get(ctaUrl, headers = headersWithRef)
            val messyHtml = if (r2.text.contains("download-btn") || r2.text.contains("/files/")) {
                r2.text
            } else {
                val loc2 = r2.headers["Location"]
                if (!loc2.isNullOrBlank()) {
                    app.get(fixUrl(loc2), headers = DEFAULT_HEADERS).text
                } else {
                    r2.text
                }
            }

            val doc2 = Jsoup.parse(messyHtml)
            val dlBtn = doc2.selectFirst("a.download-btn, a[href*='/files/'], a[href*='cdn.']")?.attr("href")
            if (!dlBtn.isNullOrBlank()) return fixUrl(dlBtn)

            val regexMatch = Regex("""(?i)https?://[^\s"'<>]+\.(?:mkv|mp4)[^\s"'<>]*""").find(messyHtml)
            regexMatch?.value?.let { fixUrl(it) }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun parseMagnetMetadata(magnetUrl: String): Pair<String, Int> {
        val dn = extractQueryParam(magnetUrl, "dn") ?: ""
        val xl = extractQueryParam(magnetUrl, "xl")?.toLongOrNull()

        val decodedDn = try {
            URLDecoder.decode(dn, "UTF-8")
        } catch (_: Exception) {
            dn
        }

        return formatLinkLabel(decodedDn, xl)
    }

    private fun parseTorrentMetadata(torrentText: String, torrentUrl: String): Pair<String, Int> {
        val nameCandidate = torrentText.ifBlank { torrentUrl.substringAfterLast("/") }
        return formatLinkLabel(nameCandidate, null, prefix = "[Torrent]")
    }

    private fun formatLinkLabel(rawName: String, sizeBytes: Long?, prefix: String? = null): Pair<String, Int> {
        val nameUpper = rawName.uppercase()

        val (qualityLabel, quality) = when {
            nameUpper.contains("2160P") || nameUpper.contains("4K") -> "4K" to Qualities.P2160.value
            nameUpper.contains("1080P") -> "1080p" to Qualities.P1080.value
            nameUpper.contains("720P") -> "720p" to Qualities.P720.value
            nameUpper.contains("480P") -> "480p" to Qualities.P480.value
            else -> "HD" to Qualities.Unknown.value
        }

        val codecs = mutableListOf<String>()
        if (nameUpper.contains("HEVC") || nameUpper.contains("X265")) codecs.add("HEVC")
        else if (nameUpper.contains("AVC") || nameUpper.contains("X264")) codecs.add("x264")

        if (nameUpper.contains("DD+5.1") || nameUpper.contains("DDP5.1")) codecs.add("DD+ 5.1")
        else if (nameUpper.contains("DD5.1") || nameUpper.contains("DD 5.1")) codecs.add("DD 5.1")
        else if (nameUpper.contains("AAC")) codecs.add("AAC")

        val sizeStr = if (sizeBytes != null && sizeBytes > 0) {
            val mb = sizeBytes / (1024.0 * 1024.0)
            if (mb >= 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.0f MB", mb)
            }
        } else {
            val sizeMatch = Regex("""(?i)\b(\d+(?:\.\d+)?\s*(?:GB|MB))\b""").find(rawName)
            sizeMatch?.groupValues?.get(1)
        }

        val labelParts = mutableListOf<String>()
        labelParts.add(qualityLabel)
        labelParts.addAll(codecs)
        sizeStr?.let { labelParts.add(it) }

        val prefixStr = if (!prefix.isNullOrBlank()) "$prefix " else ""
        val finalLabel = "[1TamilMV] $prefixStr" + labelParts.joinToString(" • ")
        return finalLabel to quality
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val query = url.substringAfter("?", "")
        if (query.isBlank()) return null
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0].equals(key, ignoreCase = true)) {
                return parts[1]
            }
        }
        return null
    }
}
