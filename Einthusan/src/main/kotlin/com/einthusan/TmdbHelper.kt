package com.einthusan

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

    private val metadataCache = ConcurrentHashMap<String, TmdbMovieDetail?>()

    fun cleanMovieTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\b(?:Tamil|Telugu|Hindi|Malayalam|Kannada|Bengali|Marathi|Punjabi)\b"""), " ")
            .replace(Regex("""(?i)\b(?:Dubbed|Must\s*Watch|Staff\s*Pick|Regional\s*Hit)\b"""), " ")
            .replace(Regex("""\b(19\d{2}|20\d{2})\b"""), " ")
            .replace(Regex("""[\[\]\(\)]"""), " ")
            .replace(Regex("""[\-_|:–—]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .ifBlank { raw.trim() }
    }

    fun toIso6391(language: String?): String {
        return when (language?.lowercase()?.trim()) {
            "tamil" -> "ta"
            "telugu" -> "te"
            "hindi" -> "hi"
            "malayalam" -> "ml"
            "kannada" -> "kn"
            "bengali" -> "bn"
            "marathi" -> "mr"
            "punjabi" -> "pa"
            else -> "en"
        }
    }

    var posterWorkerUrl: String = "https://bp-cs3.devs-10f.workers.dev"

    fun getPosterWithLanguage(
        tmdbId: Int?,
        fallbackPosterUrl: String?,
        language: String?
    ): String? {
        val langName = language?.trim()?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() } ?: "Tamil"
        val isoLang = toIso6391(langName)
        val encodedLang = URLEncoder.encode(langName, "UTF-8")

        if (tmdbId != null && tmdbId > 0) {
            return "$posterWorkerUrl/?tmdb=$tmdbId&type=movie&logo=en&text=auto&lang=$encodedLang&top=auto&format=webp"
        }

        if (!fallbackPosterUrl.isNullOrBlank()) {
            return fallbackPosterUrl
        }

        return "$posterWorkerUrl/?text=$encodedLang&format=webp"
    }

    fun getPosterWithLanguage(posterUrl: String?, language: String?): String? {
        val tmdbId = posterUrl?.toIntOrNull()
        return getPosterWithLanguage(tmdbId, posterUrl, language)
    }

    private val INDIAN_LANGS = setOf("ta", "te", "hi", "ml", "kn", "bn", "mr", "pa")

    private fun normalizeTitle(s: String): String {
        return s.lowercase().replace(Regex("""[^a-z0-9]"""), "")
    }

    private fun findBestMatch(
        cleanTitle: String,
        results: List<TmdbSearchMovieItem>,
        expectedYear: Int?,
        expectedLang: String
    ): TmdbSearchMovieItem? {
        val cleanNorm = normalizeTitle(cleanTitle)
        if (cleanNorm.isBlank()) return null

        var bestScore = -1
        var bestItem: TmdbSearchMovieItem? = null

        for (item in results) {
            val tNorm = normalizeTitle(item.title ?: "")
            val otNorm = normalizeTitle(item.originalTitle ?: "")

            val isExact = tNorm == cleanNorm || otNorm == cleanNorm
            val isStarts = tNorm.startsWith(cleanNorm) || otNorm.startsWith(cleanNorm)
            val isContains = (cleanNorm.length >= 4 && (tNorm.contains(cleanNorm) || otNorm.contains(cleanNorm)))

            if (!isExact && !isStarts && !isContains) continue

            var score = 0
            if (isExact) score += 100
            else if (isStarts) score += 50
            else if (isContains) score += 25

            val rLang = item.originalLanguage?.lowercase()?.trim()
            if (rLang == expectedLang) {
                score += 50
            } else if (rLang != null && INDIAN_LANGS.contains(rLang)) {
                score += 30
            }

            val rYear = item.releaseDate?.take(4)?.toIntOrNull()
            if (expectedYear != null && rYear != null) {
                val diff = kotlin.math.abs(expectedYear - rYear)
                if (diff == 0) score += 50
                else if (diff <= 1) score += 25
                else if (diff > 3) score -= 60
            }

            val pop = item.voteAverage ?: 0.0
            score += pop.toInt().coerceIn(0, 10)

            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }

        return if (bestScore >= 50) bestItem else null
    }

    suspend fun getMovieMetadata(
        title: String,
        year: Int? = null,
        preferredLang: String = "en"
    ): TmdbMovieDetail? {
        val clean = cleanMovieTitle(title)
        if (clean.isBlank()) return null

        val cacheKey = "$clean:$year:$preferredLang".lowercase()
        if (metadataCache.containsKey(cacheKey)) {
            return metadataCache[cacheKey]
        }

        val detail = fetchMetadata(clean, year, preferredLang)
        metadataCache[cacheKey] = detail
        return detail
    }

    private suspend fun fetchMetadata(cleanTitle: String, year: Int?, preferredLang: String): TmdbMovieDetail? {
        val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")

        val searchUrls = mutableListOf<String>()
        if (year != null && year > 1900) {
            searchUrls.add("$TMDB_BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedTitle&year=$year")
        }
        searchUrls.add("$TMDB_BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedTitle")

        var matchedMovie: TmdbSearchMovieItem? = null
        for (url in searchUrls) {
            val results = searchMovies(url)
            matchedMovie = findBestMatch(cleanTitle, results, year, preferredLang)
            if (matchedMovie != null) break
        }

        if (matchedMovie == null) return null

        return fetchMovieDetails(matchedMovie.id)
    }

    private suspend fun searchMovies(url: String): List<TmdbSearchMovieItem> {
        return try {
            val resp = app.get(url, headers = defaultHeaders)
            if (resp.code in 200..299) {
                val data = parseJson<TmdbSearchResponse>(resp.text)
                data.results ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchMovieDetails(movieId: Int): TmdbMovieDetail? {
        val detailUrl = "$TMDB_BASE_URL/movie/$movieId?api_key=$TMDB_API_KEY" +
                "&append_to_response=images,videos,credits,recommendations" +
                "&include_image_language=en,ta,te,hi,ml,kn,null"

        return try {
            val resp = app.get(detailUrl, headers = defaultHeaders)
            if (resp.code in 200..299) {
                parseJson<TmdbMovieDetail>(resp.text)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun toActors(detail: TmdbMovieDetail): List<ActorData> {
        val castList = detail.credits?.cast ?: return emptyList()
        return castList.take(20).mapNotNull { member ->
            val name = member.name ?: return@mapNotNull null
            val photoUrl = member.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
            ActorData(
                actor = Actor(name, photoUrl),
                roleString = member.character ?: "Cast"
            )
        }
    }

    fun toTrailers(detail: TmdbMovieDetail): List<TrailerData> {
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

    fun toRecommendations(
        detail: TmdbMovieDetail,
        api: MainAPI
    ): List<SearchResponse> {
        val recList = detail.recommendations?.results ?: return emptyList()
        val lang = detail.originalLanguage ?: "Tamil"
        return recList.take(15).mapNotNull { rec ->
            val title = rec.title ?: return@mapNotNull null
            val fallbackPoster = rec.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            val poster = getPosterWithLanguage(rec.id, fallbackPoster, lang)
            val searchUrl = "${api.mainUrl}/movie/results/?query=${URLEncoder.encode(title, "UTF-8")}"
            api.newMovieSearchResponse(title, searchUrl, TvType.Movie) {
                this.posterUrl = poster
                this.year = rec.releaseDate?.take(4)?.toIntOrNull()
            }
        }
    }
}
