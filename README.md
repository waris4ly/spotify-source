# spotify-source

A rewritten Spotify source manager for Lavaplayer.

Supports Spotify tracks, albums, playlists, artists, podcasts, episodes, and audiobooks with on-demand audio resolution and built-in rate-limit handling.

## Table of Contents

- [Features](#features)
- [Plugin](#plugin)
- [Common](#common)
- [Search Prefixes and Links](#search-prefixes-and-links)
- [Track Plugin Info](#track-plugin-info)
- [Important Notes](#important-notes)
- [Issues and Support](#issues-and-support)
- [License](#license)

---

## Features

- **Zero Credentials Needed:** Queries Spotify's web player endpoints by default without requiring Premium or developer keys.
- **On-Demand Audio Mirroring:** Metadata loads immediately; audio streams resolve via ISRC matching on playback, keeping queueing instant.
- **Circuit Breaker:** Automatically cools down rate-limited backends and routes requests to available fallbacks.
- **Inflight Deduplication:** Concurrent requests for the same track or playlist share a single network call in memory.
- **Short Link Resolution:** Automatically follows `spotify.link` and `spotify.app.link` redirect chains.
- **Extended Metadata:** Exposes artwork colors, album owner, track counts, canvas loops, and ISRCs in Lavalink's `pluginInfo`.

---

## Plugin

For use with [Lavalink v4](https://github.com/lavalink-devs/Lavalink).

### Installation

Download the latest `spotify-plugin-x.x.x.jar` from [Releases](https://github.com/waris4ly/spotify-source/releases) and place it in your `plugins/` directory:

```text
lavalink-server/
├── application.yml
├── Lavalink.jar
└── plugins/
    └── spotify-plugin-x.x.x.jar
```

### Configuration (`application.yml`)

```yaml
plugins:
  # Disable LavaSrc's spotify module if installed to avoid source name conflicts
  lavasrc:
    sources:
      spotify: false

  spotify:
    enabled: true
    countryCode: "US"
    internalBackend: true

    # Optional: Official Spotify Developer credentials
    clientId: ""
    clientSecret: ""

    # Optional: sp_dc cookie for Canvas video loops and client metadata
    accountCookie: ""

    # Limits
    searchLimit: 10
    playlistLoadLimit: 1000
    albumLoadLimit: 500
    showLoadLimit: 200
    recommendationLimit: 10

    # Audio mirroring (resolved on YouTube via ISRC / title)
    mirrorProviders:
      - 'ytsearch:"%isrc%"'
      - "ytsearch:%query%"
    mirrorDurationToleranceMs: 5000

    # Cache (in minutes)
    metadataCacheMinutes: 30
    mirrorCacheMinutes: 360
```

---

## Common

Standalone Lavaplayer 2.x audio source manager for JVM applications.

### Gradle Setup

```kotlin
repositories {
    mavenCentral()
    maven(url = "https://maven.lavalink.dev/releases")
}

dependencies {
    implementation("dev.arbjerg:lavaplayer:2.2.7")
    implementation("dev.waris.spotify:common:x.x.x")
}
```

### Maven Setup

```xml
<repositories>
    <repository>
        <id>lavalink-releases</id>
        <url>https://maven.lavalink.dev/releases</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>dev.arbjerg</groupId>
        <artifactId>lavaplayer</artifactId>
        <version>2.2.7</version>
    </dependency>
    <dependency>
        <groupId>dev.waris.spotify</groupId>
        <artifactId>common</artifactId>
        <version>x.x.x</version>
    </dependency>
</dependencies>
```

### Usage

**Kotlin:**
```kotlin
val playerManager = DefaultAudioPlayerManager()
val options = SpotifyOptions(countryCode = "US", playlistLoadLimit = 1000)

playerManager.registerSourceManager(SpotifyAudioSourceManager(options))
```

**Java:**
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
SpotifyOptions options = new SpotifyOptions();

playerManager.registerSourceManager(new SpotifyAudioSourceManager(options));
```

---

## Search Prefixes and Links

### Search Prefixes

| Prefix | Description | Example Query |
| --- | --- | --- |
| `spsearch:` | Track search by title and artist | `spsearch:Starboy The Weeknd` |
| `spsuggest:` | Autocomplete search suggestions | `spsuggest:Dua Lipa` |
| `sprec:` | Algorithmic recommendations by track ID | `sprec:7qiZfU4dY1lWllzX7mPBI3` |
| `spsimilar:` | Similar albums and tracks by track ID | `spsimilar:7qiZfU4dY1lWllzX7mPBI3` |

### Supported Links

- **Tracks:** `https://open.spotify.com/track/...` or `spotify:track:...`
- **Albums:** `https://open.spotify.com/album/...` or `spotify:album:...`
- **Playlists:** `https://open.spotify.com/playlist/...` or `spotify:playlist:...`
- **Artists:** `https://open.spotify.com/artist/...` or `spotify:artist:...`
- **Podcasts & Episodes:** `https://open.spotify.com/show/...` or `https://open.spotify.com/episode/...`
- **Audiobooks & Chapters:** `https://open.spotify.com/chapter/...`
- **Short Links:** `https://spotify.link/...` and `https://spotify.app.link/...` (followed automatically)

---

## Track Plugin Info

Extended metadata delivered in Lavalink's `pluginInfo`:

**Track:**
```json
{
  "type": "track",
  "url": "https://open.spotify.com/track/...",
  "author": "Artist Name",
  "artworkUrl": "https://i.scdn.co/image/...",
  "isrc": "USUG12345678",
  "canvasUrl": "https://canvaz.scdn.co/...",
  "colors": {
    "dark": -14408668,
    "light": -1250068,
    "raw": -986896
  }
}
```

**Playlist:**
```json
{
  "type": "playlist",
  "url": "https://open.spotify.com/playlist/...",
  "author": "Playlist Owner",
  "artworkUrl": "https://i.scdn.co/image/...",
  "totalTracks": 42
}
```

---

## Important Notes

- **LavaSrc Conflict:** If LavaSrc is installed on the same server, disable its Spotify source (`plugins.lavasrc.sources.spotify: false`) to avoid duplicate source registration.
- **Audio Provider:** Spotify audio streams are DRM-encrypted on Spotify CDN servers. `spotify-source` mirrors tracks to YouTube using ISRC fingerprints. Ensure a playable audio source (such as `youtube-source`) is registered.
- **Credentials:** No Spotify Premium subscription or developer keys are required. If `clientId` and `clientSecret` are omitted, the plugin automatically routes requests through the internal web player backend.

---

## Issues and Support

For questions, bug reports, or feature requests, open an issue using the corresponding template:

- [**Bug Report**](.github/ISSUE_TEMPLATE/bug_report.yml): For errors, broken queries, or playback failures.
- [**Feature Request**](.github/ISSUE_TEMPLATE/feature_request.md): For proposing new capabilities, prefixes, or enhancements.

---

## License

spotify-source is licensed under the [MIT License](LICENSE).
