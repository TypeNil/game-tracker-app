# Changelog

All notable changes to the **GameTracker** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-06

Initial public release of **GameTracker** — a production-grade Android application and Kotlin/Ktor BFF service proxying the IGDB API.

### Added

#### Android Application (`:app`)
- **Discover Screen & Feeds**:
  - Personalized "For You" recommendations powered by on-device heuristic preference scoring.
  - Popular Upcoming games with countdown indicators.
  - Top-Rated games chart with infinite scroll via Room SSOT.
- **Search & Filter**:
  - Real-time catalog search with 300 ms debouncing and instant cancellation of stale in-flight requests.
  - Interactive filter chips for genres and platforms.
  - Search history management with persistent Room caching.
- **Game Details Screen**:
  - Full game metadata presentation (developer/publisher companies, genres, themes, game modes, platforms, and release dates).
  - High-resolution poster covers with gradient backdrops and responsive skeletons.
  - Media carousel supporting screenshots, artwork, and developer trailers.
  - "Similar Games" discovery carousel with synthetic navigation back stack.
  - Native Android share sheet integration and YouTube trailer playback via external Intent.
- **Interactive Screenshot Gallery**:
  - Fullscreen screenshot viewer with custom gesture arbitration.
  - Pinch-to-zoom (1x..4x), double-tap zoom toggling (1x $\leftrightarrow$ 2.5x), and aspect-ratio bounded panning.
- **Personal Game Library**:
  - 5 status tiers (*Playing, Completed, Wishlist, Dropped, Not Interested*).
  - 1–10 rating scrubber with semantic tiers (*Masterpiece, Great, Good, Mediocre, Bad*).
  - Quick Hours stepper dialog with session delta tracking and playtime recording.
  - Personal markdown notes preview in cards and full note editing in `EditLibrarySheet`.
  - Favorite toggle with persistent status.
- **Background Release Tracking & Notifications**:
  - Periodic release date sync using WorkManager with network constraints.
  - Android 13+ (`POST_NOTIFICATIONS`) runtime permission flows and dedicated notification channels.
  - Type-safe deep linking (`gametracker://game/{id}`) navigating directly to game details.
- **Internationalization (i18n)**:
  - Complete English and Russian (ru) localization.
  - Locale-aware date and number formatting.
- **Build Variants**:
  - `demo`: 100% self-contained offline mode with bundled authentic IGDB fixtures and zero external dependencies.
  - `live`: Configurable network mode communicating with the Ktor BFF gateway.

#### Backend-for-Frontend Service (`:backend`)
- **Microservice Architecture**:
  - High-performance asynchronous service built on Kotlin and Ktor 3 with Netty engine.
  - Micro-proxying endpoints: `/v1/discover/top-rated`, `/v1/games/search`, `/v1/games/{id}`, and `/health`.
- **Security & Quota Hardening**:
  - Monotonic `SmoothRateLimiter` enforcing strict 300 ms intervals ($\le 3.33\text{ req/s}$) to guarantee compliance with IGDB's 4 req/s limit.
  - Single-flight in-memory Caffeine cache (`SupervisorScope` + `CompletableDeferred`) eliminating request dogpiling.
  - Thread-safe OAuth2 token management with Mutex synchronization and automatic invalidation upon upstream 401.
  - Ingress socket peer validation against `trustedHosts` before honoring `X-Forwarded-For`.
  - `SearchQueryValidator` enforcing Unicode NFC normalization, 1..100 length bounds, and character allowlists to prevent APICalypse injection.
  - Strict log sanitization and normalized `ErrorResponse` formatting.

#### Architecture & Quality
- **Data Layer & Room SSOT**:
  - Single Source of Truth architecture: UI collects repository-provided `Flow<List<Game>>` and `Flow<PagingData<Game>>`.
  - Room database schema `v6` with tested migrations `1→2→3→4→5→6`.
  - `ForeignKey.RESTRICT` constraints protecting user library records from catalog cache eviction.
- **Testing & CI/CD**:
  - Three parallel GitHub Actions CI jobs: Backend quality, Android lint & assemble, and Connected instrumentation on API 30.
  - Static analysis with Detekt and formatting rules (both Android and backend pass with no findings).
  - R8 release verification with resource shrinking and ProGuard optimization rules.

### Tested Environments
- **Physical Device**: OnePlus 11 (CPH2449), Android 14 / Android 15 (API 34/35).
- **CI Emulator**: Android 11 (API 30), 320x640 dp @ 160 dpi.
- **Local Emulators**: Medium Phone / Pixel 7 (API 34 / 35).
- **Platform Compatibility**: `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 36` (Android 16), `compileSdk = 37`.

### Known Limitations
- **Upstream Rate Limits**: Upstream IGDB enforces a 4 req/s quota. The BFF queues requests with a 300 ms step, but uncached bulk operations are rate-limited.
- **Offline Dataset Scope**: The `demo` flavor includes a curated collection of popular titles and edge cases, rather than the complete IGDB database.
- **Local Storage**: User library data and notes are stored strictly on-device in Room SQLite (no cloud account synchronization).
- **Video Playback**: Game trailers are handed off to native external video applications (YouTube / web browser) via Android Intent to avoid bundling heavyweight player components.
