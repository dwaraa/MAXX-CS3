package com.zdkserials

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

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
    @JsonProperty("medium") val medium: WpMediaSize? = null,
    @JsonProperty("thumbnail") val thumbnail: WpMediaSize? = null
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
data class WpEmbedded(
    @JsonProperty("wp:featuredmedia") val featuredMedia: List<WpFeaturedMedia>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpShow(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("title") val title: WpRendered? = null,
    @JsonProperty("content") val content: WpRendered? = null,
    @JsonProperty("excerpt") val excerpt: WpRendered? = null,
    @JsonProperty("_embedded") val embedded: WpEmbedded? = null
) {
    val cleanTitle: String
        get() {
            val raw = title?.rendered ?: "Untitled Show"
            return Jsoup.parse(raw).text().trim()
        }

    val posterUrl: String?
        get() {
            val media = embedded?.featuredMedia?.firstOrNull() ?: return null
            val sizes = media.mediaDetails?.sizes
            return sizes?.large?.sourceUrl
                ?: sizes?.mediumLarge?.sourceUrl
                ?: sizes?.medium?.sourceUrl
                ?: sizes?.full?.sourceUrl
                ?: media.sourceUrl
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpEpisode(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("title") val title: WpRendered? = null,
    @JsonProperty("content") val content: WpRendered? = null,
    @JsonProperty("excerpt") val excerpt: WpRendered? = null,
    @JsonProperty("_embedded") val embedded: WpEmbedded? = null
) {
    val cleanTitle: String
        get() {
            val raw = title?.rendered ?: "Episode"
            return Jsoup.parse(raw).text().trim()
        }

    val posterUrl: String?
        get() {
            val media = embedded?.featuredMedia?.firstOrNull() ?: return null
            val sizes = media.mediaDetails?.sizes
            return sizes?.mediumLarge?.sourceUrl
                ?: sizes?.large?.sourceUrl
                ?: sizes?.medium?.sourceUrl
                ?: sizes?.full?.sourceUrl
                ?: media.sourceUrl
        }

    val episodeNumber: Int?
        get() {
            val linkUrl = link ?: ""
            val match = Regex("""/season-\d+/(\d+)/watch/""").find(linkUrl)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
            val titleMatch = Regex("""\b(?:Episode|Ep|E)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(cleanTitle)
            return titleMatch?.groupValues?.get(1)?.toIntOrNull()
        }

    val seasonNumber: Int?
        get() {
            val linkUrl = link ?: ""
            val match = Regex("""/season-(\d+)/\d+/watch/""").find(linkUrl)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpChannel(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("title") val title: WpRendered? = null,
    @JsonProperty("_embedded") val embedded: WpEmbedded? = null
) {
    val cleanTitle: String
        get() {
            val raw = title?.rendered ?: "Channel"
            return Jsoup.parse(raw).text().trim()
        }

    val posterUrl: String?
        get() {
            val media = embedded?.featuredMedia?.firstOrNull() ?: return null
            val sizes = media.mediaDetails?.sizes
            return sizes?.large?.sourceUrl
                ?: sizes?.mediumLarge?.sourceUrl
                ?: sizes?.full?.sourceUrl
                ?: media.sourceUrl
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ZdkLoadMoreResponse(
    @JsonProperty("html") val html: String? = null,
    @JsonProperty("count") val count: Int? = null,
    @JsonProperty("more") val more: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WexioWatchSessionResponse(
    @JsonProperty("session_id") val sessionId: String? = null,
    @JsonProperty("embed_context_token") val embedContextToken: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WexioPlaybackGrantResponse(
    @JsonProperty("hls_url") val hlsUrl: String? = null
)

