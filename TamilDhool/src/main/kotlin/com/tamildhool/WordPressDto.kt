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

    val displayDate: String
        get() {
            val titleText = cleanTitle

            // 1. Match dd-mm-yyyy or dd.mm.yyyy or dd/mm/yyyy
            val dmyRegex = Regex("""\b(\d{1,2})[-./](\d{1,2})[-./](\d{2,4})\b""")
            val dmyMatch = dmyRegex.find(titleText)
            if (dmyMatch != null) {
                val d = dmyMatch.groupValues[1].padStart(2, '0')
                val m = dmyMatch.groupValues[2].padStart(2, '0')
                val y = dmyMatch.groupValues[3].let { if (it.length == 2) "20$it" else it }
                return "$d-$m-$y"
            }

            // 2. Match text month e.g. 11th Oct 2026 or 11 October 2026
            val wordDateRegex = Regex(
                """\b(\d{1,2})(?:st|nd|rd|th)?\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+(\d{2,4})\b""",
                RegexOption.IGNORE_CASE
            )
            val wordMatch = wordDateRegex.find(titleText)
            if (wordMatch != null) {
                return wordMatch.value
            }

            // 3. Fallback to post.date (ISO format: 2026-10-11T12:00:00)
            val pDate = date ?: ""
            if (pDate.length >= 10 && pDate[4] == '-' && pDate[7] == '-') {
                val y = pDate.substring(0, 4)
                val m = pDate.substring(5, 7)
                val d = pDate.substring(8, 10)
                return "$d-$m-$y"
            }

            return ""
        }

    val pureShowTitle: String
        get() {
            var text = cleanTitle

            // 1. Remove dates
            text = text.replace(Regex("""\b\d{1,2}[-./]\d{1,2}[-./]\d{2,4}\b"""), " ")
            text = text.replace(
                Regex(
                    """\b\d{1,2}(?:st|nd|rd|th)?\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{2,4}\b""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )

            // 2. Remove episode / day / part numbers
            text = text.replace(Regex("""\b(?:Episode|Epi|Ep)\s*\.?\s*\d+\b""", RegexOption.IGNORE_CASE), " ")
            text = text.replace(Regex("""\b(?:Day)\s*\.?\s*\d+\b""", RegexOption.IGNORE_CASE), " ")
            text = text.replace(Regex("""\b(?:Part)\s*\.?\s*\d+\b""", RegexOption.IGNORE_CASE), " ")

            // 3. Remove channel, serial/show keywords and special modifiers (case-insensitive)
            val channelPatterns = listOf(
                """(?:Sun|Vijay|Star\s*Vijay|Zee\s*Tamil|Colors\s*Tamil|Kalaignar|Jaya|Raj|Polimer)\s*TV\s*(?:Serial|Show|Reality\s*Show)?""",
                """(?:Sun|Vijay|Zee|Colors|Kalaignar)\s*(?:Serial|Show)""",
                """(?:TV\s*)?(?:Serial|Show|Reality\s*Show)""",
                """(?:சன்|விஜய்|ஜீ|கலர்ஸ்|கலைஞர்)\s*(?:டிவி)?\s*(?:சீரியல்|நிகழ்ச்சி)?""",
                """(?:Special\s*Episode|Mega\s*Episode|Maha\s*Sangamam|Special\s*Show|Special)""",
                """(?:Watch\s*Online|Full\s*Episode|Today\s*Episode|Online)"""
            )
            for (p in channelPatterns) {
                text = text.replace(Regex("""(?i)\b$p\b"""), " ")
            }

            // 4. Normalize dashes and clean symbols/whitespace
            text = text.replace('–', '-').replace('—', '-')
                .replace(Regex("""[\[\]\(\)]"""), " ")
                .replace(Regex("""[\s\-_|–—:]+$"""), "")
                .replace(Regex("""^[\s\-_|–—:]+"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()

            return if (text.isNotBlank()) text else cleanTitle
        }

    val showCategory: WpTerm?
        get() {
            val catTerms = embedded?.terms?.firstOrNull() ?: return null
            // Select the specific show sub-category (exclude general top-level channel categories)
            val specificCat = catTerms.firstOrNull { term ->
                val slug = term.slug?.lowercase() ?: ""
                slug != "sun-tv" && slug != "sun-tv-serial" && slug != "sun-tv-show" &&
                slug != "vijay-tv" && slug != "vijay-tv-serial" && slug != "vijay-tv-show" &&
                slug != "zee-tamil" && slug != "zee-tamil-serial" && slug != "zee-tamil-show" &&
                slug != "colors-tamil" && slug != "colors-tamil-serial" && slug != "colors-tamil-show" &&
                slug != "kalaignar-tv" && slug != "kalaignar-tv-serial" && slug != "kalaignar-tv-show" &&
                slug != "uncategorized"
            }
            return specificCat ?: catTerms.firstOrNull()
        }

    val isPromoCategory: Boolean
        get() {
            val slug = showCategory?.slug?.lowercase() ?: ""
            return slug == "news-gossips" ||
                    categories?.any { it == 28 } == true ||
                    link?.contains("/news-gossips/") == true
        }

    val isSingleSpecialProgram: Boolean
        get() {
            val slug = showCategory?.slug?.lowercase() ?: ""
            return slug.contains("special-program") ||
                    slug.contains("special-show") ||
                    slug.contains("special-shows")
        }

    val isPromoOnly: Boolean
        get() {
            val titleLower = cleanTitle.lowercase()
            return titleLower.contains("promo") ||
                    titleLower.contains("trailer") ||
                    titleLower.contains("teaser") ||
                    titleLower.contains("ப்ரோமோ") ||
                    titleLower.contains("டிரெய்லர்")
        }

    val isStandaloneItem: Boolean
        get() = isPromoCategory || isSingleSpecialProgram || isPromoOnly

    val isStandalone: Boolean
        get() = isStandaloneItem

    val resolvedShowTitle: String
        get() {
            if (isStandaloneItem) return cleanTitle
            val catName = showCategory?.name?.let { org.jsoup.Jsoup.parse(it).text() }
            return if (!catName.isNullOrBlank() && !isSingleSpecialProgram && !isPromoCategory) {
                catName
            } else {
                pureShowTitle
            }
        }

    val homeCardTitle: String
        get() = if (isStandaloneItem) cleanTitle else resolvedShowTitle

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
