# spotify-source

A rewritten Spotify source manager for Lavaplayer.

Loads tracks, albums, and playlists by routing requests across Spotify's web player endpoints and official API. It handles rate limits with built-in circuit breakers and resolves playable audio streams on-demand via ISRC matching to keep large queues responsive.

## Table of Contents

- [Features](#features)
  - Overview of key capabilities including zero credentials, lazy mirroring, and circuit breaker.
- [Plugin](#plugin)
  - Information about the Lavalink v4 plugin module, installation, and server setup.
  - [Installation](#installation)
    - How to add `spotify-plugin-x.x.x.jar` to your Lavalink server plugins directory.
  - [Configuration](#configuration-applicationyml)
    - Full `application.yml` reference for backend routing, credentials, and audio mirror options.
- [Common](#common)
  - Information about the standalone Lavaplayer 2.x audio source manager module.
  - [Gradle Setup](#gradle-setup)
    - Dependency setup for standalone Gradle Kotlin and Groovy projects.
  - [Kotlin Usage](#kotlin-usage)
    - Code sample demonstrating registration and usage with Kotlin.
  - [Java Usage](#java-usage)
    - Code sample demonstrating registration and usage with Java.
- [Search Prefixes and Links](#search-prefixes-and-links)
  - Supported Spotify search prefixes (`spsearch:`, `spsuggest:`, `sprec:`, `spsimilar:`) and URL formats.
- [Track Plugin Info](#track-plugin-info)
  - Extended metadata payloads delivered in Lavalink `pluginInfo` (artwork, colors, canvas, ISRC).
- [Important Notes](#important-notes)
  - Critical operational details on LavaSrc conflict prevention, playable audio mirrors, and Premium requirements.
- [Issues and Support](#issues-and-support)
  - Issue templates and guidelines for reporting bugs or requesting new features.
- [License](#license)
  - Licensing information under the MIT License.

---

## Features

- **Zero Configuration:** Queries Spotify's web player backend by default. No Spotify Premium subscription or developer credentials required.
- **Lazy Audio Mirroring:** Enqueued tracks load metadata immediately. Audio stream resolution delegates to YouTube only when playback begins (`process()`), eliminating queue lag on large playlists.
- **Circuit Breaker:** Rate limits (HTTP 429) and consecutive request errors trigger cooldown periods that automatically route requests through fallback backends.
- **Inflight Deduplication:** Simultaneous requests across guilds for the same track or playlist share a single network call in memory.
- **Short Link Resolution:** Automatically follows `spotify.link` and `spotify.app.link` redirect chains to their canonical URLs.
- **Extended Metadata:** Exposes artwork URLs, album author, total track count, canvas video loops, and dominant artwork colors (`dark`, `light`, `raw`) in Lavalink's `pluginInfo`.

---

## Plugin

For use with [Lavalink v4](https://github.com/lavalink-devs/Lavalink).

### Installation

1. Download the latest `spotify-plugin-x.x.x.jar` from the GitHub Releases tab.
2. Place the jar file inside your Lavalink server's `plugins/` directory:

```text
lavalink-server/
├── application.yml
├── Lavalink.jar
└── plugins/
    └── spotify-plugin-x.x.x.jar
```

3. Configure your `application.yml` file and start Lavalink.

### Configuration (`application.yml`)

All settings include default values. Only specify properties you wish to override.

```yaml
plugins:
  # LavaSrc conflict prevention
  # If LavaSrc is installed on the same server, disable its Spotify source to avoid conflicts.
  lavasrc:
    sources:
      spotify: false

  spotify:
    # Set to false to disable this plugin without removing the jar
    enabled: true

    # Official Spotify Developer credentials
    # Optional. Leave both empty to use the free internal backend.
    # Note: Spotify requires the application owner to have an active Spotify Premium subscription for official apps.
    clientId: ""
    clientSecret: ""

    # Optional sp_dc cookie from an active Spotify account session
    # Enables Spotify Canvas video loops and client metadata lookups.
    # A free throwaway account cookie is recommended.
    accountCookie: ""

    # Optional custom service URL returning anonymous tokens
    # Leave empty to let spotify-source handle anonymous token acquisition automatically.
    anonymousTokenUrl: ""

    # Market country code for catalog lookups (two-letter ISO 3166-1 alpha-2 code)
    countryCode: "US"

    # Internal web player backend
    # Required for loading public playlists and podcasts without user OAuth login.
    # Default is true.
    internalBackend: true

    # Maximum number of search results returned by spsearch: (1 to 50)
    searchLimit: 10

    # Maximum number of tracks loaded from a playlist (1 to 10000)
    playlistLoadLimit: 1000

    # Maximum number of tracks loaded from an album (1 to 10000)
    albumLoadLimit: 500

    # Maximum number of episodes or chapters loaded from a show or audiobook (1 to 10000)
    showLoadLimit: 200

    # Keep local files from playlists as query-only search tracks
    # When false, local tracks in a playlist are omitted.
    allowLocalFiles: false

    # Extract dominant color palette from artwork (dark, light, raw) in pluginInfo
    extractColors: false

    # Extract Canvas video loop URLs in pluginInfo (requires accountCookie)
    canvas: false

    # Number of tracks returned by sprec: and spsimilar: (1 to 50)
    recommendationLimit: 10

    # Per-request HTTP timeout in seconds
    requestTimeoutSeconds: 10

    # Search templates tried in order to resolve a playable audio stream on YouTube
    # %isrc% is replaced with the track ISRC
    # %query% is replaced with "Track Title Primary Artist"
    mirrorProviders:
      - 'ytsearch:"%isrc%"'
      - "ytsearch:%query%"

    # Maximum millisecond duration difference allowed when scoring mirror candidates
    # Results differing by more than this tolerance receive a score penalty.
    mirrorDurationToleranceMs: 5000

    # In-memory cache duration in minutes for track and playlist metadata
    metadataCacheMinutes: 30

    # In-memory cache duration in minutes for resolved mirror audio streams
    mirrorCacheMinutes: 360

    # Optional overrides for internal Spotify GraphQL query hashes
    queryHashes: {}
```

---

## Common

Provides the standalone Lavaplayer source manager for JVM applications and bots running without the Lavalink server.

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

### Kotlin Usage

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

### Java Usage

```java
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.waris.spotify.SpotifyAudioSourceManager;
import dev.waris.spotify.SpotifyOptions;

AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
SpotifyOptions options = new SpotifyOptions();

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

Tracks and playlists loaded by this source provide extended metadata in Lavalink's `pluginInfo` payload.

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

### 1. LavaSrc Conflict Prevention
Both `LavaSrc` and `spotify-source` register an audio source named `spotify` and intercept the `spsearch:` prefix. If both plugins are installed, disable LavaSrc's Spotify module in `application.yml`:

```yaml
plugins:
  lavasrc:
    sources:
      spotify: false
```

### 2. Required Playable Audio Source
Spotify tracks are DRM-encrypted on Spotify CDN servers. `spotify-source` mirrors tracks to YouTube using ISRC fingerprints and duration scoring. Ensure a playable audio source (such as `youtube-source`) is registered in your Lavalink server or player manager.

### 3. Spotify Developer API and Premium Requirement
In development mode, Spotify's developer portal requires the application owner to have an active Spotify Premium subscription. If you do not have Spotify Premium, leave `clientId` and `clientSecret` empty; the plugin will use the internal web player backend to load tracks, albums, and playlists without credentials.

### 4. Internal Web Player Backend
The internal backend queries Spotify's public web player endpoints to retrieve playlist and catalog metadata. This enables public playlist loading without requiring user OAuth authentication.

---

## Issues and Support

For questions, troubleshooting, bug reports, or feature requests, open an issue on the repository using the appropriate template:

- [**Bug Report**](.github/ISSUE_TEMPLATE/bug_report.yml): For errors, broken queries, or unexpected failures. Please provide complete reproduction steps, stack traces, and relevant configuration.
- [**Feature Request**](.github/ISSUE_TEMPLATE/feature_request.md): For proposing new capabilities, prefixes, or enhancements.

---

## License

spotify-source is licensed under the [MIT License](LICENSE).

