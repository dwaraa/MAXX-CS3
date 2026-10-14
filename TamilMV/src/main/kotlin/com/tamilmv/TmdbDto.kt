package com.tamilmv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSearchResponse(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("results") val results: List<TmdbSearchResultItem>? = null,
    @JsonProperty("total_results") val totalResults: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSearchResultItem(
    @JsonProperty("id") val id: Int,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("original_language") val originalLanguage: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("media_type") val mediaType: String? = null
) {
    fun getResolvedTitle(): String {
        return title ?: name ?: originalTitle ?: originalName ?: ""
    }

    fun getResolvedYear(): Int? {
        val date = releaseDate ?: firstAirDate
        return date?.take(4)?.toIntOrNull()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbMediaDetail(
    @JsonProperty("id") val id: Int,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("tagline") val tagline: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("episode_run_time") val episodeRunTime: List<Int>? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("vote_count") val voteCount: Int? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("images") val images: TmdbImages? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("recommendations") val recommendations: TmdbRecommendations? = null
) {
    fun getResolvedTitle(): String {
        return title ?: name ?: originalTitle ?: originalName ?: ""
    }

    fun getResolvedImdbId(): String? {
        return imdbId ?: externalIds?.imdbId
    }

    fun getLogoUrl(preferredLang: String = "en"): String? {
        val logosList = images?.logos ?: return null
        if (logosList.isEmpty()) return null

        val logo = logosList.firstOrNull { it.iso6391.equals(preferredLang, ignoreCase = true) }
            ?: logosList.firstOrNull { it.iso6391.equals("en", ignoreCase = true) }
            ?: logosList.firstOrNull { it.iso6391 == null }
            ?: logosList.firstOrNull()

        return logo?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }

    fun getBackdropUrl(): String? {
        return backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
            ?: images?.backdrops?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/original$it" }
    }

    fun getPosterUrl(preferredLang: String? = null): String? {
        val posterList = images?.posters
        if (!posterList.isNullOrEmpty() && !preferredLang.isNullOrBlank()) {
            val matched = posterList.firstOrNull { it.iso6391.equals(preferredLang, ignoreCase = true) }
            if (matched?.filePath != null) {
                return "https://image.tmdb.org/t/p/w500${matched.filePath}"
            }
        }
        return posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            ?: images?.posters?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }

    fun getYear(): Int? {
        val date = releaseDate ?: firstAirDate
        return date?.take(4)?.toIntOrNull()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbExternalIds(
    @JsonProperty("imdb_id") val imdbId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbImages(
    @JsonProperty("logos") val logos: List<TmdbImageFile>? = null,
    @JsonProperty("backdrops") val backdrops: List<TmdbImageFile>? = null,
    @JsonProperty("posters") val posters: List<TmdbImageFile>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbImageFile(
    @JsonProperty("file_path") val filePath: String? = null,
    @JsonProperty("iso_639_1") val iso6391: String? = null,
    @JsonProperty("width") val width: Int? = null,
    @JsonProperty("height") val height: Int? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideos(
    @JsonProperty("results") val results: List<TmdbVideo>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideo(
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("site") val site: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("official") val official: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCredits(
    @JsonProperty("cast") val cast: List<TmdbCastMember>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCastMember(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbRecommendations(
    @JsonProperty("results") val results: List<TmdbRecommendationItem>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbRecommendationItem(
    @JsonProperty("id") val id: Int,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null
)
