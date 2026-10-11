package com.tamildhool

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
data class WpPost(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("title") val title: WpRendered? = null,
    @JsonProperty("content") val content: WpRendered? = null,
    @JsonProperty("excerpt") val excerpt: WpRendered? = null,
    @JsonProperty("categories") val categories: List<Int>? = null,
    @JsonProperty("jetpack_featured_media_url") val jetpackFeaturedMediaUrl: String? = null,
    @JsonProperty("_embedded") val embedded: WpEmbedded? = null
) {
    val cleanTitle: String
        get() {
            val raw = title?.rendered ?: "Untitled"
            return org.jsoup.Jsoup.parse(raw).text()
        }

    val posterUrl: String?
        get() {
            return embedded?.featuredMedia?.firstOrNull()?.sourceUrl
                ?: jetpackFeaturedMediaUrl
                ?: extractFirstImg(content?.rendered)
        }

    private fun extractFirstImg(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return try {
            val doc = org.jsoup.Jsoup.parse(html)
            doc.selectFirst("img")?.attr("abs:src")?.ifBlank {
                doc.selectFirst("img")?.attr("src")
            }
        } catch (_: Exception) {
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpCategory(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("count") val count: Int? = null,
    @JsonProperty("link") val link: String? = null
)
