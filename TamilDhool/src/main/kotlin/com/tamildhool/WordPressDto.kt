package com.tamildhool

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpRendered(
    @JsonProperty("rendered") val rendered: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpMediaSize(
    @JsonProperty("source_url") val sourceUrl: String? = null,
    @JsonProperty("width") val width: Int? = null,
    @JsonProperty("height") val height: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpMediaSizes(
    @JsonProperty("large") val large: WpMediaSize? = null,
    @JsonProperty("medium_large") val mediumLarge: WpMediaSize? = null,
    @JsonProperty("full") val full: WpMediaSize? = null,
    @JsonProperty("medium") val medium: WpMediaSize? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpMediaDetails(
    @JsonProperty("sizes") val sizes: WpMediaSizes? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpFeaturedMedia(
    @JsonProperty("source_url") val sourceUrl: String? = null,
    @JsonProperty("media_details") val mediaDetails: WpMediaDetails? = null
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
            // Prioritize landscape sizes from media details if available
            val media = embedded?.featuredMedia?.firstOrNull()
            val sizeUrl = media?.mediaDetails?.sizes?.let { s ->
                s.large?.sourceUrl
                    ?: s.mediumLarge?.sourceUrl
                    ?: s.full?.sourceUrl
                    ?: s.medium?.sourceUrl
            }

            return sizeUrl
                ?: media?.sourceUrl
                ?: jetpackFeaturedMediaUrl
                ?: extractFirstImg(content?.rendered)
        }

    private fun extractFirstImg(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return try {
            // 1. Check for YouTube embed thumbnail (16:9 landscape)
            val ytMatch = Regex("""(?:youtube\.com/embed/|youtu\.be/)([a-zA-Z0-9_-]{11})""").find(html)
            if (ytMatch != null) {
                val videoId = ytMatch.groupValues[1]
                return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            }

            // 2. Extract first valid content image
            val doc = org.jsoup.Jsoup.parse(html)
            for (img in doc.select("img")) {
                val src = img.attr("abs:src").ifBlank {
                    img.attr("src").ifBlank {
                        img.attr("data-src").ifBlank {
                            img.attr("data-lazy-src")
                        }
                    }
                }
                if (src.isNotBlank() && !src.contains("gravatar") && !src.contains("emoji")) {
                    return src
                }
            }
            null
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
