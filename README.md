# MAXX CloudStream 3 Plugins (MAXX-CS3)

A high-performance **CloudStream 3** plugin repository featuring **SpecialityStream** (TMDB catalog with VidLink.pro & multi-embeds), **Einthusan** (South Asian Movies with 4K UHD and HD playback), **TamilDhool** (Tamil TV Serials and Shows via WordPress REST API), and **TamilUltra** (Live TV channels).

---

## ✨ Features

### 📺 TamilDhool Provider
- **⚡ Fast WordPress REST API**: Fetches serials, shows, posters, and episodes directly via JSON endpoints rather than slow HTML scraping.
- **📺 Comprehensive Channel Coverage**:
  - **Sun TV Serials** (Kayal, Singapennae, Sundari, Vanathai Pola, Marumagal, Iniya, etc.)
  - **Vijay TV Serials & Shows** (Siragadikka Aasai, Baakiyalakshmi, Pandian Stores, Mahanadhi, Cooku with Comali, etc.)
  - **Zee Tamil Serials** (Karthigai Dheepam, Meenakshi Ponnunga, Sandakozhi, Anna, etc.)
  - **Colors Tamil & Kalaignar TV**
  - **Tamil Web Series & Reality Shows**
- **🔁 Serial Episode Aggregation**: Opening an episode automatically discovers and lists previous recent episodes from that serial's category for easy binge-watching.
- **🎥 Multi-Host Video Extraction**: Dailymotion, YouTube, PlayVK / VK, Streamwish, Filelions, Vidguard, Voe, Doodstream, direct `.m3u8` / `.mp4` streams.

### 🎬 Einthusan Provider
- **🍿 Multi-Language Catalog**: Tamil, Telugu, Hindi, Malayalam, and Kannada movie collections.
- **✨ TMDB Rich Metadata & Logo Mapping**:
  - **Transparent Title Logos**: Automatically maps transparent PNG movie logos (`logoUrl`) from TMDB.
  - **Ultra-HD Backdrops & Posters**: Replaces low-res covers with crystal-clear TMDB theatrical posters and 4K backdrops (`backgroundPosterUrl`).
  - **Rich Metadata & Cast**: Fetches official plot synopses, release year, runtime duration, TMDB rating score, and cast members complete with headshot pictures.
  - **Trailers & Recommendations**: Embeds official YouTube movie trailers and related title recommendations directly on the details screen.
  - **Sync Integration**: Maps `tmdb` and `imdb` IDs for seamless Trakt / Simkl tracking synchronization.
- **💎 4K UHD Direct MP4 Extraction**: Direct high-speed CDN playback without premium account lock (`B{movieId}.mp4` static CDN detection).
- **🚀 Obfuscation Decryption Engine**: Automatically extracts `data-pageid` (with HTML entity unescaping) and `data-ejpingables`, posts to internal gorilla AJAX endpoints, and unscrambles obfuscated base64 `EJLinks`.
- **⚡ Adaptive HLS & 1080p Full HD**: Provides both Adaptive HLS playlists (`.m3u8`) and direct MP4 video streams.
- **🔍 Quick Search & Home Hubs**: Recently Added, Top 30-Day Popular, Top Rated, Staff Picks, and Regional Hits.

### 🌟 SpecialityStream Provider
- **🌐 TMDB Master Catalog**: Direct integration with TMDB API for global movies, TV shows, and anime.
- **✨ High-Fidelity Details**: Official posters, 4K backdrops, trailers, complete cast, seasons & episode lists, and recommendations.
- **🚀 Advanced Decryption & Multi-Embed Resolvers**:
  - **VidLink.pro**: Pure Kotlin implementation of TweetNaCl XSalsa20-Poly1305 SecretBox encryption for high-speed direct CDN streams (1080p, 720p, 480p, 360p) with multi-language subtitle tracks.
  - **VidSrc Ecosystem**: Dynamic mirror resolution across `vidsrc.net`, `vidsrc.pm`, `vidsrc.cc`, `vidsrc.xyz`, `vidsrc.vip`.
  - **Multi-Embed Fallbacks**: `2Embed`, `AutoEmbed`, `MultiEmbed`, `SmashyStream`, `MoviesAPI`.

### ⚡ 1TamilMV Provider (1tamilmv.fi)
- **🧲 Direct Torrent & Magnet Streaming**: Complete extraction of all available magnet links and torrent attachments directly compatible with CloudStream's built-in torrent engine.
- **🎬 Comprehensive Regional & Global Catalog**:
  - **Tamil**: WEB-HD / BluRay, Web Series & TV Shows, PreDVD / DVDScr, and HD-Rips.
  - **Telugu**: WEB-HD / BluRay, Web Series, and PreDVD.
  - **Hindi**: WEB-HD / BluRay, Web Series, and PreDVD.
  - **Malayalam & Kannada**: WEB-HD / BluRay releases and Web Series.
  - **English & Hollywood**: Hollywood multi-audio movies and Web Series.
