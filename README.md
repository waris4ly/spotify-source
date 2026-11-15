# spotify-source

A rewritten Spotify source manager for Lavaplayer and Lavalink v4.

Supports Spotify tracks, albums, playlists, artists, podcasts, episodes, and audiobooks with on-demand audio resolution and built-in rate-limit handling.

Spotify's CDN audio streams are DRM-encrypted. To provide music playback, `spotify-source` loads metadata directly from Spotify and resolves playable audio streams on-demand via ISRC fingerprints and duration matching against YouTube (or any registered playable source). Catalog requests are routed across Spotify's public web player backend and official developer API with automatic circuit breaker failovers and in-memory in-flight deduplication.

## Table of Contents

- [Features](#features)
- [Plugin](#plugin)
  - [Installation](#installation)
  - [Configuration](#configuration-applicationyml)
- [Common](#common)
  - [Gradle Setup](#gradle-setup)
  - [Maven Setup](#maven-setup)
  - [Usage](#usage)
- [Search Prefixes and Links](#search-prefixes-and-links)
- [Track Plugin Info](#track-plugin-info)
- [Important Notes](#important-notes)
- [Issues and Support](#issues-and-support)
- [License](#license)

---

## Features

- **Zero Credentials Needed:** Queries Spotify's public web player endpoints by default without requiring a Spotify Premium subscription or developer keys.
- **On-Demand Audio Mirroring:** Metadata loads immediately; audio streams resolve to YouTube via ISRC matching only when track playback begins, eliminating playlist queue lag.
- **Circuit Breaker:** Rate limits (HTTP 429) and consecutive connection errors automatically trigger cooldowns that route requests to available fallback backends.
- **Inflight Deduplication:** Concurrent requests across guilds for the same track or playlist share a single network call in memory.
- **Short Link Resolution:** Automatically follows `spotify.link` and `spotify.app.link` redirect chains to their canonical URLs.
- **Extended Metadata:** Exposes artwork URLs, album author, total track count, canvas video loops, and dominant artwork colors in Lavalink's `pluginInfo`.

---

## Plugin

For use with [Lavalink v4](https://github.com/lavalink-devs/Lavalink).

### Installation

Download the latest `spotify-plugin-x.x.x.jar` from [Releases](https://github.com/waris4ly/spotify-source/releases) and place it inside your Lavalink server's `plugins/` directory:

```text
lavalink-server/
├── application.yml
├── Lavalink.jar
└── plugins/
    └── spotify-plugin-x.x.x.jar
```

### Configuration (`application.yml`)

All settings include sensible defaults. Only include the properties you need to override.

```yaml
plugins:
  lavasrc:
    sources:
      spotify: false # disable LavaSrc's Spotify module to prevent source conflicts

  spotify:
    enabled: true # toggle plugin on or off without removing the jar
    countryCode: "US" # two-letter ISO 3166-1 alpha-2 country code for market-specific catalog lookups
    internalBackend: true # use Spotify's public web player backend (allows loading without user OAuth login)
    clientId: "" # optional Spotify Developer Client ID (required only if using official Web API)
    clientSecret: "" # optional Spotify Developer Client Secret
    accountCookie: "" # optional sp_dc cookie from an active Spotify session (enables Canvas and client metadata)
    anonymousTokenUrl: "" # optional custom endpoint returning anonymous tokens (defaults to internal flow)
    searchLimit: 10 # maximum results returned for spsearch: queries (1 to 50)
    playlistLoadLimit: 1000 # maximum tracks loaded from a playlist (1 to 10000)
    albumLoadLimit: 500 # maximum tracks loaded from an album (1 to 10000)
    showLoadLimit: 200 # maximum episodes loaded from a show or podcast (1 to 10000)
    recommendationLimit: 10 # number of tracks returned by sprec: and spsimilar: queries (1 to 50)
    allowLocalFiles: false # keep local playlist files as search-based track titles instead of skipping them
    extractColors: false # extract dominant artwork palette (dark, light, raw) into Lavalink track pluginInfo
    canvas: false # extract Spotify Canvas looping video URLs into pluginInfo (requires accountCookie)
    requestTimeoutSeconds: 10 # HTTP connection and read timeout in seconds for Spotify API requests
    mirrorDurationToleranceMs: 5000 # maximum allowed duration difference in ms between Spotify track and YouTube video
    metadataCacheMinutes: 30 # in-memory cache time in minutes for track, album, and playlist metadata
    mirrorCacheMinutes: 360 # in-memory cache time in minutes for resolved YouTube mirror stream URLs
    mirrorProviders: # ordered list of YouTube search templates used to find playable audio streams
      - 'ytsearch:"%isrc%"' # first priority: exact ISRC code match
      - "ytsearch:%query%" # fallback: title and artist search
    queryHashes: {} # optional overrides for Spotify internal GraphQL operation sha256 hashes
```

---

## Common

Standalone Lavaplayer 2.x audio source manager for JVM applications and Discord bots running without a Lavalink server.

### Gradle Setup

**Kotlin DSL:**
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

**Groovy DSL:**
```groovy
repositories {
    mavenCentral()
    maven { url 'https://maven.lavalink.dev/releases' }
}

dependencies {
    implementation 'dev.arbjerg:lavaplayer:2.2.7'
    implementation 'dev.waris.spotify:common:x.x.x'
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
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import dev.waris.spotify.SpotifyAudioSourceManager
import dev.waris.spotify.SpotifyOptions

val playerManager = DefaultAudioPlayerManager()

val options = SpotifyOptions(
    countryCode = "US",
    playlistLoadLimit = 1000,
    metadataCacheMinutes = 30,
)

playerManager.registerSourceManager(SpotifyAudioSourceManager(options))
```

**Java:**
```java
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.waris.spotify.SpotifyAudioSourceManager;
import dev.waris.spotify.SpotifyOptions;

AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

SpotifyOptions options = new SpotifyOptions.Builder()
    .setCountryCode("US")
    .setPlaylistLoadLimit(1000)
    .setMetadataCacheMinutes(30)
    .build();

playerManager.registerSourceManager(new SpotifyAudioSourceManager(options));
```

---

## Search Prefixes and Links

### Search Prefixes

| Prefix | Description | Example Query |
| --- | --- | --- |
| `spsearch:` | Track search by song title and artist | `spsearch:Starboy The Weeknd` |
| `spsuggest:` | Autocomplete search suggestions | `spsuggest:Dua Lipa` |
| `sprec:` | Algorithmic recommendations seeded by a track ID | `sprec:7qiZfU4dY1lWllzX7mPBI3` |
| `spsimilar:` | Similar albums and tracks based on a track ID | `spsimilar:7qiZfU4dY1lWllzX7mPBI3` |

### Supported Links

- **Tracks:** `https://open.spotify.com/track/...` or `spotify:track:...`
- **Albums:** `https://open.spotify.com/album/...` or `spotify:album:...`
- **Playlists:** `https://open.spotify.com/playlist/...` or `spotify:playlist:...`
- **Artists:** `https://open.spotify.com/artist/...` or `spotify:artist:...`
- **Shows / Podcasts:** `https://open.spotify.com/show/...` or `spotify:show:...`
- **Podcast Episodes:** `https://open.spotify.com/episode/...` or `spotify:episode:...`
- **Audiobooks and Chapters:** `https://open.spotify.com/chapter/...` or `spotify:chapter:...`
- **Short Links:** `https://spotify.link/...` or `https://spotify.app.link/...` (redirects are followed automatically)

---

## Track Plugin Info

Extended metadata delivered in Lavalink's `pluginInfo` payload:

### Track Metadata

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

### Playlist Metadata

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

- **LavaSrc Conflict Prevention:** Both `LavaSrc` and `spotify-source` register an audio source named `spotify` and intercept the `spsearch:` prefix. If both plugins are installed on the same server, disable LavaSrc's Spotify module in `application.yml` (`plugins.lavasrc.sources.spotify: false`).
- **Required Playable Audio Source:** Spotify tracks are DRM-encrypted on Spotify's CDN. `spotify-source` mirrors tracks to YouTube using ISRC fingerprints. Ensure a playable audio source (such as `youtube-source`) is registered in your Lavalink server or player manager.
- **Credentials and Premium Requirements:** No Spotify Premium subscription or developer keys are required out of the box. Leaving `clientId` and `clientSecret` empty causes the plugin to use the internal web player backend, which queries Spotify's public endpoints to load tracks, albums, and playlists without requiring user OAuth login.

---

## Issues and Support

For questions, troubleshooting, bug reports, or feature requests, open an issue using the corresponding template:

- [**Bug Report**](.github/ISSUE_TEMPLATE/bug_report.yml): For errors, broken queries, or playback failures.
- [**Feature Request**](.github/ISSUE_TEMPLATE/feature_request.md): For proposing new capabilities, prefixes, or enhancements.

---

## License

spotify-source is licensed under the [MIT License](LICENSE).
