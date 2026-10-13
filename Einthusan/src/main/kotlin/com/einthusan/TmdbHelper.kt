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

    var posterWorkerUrl: String = "https://posters.devs-10f.workers.dev"

    fun getPosterWithLanguage(posterUrl: String?, language: String?): String? {
        val langName = language?.trim()?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() } ?: "Tamil"
        val targetWorkerUrl = if (posterUrl.isNullOrBlank()) {
            val encodedLang = URLEncoder.encode(langName, "UTF-8")
            "$posterWorkerUrl/?text=$encodedLang&w=500&h=750&bg=0f172a&color=fbbf24&size=36&format=webp"
        } else {
            val cleanUrl = if (posterUrl.startsWith("http")) {
                posterUrl
            } else {
                "https://image.tmdb.org/t/p/w500/${posterUrl.removePrefix("/")}"
            }
            val encodedTmdb = URLEncoder.encode(cleanUrl, "UTF-8")
            val encodedLang = URLEncoder.encode(langName, "UTF-8")
            "$posterWorkerUrl/?tmdb=$encodedTmdb&lang=$encodedLang&format=webp"
        }

        return try {
            "https://wsrv.nl/?url=${URLEncoder.encode(targetWorkerUrl, "UTF-8")}&output=webp"
        } catch (_: Exception) {
            targetWorkerUrl
        }
    }

    suspend fun getMovieMetadata(
        title: String,
        year: Int? = null,
        preferredLang: String = "en"
    ): TmdbMovieDetail? {
        val clean = cleanMovieTitle(title)
        if (clean.isBlank()) return null

        val cacheKey = "$clean:$year".lowercase()
        if (metadataCache.containsKey(cacheKey)) {
            return metadataCache[cacheKey]
        }

        val detail = fetchMetadata(clean, year)
        metadataCache[cacheKey] = detail
        return detail
    }

    private suspend fun fetchMetadata(cleanTitle: String, year: Int?): TmdbMovieDetail? {
        val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")

        // 1. Search with year if available
        var searchUrl = "$TMDB_BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedTitle"
        if (year != null && year > 1900) {
            searchUrl += "&year=$year"
        }

        var results = searchMovies(searchUrl)

        // 2. Fallback: Search without year constraint if no match found
        if (results.isEmpty() && year != null) {
            val fallbackUrl = "$TMDB_BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=$encodedTitle"
            results = searchMovies(fallbackUrl)
        }

        val matchedMovie = results.firstOrNull() ?: return null

        // 3. Fetch comprehensive details, logos, posters, backdrops, cast, trailers, recommendations
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
        return recList.take(15).mapNotNull { rec ->
            val title = rec.title ?: return@mapNotNull null
            val poster = rec.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            val searchUrl = "${api.mainUrl}/movie/results/?query=${URLEncoder.encode(title, "UTF-8")}"
            api.newMovieSearchResponse(title, searchUrl, TvType.Movie) {
                this.posterUrl = poster
                this.year = rec.releaseDate?.take(4)?.toIntOrNull()
            }
        }
    }
}
