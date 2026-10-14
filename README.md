# MAXX CloudStream 3 Repository

Official CloudStream 3 provider repository containing extensions for movies, TV shows, regional serials, live channels, and torrents.

---

## 📦 Providers Included

| Provider | Type | Description |
| :--- | :--- | :--- |
| **SpecialityStream** | Movies, TV, Anime | TMDB discovery catalog with fast VidLink & multi-embed streaming |
| **1TamilMV** | Movies, Series, Torrents | High-speed torrent & direct link streaming with quality badges and TMDB artwork |
| **Einthusan** | Movies | South Asian cinema catalog with 4K UHD and 1080p direct streams |
| **TamilDhool** | TV Serials & Shows | Tamil serials & shows (Sun TV, Vijay TV, Zee Tamil, Colors) via REST API |
| **TamilUltra** | Live TV | Tamil entertainment, news, music, and sports channels |

---

## 📲 How to Add to CloudStream

1. Open **CloudStream 3** on your Android phone, tablet, or Android TV.
2. Go to **Settings** > **Extensions**.
3. Click **Add Repository**.
4. Fill in the details:
   - **Repository Name**: `MAXX`
   - **Repository URL**:
     ```
     https://raw.githubusercontent.com/dwaraa/MAXX-CS3/builds/repo.json
     ```
5. Click **Download** to install your chosen providers.

---

## 🛠️ Local Development & Building

Requirements: **JDK 17** and **Android SDK**.

```powershell
# Build all plugins (.cs3 files)
.\gradlew.bat make

# Build plugins.json manifest
.\gradlew.bat makePluginsJson

# Deploy directly to connected Android device via ADB
.\gradlew.bat deployWithAdb
```

---

## 🌐 Connectivity & ISP Notes

If some provider streams or sites are restricted by your ISP:
- In CloudStream, navigate to **Settings** > **Network** > Enable **DNS-over-HTTPS (DoH)** (Cloudflare or Google).
