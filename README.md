# TamilDhool & Einthusan CloudStream 3 Plugin

A high-performance **CloudStream 3** provider plugin providing **TamilDhool** (Tamil TV Serials and Shows via WordPress REST API) and **Einthusan** (South Asian Movies with 4K UHD and HD playback).

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
├── TamilDhool/
│   ├── build.gradle.kts            # TamilDhool plugin metadata & settings
│   └── src/
│       └── main/
│           └── kotlin/
│               └── com/
│                   └── tamildhool/
│                       ├── TamilDhoolPlugin.kt    # Entrypoint (@CloudstreamPlugin)
│                       ├── TamilDhoolProvider.kt  # TamilDhool MainAPI implementation
│                       ├── WordPressDto.kt        # WordPress REST API JSON models
│                       └── Extractors.kt          # Video link resolution engine
├── Einthusan/
│   ├── build.gradle.kts            # Einthusan plugin metadata & settings
│   └── src/
│       └── main/
│           └── kotlin/
│               └── com/
│                   └── einthusan/
│                       ├── EinthusanPlugin.kt     # Entrypoint (@CloudstreamPlugin)
│                       ├── EinthusanProvider.kt   # Einthusan MainAPI (HD & 4K UHD)
│                       ├── TmdbHelper.kt          # TMDB mapping for logos, backdrops & metadata
│                       └── TmdbDto.kt             # TMDB REST API response models
├── build.gradle.kts                # Root buildscript
├── settings.gradle.kts             # Gradle subproject configuration (TamilDhool + Einthusan)
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

1. Create a new repository on GitHub (e.g. `TAMILDHOOL-CS3`).
2. Push this repository to `main`:
   ```bash
   git init
   git add .
   git commit -m "Initial commit for TamilDhool CloudStream plugin"
   git remote add origin https://github.com/<your-username>/TAMILDHOOL-CS3.git
   git push -u origin main
   ```
3. Enable **Read and write permissions** for GitHub Actions in your repository:
   - Go to **Settings** > **Actions** > **General** > **Workflow permissions**.
   - Select **Read and write permissions** and save.
4. When the GitHub Action completes, it pushes the compiled `.cs3` files and `plugins.json` to the `builds` branch.
5. In CloudStream 3 on your Android phone or TV:
   - Navigate to **Settings** > **Extensions** > **Add Repository**.
   - Enter Repository Name: `TamilDhool`
   - Enter Repository URL:
     ```
     https://raw.githubusercontent.com/<your-username>/TAMILDHOOL-CS3/builds/repo.json
     ```
   - Click **Download** on TamilDhool and enjoy!

---

## 🌐 Note on ISP Blocks & Domain Mirrors

In some regions (e.g. India), internet providers may block or spoof DNS for `tamildhool.tech` / `tamildhool.net`. 
To ensure smooth playback in CloudStream:
- Go to CloudStream **Settings** > **Network** > Enable **DNS-over-HTTPS (DoH)** (select **Cloudflare** or **Google**).
