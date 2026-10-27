package com.specialitystream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.net.URLEncoder

class SpecialityStreamProvider : MainAPI() {
    override var name = "SpecialityStream"
    override var mainUrl = "https://api.themoviedb.org/3"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true

    companion object {
        private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    }

    private val tmdbHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "application/json"
    )

    override val mainPage = mainPageOf(
        MainPageData(name = "Trending Movies Today", data = "trending/movie/day", horizontalImages = false),
        MainPageData(name = "Trending TV Series Today", data = "trending/tv/day", horizontalImages = false),
        MainPageData(name = "Popular Movies", data = "movie/popular", horizontalImages = false),
        MainPageData(name = "Popular TV Series", data = "tv/popular", horizontalImages = false),
        MainPageData(name = "Top Rated Movies", data = "movie/top_rated", horizontalImages = false),
        MainPageData(name = "Top Rated TV Series", data = "tv/top_rated", horizontalImages = false),
        MainPageData(name = "Now Playing in Theatres", data = "movie/now_playing", horizontalImages = false),
        MainPageData(name = "Trending Indian Cinema", data = "discover/movie?with_origin_country=IN&sort_by=popularity.desc", horizontalImages = false),
        MainPageData(name = "Tamil Cinema Hits", data = "discover/movie?with_original_language=ta&sort_by=popularity.desc", horizontalImages = false),
        MainPageData(name = "Popular Anime", data = "discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc", horizontalImages = false)
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sep = if (request.data.contains("?")) "&" else "?"
        val url = "$TMDB_BASE_URL/${request.data}${sep}page=$page&api_key=$TMDB_API_KEY"

        val response = try {
            app.get(url, headers = tmdbHeaders)
        } catch (_: Exception) {
            null
        }

        if (response == null || response.code !in 200..299) {
            return newHomePageResponse(
                listOf(HomePageList(request.name, emptyList(), isHorizontalImages = false)),
                hasNext = false
            )
        }

        val pageData = try {
            parseJson<TmdbPageResponse>(response.text)
        } catch (_: Exception) {
            null
        }

        val isTvSection = request.data.contains("/tv") ||
                request.name.contains("TV", ignoreCase = true) ||
                request.name.contains("Anime", ignoreCase = true)

        val items = pageData?.results?.mapNotNull { item ->
            toSearchResponse(item, isTvSection)
        } ?: emptyList()

        val totalPages = pageData?.totalPages ?: 1
        val hasNext = page < totalPages

        return newHomePageResponse(
            listOf(HomePageList(request.name, items, isHorizontalImages = false)),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$TMDB_BASE_URL/search/multi?query=$encoded&api_key=$TMDB_API_KEY"

        val response = try {
            app.get(url, headers = tmdbHeaders)
        } catch (_: Exception) {
            return emptyList()
        }

        if (response.code !in 200..299) return emptyList()

        val pageData = try {
            parseJson<TmdbPageResponse>(response.text)
        } catch (_: Exception) {
            null
        }

        return pageData?.results?.mapNotNull { item ->
            val isTv = item.mediaType == "tv"
            toSearchResponse(item, isTv)
        } ?: emptyList()
    }

    private fun toSearchResponse(item: TmdbPageResult, isTvDefault: Boolean): SearchResponse? {
        val id = item.id ?: return null
        val title = item.title?.ifBlank { null }
            ?: item.name?.ifBlank { null }
            ?: item.originalTitle?.ifBlank { null }
            ?: item.originalName?.ifBlank { null }
            ?: return null

        val isTv = item.mediaType == "tv" || (item.mediaType == null && isTvDefault)
        val isAnime = isTv && item.genreIds?.contains(16) == true
        val type = when {
            isAnime -> TvType.Anime
            isTv -> TvType.TvSeries
            else -> TvType.Movie
        }

        val poster = item.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val targetUrl = "$mainUrl/${if (isTv) "tv" else "movie"}/$id"

        return if (isTv) {
            newTvSeriesSearchResponse(title, targetUrl, type) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, targetUrl, type) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val isTv = url.contains("/tv/")
        val tmdbId = url.removeSuffix("/").substringAfterLast("/").toLongOrNull() ?: return null

        return if (!isTv) {
            loadMovie(tmdbId, url)
        } else {
            loadTv(tmdbId, url)
        }
    }

    private suspend fun loadMovie(tmdbId: Long, pageUrl: String): LoadResponse? {
        val url = "$TMDB_BASE_URL/movie/$tmdbId?append_to_response=credits,recommendations,videos,external_ids&api_key=$TMDB_API_KEY"
        val response = try {
            app.get(url, headers = tmdbHeaders)
        } catch (_: Exception) {
            return null
        }

        if (response.code !in 200..299) return null

        val detail = try {
            parseJson<TmdbMovieDetail>(response.text)
        } catch (_: Exception) {
            return null
        }

        val title = detail.title ?: detail.originalTitle ?: "Movie"
        val poster = detail.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val backdrop = detail.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" } ?: poster
        val plot = detail.overview
        val year = detail.releaseDate?.take(4)?.toIntOrNull()
        val runtime = detail.runtime
        val rating = detail.voteAverage?.times(1000)?.toInt()
        val tags = detail.genres?.mapNotNull { it.name } ?: emptyList()
        val imdbId = detail.externalIds?.imdbId ?: detail.imdbId

        val actors = detail.credits?.cast?.take(15)?.mapNotNull { cast ->
            cast.name?.let { actorName ->
                ActorData(
                    Actor(actorName, cast.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }),
                    roleString = cast.character
                )
            }
        } ?: emptyList()

        val recs = detail.recommendations?.results?.mapNotNull { toSearchResponse(it, false) } ?: emptyList()
        val trailers = detail.videos?.results?.filter {
            it.site.equals("YouTube", ignoreCase = true) && !it.key.isNullOrBlank()
        }?.map { video ->
            TrailerData(
                extractorUrl = "https://www.youtube.com/watch?v=${video.key}",
                referer = "https://www.youtube.com/",
                raw = false
            )
        } ?: emptyList()

        val passData = StreamPassData(
            isMovie = true,
            tmdbId = tmdbId,
            imdbId = imdbId,
            title = title,
            year = year
        )

        return newMovieLoadResponse(title, pageUrl, TvType.Movie, passData.toJson()) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.plot = plot
            this.year = year
            this.duration = runtime
            this.tags = tags
            this.actors = actors
            this.recommendations = recs
            this.trailers = trailers.toMutableList()
            detail.voteAverage?.let {
                if (it > 0.0) this.score = Score.from10(it)
            }
        }
    }

    private suspend fun loadTv(tmdbId: Long, pageUrl: String): LoadResponse? {
        val url = "$TMDB_BASE_URL/tv/$tmdbId?append_to_response=credits,recommendations,videos,external_ids&api_key=$TMDB_API_KEY"
        val response = try {
            app.get(url, headers = tmdbHeaders)
        } catch (_: Exception) {
            return null
        }

        if (response.code !in 200..299) return null

        val detail = try {
            parseJson<TmdbTvDetail>(response.text)
        } catch (_: Exception) {
            return null
        }

        val title = detail.name ?: detail.originalName ?: "TV Series"
        val poster = detail.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val backdrop = detail.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" } ?: poster
        val plot = detail.overview
        val year = detail.firstAirDate?.take(4)?.toIntOrNull()
        val tags = detail.genres?.mapNotNull { it.name } ?: emptyList()
        val imdbId = detail.externalIds?.imdbId

        val actors = detail.credits?.cast?.take(15)?.mapNotNull { cast ->
            cast.name?.let { actorName ->
                ActorData(
                    Actor(actorName, cast.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }),
                    roleString = cast.character
                )
            }
        } ?: emptyList()

        val recs = detail.recommendations?.results?.mapNotNull { toSearchResponse(it, true) } ?: emptyList()
        val trailers = detail.videos?.results?.filter {
            it.site.equals("YouTube", ignoreCase = true) && !it.key.isNullOrBlank()
        }?.map { video ->
            TrailerData(
                extractorUrl = "https://www.youtube.com/watch?v=${video.key}",
                referer = "https://www.youtube.com/",
                raw = false
            )
        } ?: emptyList()

        val episodes = mutableListOf<Episode>()
        val seasons = detail.seasons?.filter { (it.seasonNumber ?: -1) > 0 } ?: emptyList()

        for (season in seasons) {
            val sNum = season.seasonNumber ?: continue
            val seasonUrl = "$TMDB_BASE_URL/tv/$tmdbId/season/$sNum?api_key=$TMDB_API_KEY"
            val sRes = try {
                app.get(seasonUrl, headers = tmdbHeaders)
            } catch (_: Exception) {
                null
            }

            if (sRes != null && sRes.code in 200..299) {
                val sDetail = try {
                    parseJson<TmdbSeasonDetail>(sRes.text)
                } catch (_: Exception) {
                    null
                }

                sDetail?.episodes?.forEach { ep ->
                    val epNum = ep.episodeNumber ?: return@forEach
                    val epPassData = StreamPassData(
                        isMovie = false,
                        tmdbId = tmdbId,
                        imdbId = imdbId,
                        season = sNum,
                        episode = epNum,
                        title = title,
                        year = year
                    )

                    episodes.add(
                        newEpisode(epPassData.toJson()) {
                            this.name = ep.name
                            this.season = sNum
                            this.episode = epNum
                            this.posterUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            this.description = ep.overview
                        }
                    )
                }
            }
        }

        val isAnime = tags.contains("Animation")
        val tvType = if (isAnime) TvType.Anime else TvType.TvSeries

        return newTvSeriesLoadResponse(title, pageUrl, tvType, episodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.plot = plot
            this.year = year
            this.tags = tags
            this.actors = actors
            this.recommendations = recs
            this.trailers = trailers.toMutableList()
            detail.voteAverage?.let {
                if (it > 0.0) this.score = Score.from10(it)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val passData = try {
            parseJson<StreamPassData>(data)
        } catch (_: Exception) {
            val singleId = data.toLongOrNull() ?: return false
            StreamPassData(isMovie = true, tmdbId = singleId)
        }

        var foundLinks = false

        // 1. VidLink.pro (High-priority direct streams + multi-language subtitles)
        try {
            val vidlinkSuccess = VidLinkHelper.getStream(
                tmdbId = passData.tmdbId,
                season = passData.season,
                episode = passData.episode,
                callback = {
                    callback(it)
                    foundLinks = true
                },
                subtitleCallback = subtitleCallback
            )
            if (vidlinkSuccess) foundLinks = true
        } catch (_: Throwable) {
        }

        // 2. VidStuck.cc (Aggregated embed player with multiple servers & multi-language audio dubs)
        try {
            val vidstuckSuccess = VidStuckHelper.getStream(
                tmdbId = passData.tmdbId,
                imdbId = passData.imdbId,
                season = passData.season,
                episode = passData.episode,
                title = passData.title,
                year = passData.year,
                callback = {
                    callback(it)
                    foundLinks = true
                },
                subtitleCallback = subtitleCallback
            )
            if (vidstuckSuccess) foundLinks = true
        } catch (_: Throwable) {
        }

        // 3. Multi-Embed Providers (VidSrc, 2Embed, AutoEmbed, MultiEmbed, SmashyStream, MoviesAPI)
        try {
            EmbedExtractors.extractAll(
                tmdbId = passData.tmdbId,
                imdbId = passData.imdbId,
                season = passData.season,
                episode = passData.episode,
                callback = {
                    callback(it)
                    foundLinks = true
                },
                subtitleCallback = subtitleCallback
            )
        } catch (_: Throwable) {
        }

        return foundLinks
    }
}
