package com.tamilmv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object TmdbHelper {

    private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "application/json"
    )

    private val metadataCache = ConcurrentHashMap<String, TmdbMediaDetail?>()

    fun cleanTitle(raw: String): String {
        return raw
            // Remove leading release tags like [PLF], [ALAC], etc.
            .replace(Regex("""^\[[^\]]+\]\s*"""), "")
            // Remove year in parentheses or standalone
            .replace(Regex("""\((?:19\d{2}|20\d{2})\)"""), " ")
            .replace(Regex("""\b(19\d{2}|20\d{2})\b"""), " ")
            // Remove Season / Episode markers
            .replace(Regex("""(?i)\bS\d{1,3}(?:\s*EP\s*\(?[\d\-]+\)?)?\b"""), " ")
            .replace(Regex("""(?i)\bEP\s*\(?[\d\-]+\)?\b"""), " ")
            .replace(Regex("""(?i)\bDAY\s*\d+\b"""), " ")
            // Remove languages
            .replace(Regex("""(?i)\b(?:Tamil|Telugu|Hindi|Malayalam|Kannada|English|Eng)\b"""), " ")
            // Remove quality / release tokens
            .replace(Regex("""(?i)\b(?:TRUE|WEB-DL|WEBDL|HDRip|PreDVD|DVDScr|CAM|TC|BluRay|HDTV|SDTV|HQ|Clean\s*Audio|UNTOUCHED|Rips?)\b"""), " ")
            // Remove video codecs
            .replace(Regex("""(?i)\b(?:4K|2160p|1080p|720p|480p|AVC|HEVC|x264|x265|SDR|HDR|DDP?5\.1|DD\+?5\.1|DD\+?2\.0|AAC\s*2\.0|AAC|ESub|Soft\s*ESub|HC\s*ESub)\b"""), " ")
            // Remove file size notes
            .replace(Regex("""(?i)\b\d+(?:\.\d+)?\s*(?:GB|MB)\b"""), " ")
            // Clean punctuation
            .replace(Regex("""[\[\]\(\)]"""), " ")
            .replace(Regex("""[\-_|:–—+]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .ifBlank { raw.trim() }
    }

    fun toIso6391(language: String?): String {
        return when (language?.lowercase()?.trim()) {
            "tamil", "tam" -> "ta"
            "telugu", "tel" -> "te"
            "hindi", "hin" -> "hi"
            "malayalam", "mal" -> "ml"
            "kannada", "kan" -> "kn"
            "english", "eng" -> "en"
            else -> "en"
        }
    }

    var posterWorkerUrl: String = "https://posters.devs-10f.workers.dev"

    fun getPosterWithLanguage(posterUrl: String?, language: String?): String? {
        val langName = language?.trim()?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() } ?: "Tamil"
        val targetWorkerUrl = if (posterUrl.isNullOrBlank()) {
            val encodedLang = URLEncoder.encode(langName, "UTF-8")
            "$posterWorkerUrl/?text=$encodedLang&w=500&h=750&v=2"
        } else {
            val cleanUrl = if (posterUrl.startsWith("http")) {
                posterUrl
            } else {
                "https://image.tmdb.org/t/p/w500/${posterUrl.removePrefix("/")}"
            }
            val encodedTmdb = URLEncoder.encode(cleanUrl, "UTF-8")
            val encodedLang = URLEncoder.encode(langName, "UTF-8")
            "$posterWorkerUrl/?tmdb=$encodedTmdb&lang=$encodedLang&v=2"
        }

        return try {
            "https://wsrv.nl/?url=${URLEncoder.encode(targetWorkerUrl, "UTF-8")}&output=webp"
        } catch (_: Exception) {
            targetWorkerUrl
        }
    }

    private val INDIAN_LANGS = setOf("ta", "te", "hi", "ml", "kn")

    private fun normalizeTitle(s: String): String {
        return s.lowercase().replace(Regex("""[^a-z0-9]"""), "")
    }

    private fun findBestMatch(
        cleanTitle: String,
        results: List<TmdbSearchResultItem>,
        expectedYear: Int?,
        expectedLang: String
    ): TmdbSearchResultItem? {
        val cleanNorm = normalizeTitle(cleanTitle)
        if (cleanNorm.isBlank()) return null

        var bestScore = -1
        var bestItem: TmdbSearchResultItem? = null

        for (item in results) {
            val tNorm = normalizeTitle(item.getResolvedTitle())
            val otNorm = normalizeTitle(item.originalTitle ?: item.originalName ?: "")

            val isExact = tNorm == cleanNorm || otNorm == cleanNorm
            val isStarts = tNorm.startsWith(cleanNorm) || otNorm.startsWith(cleanNorm)
            val isContains = (cleanNorm.length >= 4 && (tNorm.contains(cleanNorm) || otNorm.contains(cleanNorm)))

            if (!isExact && !isStarts && !isContains) continue

            var score = 0
            if (isExact) score += 100
            else if (isStarts) score += 50
            else if (isContains) score += 20

            val itemYear = item.getResolvedYear()
            if (expectedYear != null && itemYear != null) {
                val diff = kotlin.math.abs(expectedYear - itemYear)
                when (diff) {
                    0 -> score += 60
                    1 -> score += 25
                    else -> score -= diff * 15
                }
            }

            val itemLang = item.originalLanguage?.lowercase()
            if (itemLang != null) {
                if (itemLang == expectedLang) {
                    score += 40
                } else if (INDIAN_LANGS.contains(itemLang) && INDIAN_LANGS.contains(expectedLang)) {
                    score += 15
                }
            }

            if (item.posterPath != null) score += 10

            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }

        return if (bestScore >= 30) bestItem else null
    }

    suspend fun getMetadata(
        title: String,
        year: Int?,
        langIso: String = "ta",
        isTv: Boolean = false
    ): TmdbMediaDetail? {
        val clean = cleanTitle(title)
        if (clean.isBlank()) return null

        val cacheKey = "${if (isTv) "tv" else "movie"}_${clean.lowercase()}_${year ?: 0}_$langIso"
        if (metadataCache.containsKey(cacheKey)) {
            return metadataCache[cacheKey]
        }

        val searchType = if (isTv) "tv" else "movie"
        val encodedQuery = URLEncoder.encode(clean, "UTF-8")
        val searchUrl = "$TMDB_BASE_URL/search/$searchType?query=$encodedQuery&api_key=$TMDB_API_KEY"

        val searchRes = try {
            app.get(searchUrl, headers = defaultHeaders)
        } catch (_: Exception) {
            null
        }

        if (searchRes == null || searchRes.code !in 200..299) {
            metadataCache[cacheKey] = null
            return null
        }

        val parsedSearch = try {
            parseJson<TmdbSearchResponse>(searchRes.text)
        } catch (_: Exception) {
            null
        }

        val results = parsedSearch?.results
        if (results.isNullOrEmpty()) {
            metadataCache[cacheKey] = null
            return null
        }

        val best = findBestMatch(clean, results, year, langIso) ?: results.firstOrNull()
        val tmdbId = best?.id
        if (tmdbId == null) {
            metadataCache[cacheKey] = null
            return null
        }

        val appendFields = if (isTv) {
            "images,credits,videos,recommendations,external_ids"
        } else {
            "images,credits,videos,recommendations"
        }
        val detailUrl = "$TMDB_BASE_URL/$searchType/$tmdbId?append_to_response=$appendFields&api_key=$TMDB_API_KEY"

        val detailRes = try {
            app.get(detailUrl, headers = defaultHeaders)
        } catch (_: Exception) {
            null
        }

        if (detailRes == null || detailRes.code !in 200..299) {
            metadataCache[cacheKey] = null
            return null
        }

        val detail = try {
            parseJson<TmdbMediaDetail>(detailRes.text)
        } catch (_: Exception) {
            null
        }

        metadataCache[cacheKey] = detail
        return detail
    }

    fun toActors(detail: TmdbMediaDetail): List<ActorData> {
        val castList = detail.credits?.cast ?: return emptyList()
        return castList.take(15).mapNotNull { member ->
            val actorName = member.name?.trim() ?: return@mapNotNull null
            if (actorName.isBlank()) return@mapNotNull null

            val actorImage = member.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
            ActorData(
                Actor(actorName, actorImage),
                roleString = member.character?.trim()?.ifBlank { null }
            )
        }
    }

    fun toTrailers(detail: TmdbMediaDetail): List<TrailerData> {
        val videoList = detail.videos?.results ?: return emptyList()
        return videoList.filter {
            it.site.equals("YouTube", ignoreCase = true) && !it.key.isNullOrBlank()
        }.map { video ->
            TrailerData(
                extractorUrl = "https://www.youtube.com/watch?v=${video.key}",
                referer = "https://www.youtube.com/",
                raw = false
            )
        }
    }

    fun toRecommendations(detail: TmdbMediaDetail, api: MainAPI): List<SearchResponse> {
        val recs = detail.recommendations?.results ?: return emptyList()
        return recs.take(12).mapNotNull { rec ->
            val title = rec.title ?: rec.name ?: return@mapNotNull null
            val poster = rec.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            val year = (rec.releaseDate ?: rec.firstAirDate)?.take(4)?.toIntOrNull()
            api.newMovieSearchResponse(title, "${api.mainUrl}/index.php?/search/&q=${URLEncoder.encode(title, "UTF-8")}&type=forums_topic", TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }
}
