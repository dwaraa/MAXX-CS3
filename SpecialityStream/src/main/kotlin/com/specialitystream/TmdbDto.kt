package com.specialitystream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbPageResult(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("genre_ids") val genreIds: List<Int>? = null,
    @JsonProperty("original_language") val originalLanguage: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbPageResponse(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("results") val results: List<TmdbPageResult>? = null,
    @JsonProperty("total_pages") val totalPages: Int? = null,
    @JsonProperty("total_results") val totalResults: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCast(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCredits(
    @JsonProperty("cast") val cast: List<TmdbCast>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideo(
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("site") val site: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideos(
    @JsonProperty("results") val results: List<TmdbVideo>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbExternalIds(
    @JsonProperty("imdb_id") val imdbId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSeason(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("episode_count") val episodeCount: Int? = null,
    @JsonProperty("poster_path") val posterPath: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbMovieDetail(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("recommendations") val recommendations: TmdbPageResponse? = null,
    @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("original_language") val originalLanguage: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbTvDetail(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("recommendations") val recommendations: TmdbPageResponse? = null,
    @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null,
    @JsonProperty("seasons") val seasons: List<TmdbSeason>? = null,
    @JsonProperty("original_language") val originalLanguage: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbEpisode(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSeasonDetail(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamPassData(
    @JsonProperty("isMovie") val isMovie: Boolean,
    @JsonProperty("tmdbId") val tmdbId: Long,
    @JsonProperty("imdbId") val imdbId: String? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("year") val year: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidlinkQuality(
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("codecName") val codecName: String? = null,
    @JsonProperty("size") val size: String? = null,
    @JsonProperty("headers") val headers: Map<String, String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidlinkCaption(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidlinkStream(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("playlist") val playlist: String? = null,
    @JsonProperty("qualities") val qualities: Map<String, VidlinkQuality>? = null,
    @JsonProperty("captions") val captions: List<VidlinkCaption>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidlinkResponse(
    @JsonProperty("sourceId") val sourceId: String? = null,
    @JsonProperty("stream") val stream: VidlinkStream? = null
)
