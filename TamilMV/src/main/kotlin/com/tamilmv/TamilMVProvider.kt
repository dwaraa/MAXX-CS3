package com.tamilmv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
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

    private fun detectLanguage(text: String, defaultCategory: String? = null): String {
        val lower = "$text ${defaultCategory ?: ""}".lowercase()
        return when {
            lower.contains("telugu") -> "Telugu"
            lower.contains("hindi") -> "Hindi"
            lower.contains("malayalam") -> "Malayalam"
            lower.contains("kannada") -> "Kannada"
            lower.contains("english") || lower.contains("hollywood") -> "English"
            else -> "Tamil"
        }
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

        val language = detectLanguage(rawTitle, defaultCategory)
        val isoLang = TmdbHelper.toIso6391(language)
        val isTv = isTvSeries(rawTitle, href)

        val tmdb = try {
            TmdbHelper.getMetadata(cleanTitle, year, isoLang, isTv)
        } catch (_: Exception) {
            null
        }

        val displayTitle = tmdb?.getResolvedTitle() ?: cleanTitle
        val resolvedYear = tmdb?.getYear() ?: year
        val posterCandidate = tmdb?.getPosterUrl(isoLang)
        val finalPoster = TmdbHelper.getPosterWithLanguage(posterCandidate, language)

        return if (isTv) {
            newTvSeriesSearchResponse(displayTitle, href, TvType.TvSeries) {
                this.posterUrl = finalPoster
                this.year = resolvedYear
            }
        } else {
            newMovieSearchResponse(displayTitle, href, TvType.Movie) {
                this.posterUrl = finalPoster
                this.year = resolvedYear
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url, headers = DEFAULT_HEADERS)
        updateDomain(res.url)
        val doc = res.document

        val pageTitle = doc.selectFirst("h1.ipsType_pageTitle, h1")?.text()?.trim() ?: doc.title()
        val (cleanTitle, parsedYear) = parseTitleAndYear(pageTitle)

        val language = detectLanguage(pageTitle)
        val isoLang = TmdbHelper.toIso6391(language)
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
        val finalTitle = if (baseTitle.contains(language, ignoreCase = true)) {
            baseTitle
        } else {
            "$baseTitle ($language)"
        }

        val posterCandidate = tmdb?.getPosterUrl(isoLang) ?: scrapedPoster
        val finalPoster = TmdbHelper.getPosterWithLanguage(posterCandidate, language)
        val finalBackdrop = tmdb?.getBackdropUrl() ?: scrapedPoster
        val finalLogo = tmdb?.getLogoUrl(isoLang)
        val basePlot = tmdb?.overview?.ifBlank { null }
        val finalYear = tmdb?.getYear() ?: parsedYear

        val finalPlot = buildString {
            append("Language: $language • Year: ${finalYear ?: "N/A"}")
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
            val episodes = parseEpisodes(pageTitle, url, finalPoster)
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

    private fun parseEpisodes(pageTitle: String, topicUrl: String, poster: String?): List<Episode> {
        val seasonMatch = Regex("""(?i)\bS(\d{1,2})\b""").find(pageTitle)
        val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

        // Check for range like EP (01-04) or EP(01-10)
        val rangeMatch = Regex("""(?i)\bEP\s*\(?(\d+)\s*[-–]\s*(\d+)\)?""").find(pageTitle)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: 1
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
            if (end >= start && end - start < 100) {
                return (start..end).map { epNum ->
                    newEpisode(topicUrl) {
                        this.name = "Episode $epNum"
                        this.season = seasonNum
                        this.episode = epNum
                        this.posterUrl = poster
                    }
                }
            }
        }

        // Check for single episode like EP05 or EP 38
        val singleMatch = Regex("""(?i)\bEP\s*\(?(\d+)\)?""").find(pageTitle)
        if (singleMatch != null) {
            val epNum = singleMatch.groupValues[1].toIntOrNull() ?: 1
            return listOf(
                newEpisode(topicUrl) {
                    this.name = "Episode $epNum"
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = poster
                }
            )
        }

        // Default single episode for the whole batch
        return listOf(
            newEpisode(topicUrl) {
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
        val magnetLinks = commentWrap.select("a[href^=magnet:]")
        for (a in magnetLinks) {
            val magnetUrl = a.attr("href").trim()
            if (magnetUrl.isBlank() || !seenUrls.add(magnetUrl)) continue

            val (linkName, quality) = parseMagnetMetadata(magnetUrl)
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
            foundLinks = true
        }

        return foundLinks
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
        prefix?.let { labelParts.add(it) }
        labelParts.add(qualityLabel)
        labelParts.addAll(codecs)
        sizeStr?.let { labelParts.add(it) }

        val finalLabel = "[1TamilMV] " + labelParts.joinToString(" • ")
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
