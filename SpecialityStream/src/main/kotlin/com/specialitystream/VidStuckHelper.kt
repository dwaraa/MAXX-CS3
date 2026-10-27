package com.specialitystream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckTmdbDetails(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("last_air_date") val lastAirDate: String? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckQwertyResponse(
    @JsonProperty("token") val token: String? = null,
    @JsonProperty("ts") val ts: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckLink(
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("resolution") val resolution: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckDub(
    @JsonProperty("type") val type: Int? = null,
    @JsonProperty("lanCode") val lanCode: String? = null,
    @JsonProperty("lanName") val lanName: String? = null,
    @JsonProperty("original") val original: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckServerResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("links") val links: List<VidStuckLink>? = null,
    @JsonProperty("dubs") val dubs: List<VidStuckDub>? = null,
    @JsonProperty("server") val server: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckCaption(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("display") val display: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VidStuckSubtitleResponse(
    @JsonProperty("captions") val captions: List<VidStuckCaption>? = null
)

object VidStuckHelper {
    private const val BASE_URL = "https://vidstuck.cc"
    private const val AES_KEY = "7f4c9e2a81d63b05c4f7a9e8126d3b50e1a8c7f23d9465ab0c6e9f1d4a7b832c"

    // Obfuscated parameter names from vidstuck client scripts
    private const val KEY_TMDB_ID = "a7f39c821d604e5b91e547b"
    private const val KEY_TIMESTAMP = "61d9a5274c8e3b2f38c291e6"
    private const val KEY_TOKEN = "c492f7a183d01e436c538a716d"
    private const val KEY_TITLE = "5e28c9147a06d1e29f3674b392a1"
    private const val KEY_YEAR = "b731e6c94f8675f8341c306e"
    private const val KEY_SEASON = "d8427b5c38a2f957c3613e85b"
    private const val KEY_EPISODE = "91c6ea753d1f785c92346b713d"
    private const val KEY_SERVER = "6b491e258d392e7561a9384c"
    private const val KEY_MEDIA_TYPE = "c81b306d28147a35632e816b"
    private const val KEY_RELEASE_DATE = "e164931a39e5814b3027"
    private const val KEY_LAST_AIR_DATE = "e9543416ad739e5814b3027"
    private const val KEY_IMDB_ID = "f35a81943265e871c4933a725f"

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$BASE_URL/",
        "Origin" to BASE_URL,
        "Accept" to "application/json, text/plain, */*"
    )

    private data class ServerDef(
        val name: String,
        val server: String,
        val dubSupport: Boolean
    )

    private val SERVERS = listOf(
        ServerDef("Orion", "orion", dubSupport = true),
        ServerDef("Andromeda", "andromeda", dubSupport = false),
        ServerDef("Atlas", "atlas", dubSupport = false),
        ServerDef("Daedalus", "daedalus", dubSupport = false),
        ServerDef("Ursa", "ursa", dubSupport = false)
    )

    suspend fun getStream(
        tmdbId: Long,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        title: String? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        val isMovie = season == null || episode == null
        val mediaType = if (isMovie) "movie" else "tv"

        // 1. Fetch metadata details from vidstuck backend (or fallback to passed params)
        val details = try {
            val res = app.get("$BASE_URL/backend/tmdb/details/$mediaType/$tmdbId?language=en-US", headers = defaultHeaders, timeout = 6)
            if (res.code in 200..299) parseJson<VidStuckTmdbDetails>(res.text) else null
        } catch (_: Throwable) {
            null
        }

        val resolvedTitle = details?.title ?: title ?: "Movie"
        val resolvedReleaseDate = details?.releaseDate ?: year?.let { "$it-01-01" } ?: "2020-01-01"
        val resolvedYear = resolvedReleaseDate.split("-").firstOrNull() ?: year?.toString() ?: "2020"
        val resolvedLastAirDate = details?.lastAirDate
        val resolvedImdbId = details?.imdbId ?: imdbId

        var hasFoundLink = false

        // 2. Extract subtitles
        try {
            extractSubtitles(
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                title = resolvedTitle,
                year = resolvedYear,
                releaseDate = resolvedReleaseDate,
                subtitleCallback = subtitleCallback
            )
        } catch (_: Throwable) {
        }

        // 3. Extract servers concurrently using Cloudstream amap
        val serverResults = SERVERS.amap { serverDef ->
            try {
                extractServer(
                    serverDef = serverDef,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    title = resolvedTitle,
                    year = resolvedYear,
                    releaseDate = resolvedReleaseDate,
                    lastAirDate = resolvedLastAirDate,
                    imdbId = resolvedImdbId,
                    callback = {
                        callback(it)
                        hasFoundLink = true
                    }
                )
            } catch (_: Throwable) {
                false
            }
        }

        if (serverResults.any { it }) hasFoundLink = true
        return hasFoundLink
    }

    private suspend fun extractServer(
        serverDef: ServerDef,
        tmdbId: Long,
        mediaType: String,
        season: Int?,
        episode: Int?,
        title: String,
        year: String,
        releaseDate: String,
        lastAirDate: String?,
        imdbId: String?,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Step A: Request /backend/qwerty token for this server
        val qwerty = requestQwertyToken(tmdbId, mediaType, serverDef.server, season, episode) ?: return false

        // Step B: Request server stream data
        val queryParams = buildServerParams(
            tmdbId = tmdbId,
            mediaType = mediaType,
            server = serverDef.server,
            ts = qwerty.ts ?: System.currentTimeMillis(),
            token = qwerty.token ?: "",
            season = season,
            episode = episode,
            title = title,
            year = year,
            releaseDate = releaseDate,
            lastAirDate = lastAirDate,
            imdbId = imdbId
        )

        val serverUrl = "$BASE_URL/backend/servers/${serverDef.server}?$queryParams"
        val serverResp = try {
            val res = app.get(serverUrl, headers = defaultHeaders, timeout = 10)
            if (res.code in 200..299) parseJson<VidStuckServerResponse>(res.text) else null
        } catch (_: Throwable) {
            null
        } ?: return false

        var foundAny = false

        // Emit default sources
        serverResp.links?.forEachIndexed { idx, linkItem ->
            val encryptedLink = linkItem.link ?: return@forEachIndexed
            val decryptedUrl = decryptLink(encryptedLink) ?: return@forEachIndexed
            val rawFullUrl = if (decryptedUrl.startsWith("/")) "$BASE_URL$decryptedUrl" else decryptedUrl
            val linkType = determineLinkType(linkItem.type, rawFullUrl)
            val fullUrl = if (linkType == ExtractorLinkType.DASH) {
                VidStuckProxyServer.registerManifest(rawFullUrl)
            } else if (linkType == ExtractorLinkType.M3U8 && !rawFullUrl.contains(".m3u8")) {
                if (rawFullUrl.contains("?")) "$rawFullUrl&.m3u8" else "$rawFullUrl#.m3u8"
            } else {
                rawFullUrl
            }

            val dubTag = if (serverDef.dubSupport) " [Original Audio]" else ""
            val mirrorTag = if ((serverResp.links.size) > 1) " (Mirror ${idx + 1})" else ""
            val resTag = if (linkItem.resolution != null && linkItem.resolution > 0) " (${linkItem.resolution}p)" else ""
            val displayName = "VidStuck - ${serverDef.name}$dubTag$resTag$mirrorTag"

            callback(
                newExtractorLink(
                    source = "VidStuck",
                    name = displayName,
                    url = fullUrl,
                    type = linkType
                ) {
                    this.referer = "$BASE_URL/"
                    this.headers = mapOf(
                        "Referer" to "$BASE_URL/",
                        "Origin" to BASE_URL,
                        "User-Agent" to defaultHeaders["User-Agent"]!!
                    )
                    this.quality = linkItem.resolution ?: Qualities.Unknown.value
                }
            )
            foundAny = true
        }

        // If server supports dubs (like Orion), fetch available audio dub tracks concurrently reusing the token
        if (serverDef.dubSupport && !serverResp.dubs.isNullOrEmpty() && !qwerty.token.isNullOrBlank()) {
            val audioDubs = serverResp.dubs.filter {
                it.type == 0 && it.original != true && !it.lanCode.isNullOrBlank()
            }

            val dubResults = audioDubs.amap { dub ->
                try {
                    extractDubTrack(
                        server = serverDef.server,
                        serverName = serverDef.name,
                        token = qwerty.token,
                        ts = qwerty.ts ?: System.currentTimeMillis(),
                        dub = dub,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        season = season,
                        episode = episode,
                        title = title,
                        year = year,
                        releaseDate = releaseDate,
                        lastAirDate = lastAirDate,
                        imdbId = imdbId,
                        callback = callback
                    )
                } catch (_: Throwable) {
                    false
                }
            }
            if (dubResults.any { it }) foundAny = true
        }

        return foundAny
    }

    private suspend fun extractDubTrack(
        server: String,
        serverName: String,
        token: String,
        ts: Long,
        dub: VidStuckDub,
        tmdbId: Long,
        mediaType: String,
        season: Int?,
        episode: Int?,
        title: String,
        year: String,
        releaseDate: String,
        lastAirDate: String?,
        imdbId: String?,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseParams = buildServerParams(
            tmdbId = tmdbId,
            mediaType = mediaType,
            server = server,
            ts = ts,
            token = token,
            season = season,
            episode = episode,
            title = title,
            year = year,
            releaseDate = releaseDate,
            lastAirDate = lastAirDate,
            imdbId = imdbId
        )
        val dubParams = "$baseParams&dubCode=${URLEncoder.encode(dub.lanCode ?: "", "UTF-8")}&dubType=${dub.type ?: 0}"
        val dubUrl = "$BASE_URL/backend/servers/$server?$dubParams"

        val resp = try {
            val res = app.get(dubUrl, headers = defaultHeaders, timeout = 10)
            if (res.code in 200..299) parseJson<VidStuckServerResponse>(res.text) else null
        } catch (_: Throwable) {
            null
        } ?: return false

        var found = false
        val dubLabel = dub.lanName ?: dub.lanCode?.uppercase() ?: "Dub"

        resp.links?.forEach { linkItem ->
            val encryptedLink = linkItem.link ?: return@forEach
            val decryptedUrl = decryptLink(encryptedLink) ?: return@forEach
            val rawFullUrl = if (decryptedUrl.startsWith("/")) "$BASE_URL$decryptedUrl" else decryptedUrl
            val linkType = determineLinkType(linkItem.type, rawFullUrl)
            val fullUrl = if (linkType == ExtractorLinkType.DASH) {
                VidStuckProxyServer.registerManifest(rawFullUrl)
            } else if (linkType == ExtractorLinkType.M3U8 && !rawFullUrl.contains(".m3u8")) {
                if (rawFullUrl.contains("?")) "$rawFullUrl&.m3u8" else "$rawFullUrl#.m3u8"
            } else {
                rawFullUrl
            }
            val resTag = if (linkItem.resolution != null && linkItem.resolution > 0) " (${linkItem.resolution}p)" else ""

            callback(
                newExtractorLink(
                    source = "VidStuck",
                    name = "VidStuck - $serverName [$dubLabel]$resTag",
                    url = fullUrl,
                    type = linkType
                ) {
                    this.referer = "$BASE_URL/"
                    this.headers = mapOf(
                        "Referer" to "$BASE_URL/",
                        "Origin" to BASE_URL,
                        "User-Agent" to defaultHeaders["User-Agent"]!!
                    )
                    this.quality = linkItem.resolution ?: Qualities.Unknown.value
                }
            )
            found = true
        }

        return found
    }

    private suspend fun extractSubtitles(
        tmdbId: Long,
        mediaType: String,
        season: Int?,
        episode: Int?,
        title: String,
        year: String,
        releaseDate: String,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val qwerty = requestQwertyToken(tmdbId, mediaType, "subtitle", season, episode) ?: return
        val baseParams = buildServerParams(
            tmdbId = tmdbId,
            mediaType = mediaType,
            server = "subtitle",
            ts = qwerty.ts ?: System.currentTimeMillis(),
            token = qwerty.token ?: "",
            season = season,
            episode = episode,
            title = title,
            year = year,
            releaseDate = releaseDate,
            lastAirDate = null,
            imdbId = null
        )

        val subUrl = "$BASE_URL/backend/subtitle?$baseParams"
        val resp = try {
            val res = app.get(subUrl, headers = defaultHeaders, timeout = 8)
            if (res.code in 200..299) parseJson<VidStuckSubtitleResponse>(res.text) else null
        } catch (_: Throwable) {
            null
        } ?: return

        resp.captions?.forEach { cap ->
            val fileUrl = cap.file
            if (!fileUrl.isNullOrBlank()) {
                val label = cap.display?.ifBlank { null } ?: "English"
                subtitleCallback(newSubtitleFile(label, fileUrl))
            }
        }
    }

    private suspend fun requestQwertyToken(
        tmdbId: Long,
        mediaType: String,
        server: String,
        season: Int?,
        episode: Int?
    ): VidStuckQwertyResponse? {
        val jsonBody = if (mediaType == "tv" && season != null && episode != null) {
            """{"$KEY_TMDB_ID":$tmdbId,"$KEY_MEDIA_TYPE":"$mediaType","$KEY_SERVER":"$server","$KEY_SEASON":$season,"$KEY_EPISODE":$episode}"""
        } else {
            """{"$KEY_TMDB_ID":$tmdbId,"$KEY_MEDIA_TYPE":"$mediaType","$KEY_SERVER":"$server"}"""
        }

        return try {
            val reqBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val res = app.post(
                "$BASE_URL/backend/qwerty",
                headers = defaultHeaders,
                requestBody = reqBody,
                timeout = 8
            )
            if (res.code in 200..299) parseJson<VidStuckQwertyResponse>(res.text) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildServerParams(
        tmdbId: Long,
        mediaType: String,
        server: String,
        ts: Long,
        token: String,
        season: Int? = null,
        episode: Int? = null,
        title: String,
        year: String,
        releaseDate: String,
        lastAirDate: String?,
        imdbId: String?
    ): String {
        fun encode(v: String) = URLEncoder.encode(v, "UTF-8")

        val sb = StringBuilder()
        sb.append("$KEY_TMDB_ID=").append(encode(tmdbId.toString()))
        sb.append("&$KEY_SERVER=").append(encode(server))
        sb.append("&$KEY_MEDIA_TYPE=").append(encode(mediaType))
        sb.append("&$KEY_TIMESTAMP=").append(encode(ts.toString()))
        sb.append("&$KEY_TOKEN=").append(encode(token))
        sb.append("&$KEY_TITLE=").append(encode(title))
        sb.append("&$KEY_YEAR=").append(encode(year))
        sb.append("&$KEY_RELEASE_DATE=").append(encode(releaseDate))

        if (mediaType == "tv" && season != null && episode != null) {
            sb.append("&$KEY_SEASON=").append(encode(season.toString()))
            sb.append("&$KEY_EPISODE=").append(encode(episode.toString()))
        }
        if (mediaType == "tv" && !lastAirDate.isNullOrBlank()) {
            sb.append("&$KEY_LAST_AIR_DATE=").append(encode(lastAirDate))
        }
        if (!imdbId.isNullOrBlank()) {
            sb.append("&$KEY_IMDB_ID=").append(encode(imdbId))
        }

        return sb.toString()
    }

    private fun determineLinkType(type: String?, url: String): ExtractorLinkType {
        val lowerUrl = url.lowercase()
        val lowerType = type?.lowercase() ?: ""
        return when {
            lowerType == "dash" || lowerUrl.contains(".mpd") -> ExtractorLinkType.DASH
            lowerType == "hls" || lowerUrl.contains(".m3u8") || lowerUrl.contains("edge") -> ExtractorLinkType.M3U8
            else -> ExtractorLinkType.VIDEO
        }
    }

    fun decryptLink(encryptedBase64: String): String? {
        return try {
            val raw = try {
                android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT)
            } catch (_: Throwable) {
                java.util.Base64.getDecoder().decode(encryptedBase64)
            }
            if (raw.size < 16) return encryptedBase64
            val header = String(raw, 0, 8, StandardCharsets.US_ASCII)
            if (header != "Salted__") return encryptedBase64

            val salt = raw.copyOfRange(8, 16)
            val cipherBytes = raw.copyOfRange(16, raw.size)
            val passBytes = AES_KEY.toByteArray(StandardCharsets.UTF_8)

            // OpenSSL EVP_BytesToKey with MD5 for CryptoJS compatibility
            val md5 = MessageDigest.getInstance("MD5")
            val total = ByteArray(48)
            var currentHash = ByteArray(0)
            var totalOffset = 0

            while (totalOffset < 48) {
                md5.reset()
                if (currentHash.isNotEmpty()) {
                    md5.update(currentHash)
                }
                md5.update(passBytes)
                md5.update(salt)
                currentHash = md5.digest()

                val copyLen = minOf(currentHash.size, 48 - totalOffset)
                System.arraycopy(currentHash, 0, total, totalOffset, copyLen)
                totalOffset += copyLen
            }

            val keyBytes = total.copyOfRange(0, 32)
            val ivBytes = total.copyOfRange(32, 48)

            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}

object VidStuckProxyServer {
    private var serverSocket: ServerSocket? = null
    private var port: Int = 0
    private val manifestCache = ConcurrentHashMap<String, ByteArray>()
    private val manifestUrls = ConcurrentHashMap<String, String>()
    private val proxyHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "https://vidstuck.cc/",
        "Origin" to "https://vidstuck.cc"
    )

    @Synchronized
    fun start(): Int {
        if (serverSocket != null && !serverSocket!!.isClosed) return port
        return try {
            val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            port = ss.localPort

            thread(name = "VidStuckProxyThread", isDaemon = true) {
                while (!ss.isClosed) {
                    try {
                        val client = ss.accept()
                        thread(isDaemon = true) {
                            handleClient(client)
                        }
                    } catch (_: Throwable) {
                        break
                    }
                }
            }
            port
        } catch (_: Throwable) {
            -1
        }
    }

    fun registerManifest(rawUrl: String): String {
        val serverPort = start()
        val fallback = if (rawUrl.contains("?")) "$rawUrl&.mpd" else "$rawUrl#.mpd"
        if (serverPort <= 0) return fallback

        val id = UUID.randomUUID().toString()
        manifestUrls[id] = rawUrl

        // Keep cache bounded
        if (manifestCache.size > 25) {
            manifestCache.clear()
        }
        if (manifestUrls.size > 50) {
            manifestUrls.clear()
            manifestUrls[id] = rawUrl
        }

        return "http://127.0.0.1:$serverPort/manifest.mpd?id=$id"
    }

    private fun fetchAndSanitize(targetUrl: String): ByteArray? {
        val fullUrl = if (targetUrl.startsWith("/")) "https://vidstuck.cc$targetUrl" else targetUrl
        return try {
            val reqBuilder = okhttp3.Request.Builder().url(fullUrl)
            proxyHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
            val response = app.baseClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return null
            val rawXml = response.body?.string() ?: return null
            // Fix unescaped ampersands in XML attribute values (e.g. CloudFront signed parameters)
            val sanitized = rawXml.replace(Regex("&(?!(?:amp|lt|gt|quot|apos);)"), "&amp;")
            sanitized.toByteArray(Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val line = reader.readLine() ?: return
                val parts = line.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val path = parts[1]

                val id = path.substringAfter("id=").substringBefore("&").substringBefore(" ")
                var data = manifestCache[id]
                if (data == null) {
                    val url = manifestUrls[id]
                    if (url != null) {
                        data = fetchAndSanitize(url)
                        if (data != null) {
                            manifestCache[id] = data
                        }
                    }
                }

                val out = s.getOutputStream()
                if (data == null) {
                    val resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    out.write(resp.toByteArray(Charsets.US_ASCII))
                    out.flush()
                    return
                }

                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/dash+xml\r\n" +
                        "Content-Length: ${data.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"

                out.write(header.toByteArray(Charsets.US_ASCII))
                if (method != "HEAD") {
                    out.write(data)
                }
                out.flush()
            }
        } catch (_: Throwable) {
        }
    }
}