- **🔄 Dynamic Mirror & Domain Tracking**: Automatically follows 301/302 redirects from `1tamilmv.fi` to active domain mirrors (`.capital`, etc.).
- **✨ TMDB Rich Metadata Integration**: Automatic title & release parsing with TMDB matching for 4K theatrical backdrops, title logos, official synopsis, cast, and trailers.
- **📊 Precise Quality & Size Parsing**: Clearly labels each stream option with quality badge (4K, 1080p, 720p, 480p), video codec (HEVC/x265, AVC/x264), audio channels (DD+ 5.1, AAC), and file size.
- **🔍 Fast Invision Forum Search**: Full topic search across releases with automatic pagination.

---

## 📁 Project Structure

```
TAMILDHOOL-CS3/
├── .github/
│   └── workflows/
│       └── build.yml               # Automated GitHub Actions CI/CD to build & publish
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── SpecialityStream/
│   ├── build.gradle.kts            # SpecialityStream plugin metadata
│   └── src/
│       └── main/
│           ├── java/com/specialitystream/crypto/TweetNaclFast.java  # XSalsa20-Poly1305 encryption
│           └── kotlin/com/specialitystream/
│               ├── SpecialityStreamPlugin.kt   # Plugin entrypoint
│               ├── SpecialityStreamProvider.kt # TMDB discovery, load & stream resolution
│               ├── VidLinkHelper.kt            # VidLink.pro encryption & stream fetcher
│               ├── EmbedExtractors.kt          # Multi-embed & VidSrc fallback extractors
│               └── TmdbDto.kt                  # TMDB JSON data transfer models
├── TamilDhool/
│   ├── build.gradle.kts            # TamilDhool plugin metadata & settings
│   └── src/
│       └── main/kotlin/com/tamildhool/
├── Einthusan/
│   ├── build.gradle.kts            # Einthusan plugin metadata & settings
│   └── src/
│       └── main/kotlin/com/einthusan/
├── TamilUltra/
│   ├── build.gradle.kts            # TamilUltra plugin metadata & settings
│   └── src/
│       └── main/kotlin/com/tamilultra/
├── build.gradle.kts                # Root buildscript
├── settings.gradle.kts             # Subproject configuration
├── gradle.properties               # JVM and compiler settings
├── repo.json                       # Cloudstream repository descriptor
└── README.md
```

---

## 🛠️ Building Locally

Ensure you have **JDK 17** and an **Android SDK** installed:

### 1. Build the Plugin (.cs3)
```powershell
.\gradlew.bat make
```
*(On Linux/macOS: `./gradlew make`)*

The compiled plugin `.cs3` artifact will be located in:
`build/TamilDhool.cs3`

### 2. Generate Plugin Repository Index (plugins.json)
```powershell
.\gradlew.bat makePluginsJson
```

### 3. Deploy Directly to a Connected Device via ADB
```powershell
.\gradlew.bat deployWithAdb
```

---

## 🚀 Publishing to GitHub & Adding to CloudStream

1. Repository on GitHub: `MAXX-CS3`.
2. Push this repository to `main`:
   ```bash
   git init
   git add .
   git commit -m "Update MAXX-CS3 repository"
   git remote add origin https://github.com/dwaraa/MAXX-CS3.git
   git push -u origin main
   ```
3. Enable **Read and write permissions** for GitHub Actions in your repository:
   - Go to **Settings** > **Actions** > **General** > **Workflow permissions**.
   - Select **Read and write permissions** and save.
4. When the GitHub Action completes, it pushes the compiled `.cs3` files and `plugins.json` to the `builds` branch.
5. In CloudStream 3 on your Android phone or TV:
   - Navigate to **Settings** > **Extensions** > **Add Repository**.
   - Enter Repository Name: `MAXX`
   - Enter Repository URL:
     ```
     https://raw.githubusercontent.com/dwaraa/MAXX-CS3/builds/repo.json
     ```
   - Click **Download** on your desired providers and enjoy!

---

## 🌐 Note on ISP Blocks & Domain Mirrors

In some regions (e.g. India), internet providers may block or spoof DNS for `tamildhool.tech` / `tamildhool.net`. 
To ensure smooth playback in CloudStream:
- Go to CloudStream **Settings** > **Network** > Enable **DNS-over-HTTPS (DoH)** (select **Cloudflare** or **Google**).
