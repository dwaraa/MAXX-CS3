package com.specialitystream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.specialitystream.crypto.TweetNaclFast
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

object VidLinkHelper {
    private const val KEY_HEX = "c75136c5668bbfe65a7ecad431a745db68b5f381555b38d8f6c699449cf11fcd"

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
        "Origin" to "https://vidlink.pro",
        "Referer" to "https://vidlink.pro/",
        "Accept" to "application/json, text/plain, */*"
    )

    private fun hexToBytes(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return bytes
    }

    fun encryptToken(mediaId: String): String {
        val key = hexToBytes(KEY_HEX)
        val nonce = ByteArray(24)
        val timestamp = (System.currentTimeMillis() / 1000) + 480

        val mediaBytes = mediaId.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(mediaBytes.size + 8).order(ByteOrder.BIG_ENDIAN)
        buf.put(mediaBytes)
        buf.putLong(timestamp)
        val message = buf.array()

        val box = TweetNaclFast.SecretBox(key)
        val cipher = box.box(message, nonce)

        val fullPayload = ByteArray(nonce.size + cipher.size)
        System.arraycopy(nonce, 0, fullPayload, 0, nonce.size)
        System.arraycopy(cipher, 0, fullPayload, nonce.size, cipher.size)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(fullPayload)
    }

    suspend fun getStream(
        tmdbId: Long,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        return try {
            val token = encryptToken(tmdbId.toString())
            val url = if (season != null && episode != null) {
                "https://vidlink.pro/api/b/tv/$token/$season/$episode?multiLang=1"
            } else {
                "https://vidlink.pro/api/b/movie/$token?multiLang=1"
            }

            val response = app.get(url, headers = headers)
            if (response.code !in 200..299) return false

            val data = parseJson<VidlinkResponse>(response.text)
            val stream = data.stream ?: return false

            var found = false

            // 1. Direct playlist if provided
            val playlist = stream.playlist
            if (!playlist.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = "VidLink",
                        name = "VidLink Auto HLS",
                        url = playlist,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://vidlink.pro/"
                        this.headers = mapOf(
                            "Referer" to "https://vidlink.pro/",
                            "Origin" to "https://vidlink.pro",
                            "User-Agent" to headers["User-Agent"]!!
                        )
                    }
                )
                found = true
            }

            // 2. Direct MP4 qualities
            stream.qualities?.forEach { (resKey, q) ->
                val qUrl = q.url ?: return@forEach
                val qualityInt = resKey.toIntOrNull() ?: Qualities.Unknown.value
                val isHls = qUrl.contains(".m3u8")
                val codec = if (!q.codecName.isNullOrBlank()) " (${q.codecName})" else ""

                callback(
                    newExtractorLink(
                        source = "VidLink",
                        name = "VidLink ${resKey}p$codec",
                        url = qUrl,
                        type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://vidlink.pro/"
                        this.headers = mapOf(
                            "Referer" to "https://vidlink.pro/",
                            "Origin" to "https://vidlink.pro",
                            "User-Agent" to headers["User-Agent"]!!
                        )
                        this.quality = qualityInt
                    }
                )
                found = true
            }

            // 3. Captions / Subtitles
            stream.captions?.forEach { cap ->
                val capUrl = cap.url ?: return@forEach
                val lang = cap.language?.ifBlank { "English" } ?: "English"
                subtitleCallback(
                    SubtitleFile(
                        lang = lang,
                        url = capUrl
                    )
                )
            }

            found
        } catch (_: Exception) {
            false
        }
    }
}
