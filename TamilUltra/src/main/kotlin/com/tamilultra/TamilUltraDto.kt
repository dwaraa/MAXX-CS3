package com.tamilultra

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpRendered(
    @JsonProperty("rendered") val rendered: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpFeaturedMedia(
    @JsonProperty("source_url") val sourceUrl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpTerm(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("taxonomy") val taxonomy: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpEmbedded(
    @JsonProperty("wp:featuredmedia") val featuredMedia: List<WpFeaturedMedia>? = null,
    @JsonProperty("wp:term") val terms: List<List<WpTerm>>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpMovieItem(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("title") val title: WpRendered? = null,
    @JsonProperty("content") val content: WpRendered? = null,
    @JsonProperty("genres") val genres: List<Int>? = null,
    @JsonProperty("_embedded") val embedded: WpEmbedded? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DooPlayerResponse(
    @JsonProperty("embed_url") val embedUrl: String? = null,
    @JsonProperty("type") val type: Any? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayerOption(
    @JsonProperty("nume") val nume: String,
    @JsonProperty("title") val title: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TamilUltraPassData(
    @JsonProperty("postId") val postId: Long,
    @JsonProperty("options") val options: List<PlayerOption> = emptyList(),
    @JsonProperty("pageUrl") val pageUrl: String? = null
)
