# Design Document: Feature 9.2 — User Library (Revised)

## 1. Overview & Goals

The **User Library** feature enables players to track, organize, and rate their personal game collection offline-first with reactive updates backed by Room Single Source of Truth (SSOT).

### Key Requirements & Review Contracts (Section 9.2)
1. **Statuses**:
   - `Wishlist` (`WISHLIST`): Games planned to play or purchased for backlog.
   - `Playing` (`PLAYING`): Currently active games.
   - `Completed` (`COMPLETED`): Finished games.
   - `Dropped` (`DROPPED`): Abandoned/discontinued games.
   - `Not interested` (`NOT_INTERESTED`): Games excluded from personal collection (shown only in its own tab, excluded from `ALL`).
2. **User Rating**: 1–10 scale. Dedicated `UserRatingBadge` (not 0–100 critic `RatingBadge`).
3. **Favorites (`isFavorite`)**: Orthogonal toggle bookmarking games across any status; filtered via a "Favorites only" filter chip.
4. **Filtering & Sorting**:
   - Status Tabs (`All`, `Playing`, `Wishlist`, `Completed`, `Dropped`, `Not interested`) with dynamic count badges.
   - Tab `ALL` contains `PLAYING`, `WISHLIST`, `COMPLETED`, `DROPPED` (excludes `NOT_INTERESTED`).
   - In-memory instant text filter (no network debounce).
   - Filter chip: `Favorites only`.
   - Sorting: `Recently Updated` (default), `User Rating (High → Low)`, `Title (A → Z)`, `Hours Played`.
5. **Game Details Integration**:
   - Interactive Library Action Card in [GameDetailsScreen](file:///c:/Users/type/AndroidStudioProjects/GameTracker/app/src/main/java/io/github/typenil/gametracker/feature/details/GameDetailsScreen.kt).
   - Hoisted `EditLibrarySheet` (Material 3 `ModalBottomSheet` at screen content root) with:
     - Status chips (`Wishlist`, `Playing`, `Completed`, `Dropped`, `Not interested`)
     - User rating 1–10 picker
     - Hours played stepper (`>= 0`)
     - Unicode-validated personal notes (`<= 500` code points)
     - Favorite toggle
     - "Remove from Library" action
   - Library mutations execute only when parent `GameEntity` exists (Parent-First RESTRICT safety).
6. **Navigation**:
   - Bottom Navigation Bar coordinated entirely in `AppNavHost` (ADR-006; `MainActivity.kt` unchanged).
   - Bottom bar visible **only** on top-level destinations: `DiscoverKey` and `LibraryKey`.
   - Hidden on `SearchKey` and `GameDetailsKey`.

---

## 2. Architecture & Data Flow

```
┌────────────────────────────────────────────────────────┐
│               UI / Presentation Layer                  │
│  - LibraryScreen (Tabs, Grid, Sort/Filter)             │
│  - GameDetailsScreen (Hoisted EditLibrarySheet)        │
│  - AppNavHost (Scaffold + NavigationBar for Top-Level) │
└───────────────────────────┬────────────────────────────┘
                            │ Flow / Events
┌───────────────────────────▼────────────────────────────┐
│                    ViewModel Layer                     │
│  - LibraryViewModel (StateFlow, WhileSubscribed(5s))   │
│  - GameDetailsViewModel (combine 4 flows, Lazily)      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                      Data Layer                        │
│  - LibraryRepository / DefaultLibraryRepository        │
│  - Parent-First validation for ForeignKey.RESTRICT     │
└───────────────────────────┬────────────────────────────┘
                            │ Flow / Transactions
┌───────────────────────────▼────────────────────────────┐
│                  Database Layer (Room)                 │
│  - LibraryDao (@Transaction PopulatedLibraryGameEntity)│
│  - GameTrackerTypeConverters (PLAN_TO_PLAY → WISHLIST) │
│  - No Room schema bump (remains v3)                    │
└────────────────────────────────────────────────────────┘
```

---

## 3. Detailed Component Contracts

### 3.1. Domain Layer (`core/model`)
- **`LibraryStatus`**:
  ```kotlin
  enum class LibraryStatus {
      PLAYING,
      WISHLIST,
      COMPLETED,
      DROPPED,
      NOT_INTERESTED
  }
  ```
- **`LibraryEntry`**:
  ```kotlin
  data class LibraryEntry(
      val gameId: Long,
      val status: LibraryStatus,
      val userRating: Int? = null,
      val userNotes: String? = null,
      val isFavorite: Boolean = false,
      val addedAtEpochSeconds: Long,
      val updatedAtEpochSeconds: Long,
      val hoursPlayed: Int = 0
  )
  ```
- **`LibraryGame`**:
  ```kotlin
  data class LibraryGame(
      val game: Game,
      val entry: LibraryEntry
  )
  ```

### 3.2. Database Layer (`core/database`)
- **`GameTrackerTypeConverters`**:
  - `toLibraryStatus(value: String?)`: Strict decoding. Maps `"PLAN_TO_PLAY"` to `LibraryStatus.WISHLIST`, else `LibraryStatus.valueOf(value)`. Unknown values throw `IllegalArgumentException` (standard 4.3.4). Write path always writes `WISHLIST`.
- **`PopulatedLibraryGameEntity`**:
  ```kotlin
  data class PopulatedLibraryGameEntity(
      @Embedded val entry: LibraryEntryEntity,
      @Relation(parentColumn = "gameId", entityColumn = "id")
      val game: GameEntity
  )
  ```
- **`LibraryDao`**:
  ```kotlin
  @Transaction
  @Query("SELECT * FROM library_entries ORDER BY updatedAtEpochSeconds DESC")
  fun getPopulatedLibraryEntriesFlow(): Flow<List<PopulatedLibraryGameEntity>>
  ```

### 3.3. Data Layer (`core/data`)
- **`LibraryRepository`**:
  ```kotlin
  interface LibraryRepository {
      fun getLibraryGamesFlow(): Flow<List<LibraryGame>>
      fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntry?>
      suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit>
      suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit>
      suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit>
  }
  ```
- **Parent-First RESTRICT Safety**: In `DefaultLibraryRepository`, before inserting/updating a `LibraryEntryEntity`, verify `gameDao.getGameById(gameId) != null` (or inside a transaction runner). If the parent `GameEntity` does not exist, return `AppResult.Error(AppError.Database(...))` instead of crashing with SQLiteConstraintException.

### 3.4. Presentation Layer — Feature Details (`feature/details`)
- **`GameDetailsViewModel` Flow Combine Contract**:
  Combine at most 4 flows to prevent overload issues:
  ```kotlin
  data class DetailsFlags(
      val isLoading: Boolean = true,
      val isRefreshing: Boolean = false,
      val isEditingLibrary: Boolean = false,
      val message: Pair<AppError?, Int?>? = null
  )
  ```
  `combine(detailsFlow, isHydratedFlow, libraryEntryFlow, flagsFlow)` produces `GameDetailsUiState`.
- **`EditLibrarySheet`**: Hoisted to screen root in `GameDetailsScreen` (outside LazyColumn).

### 3.5. Presentation Layer — Feature Library (`feature/library`)
- **`LibraryTab`**: `ALL`, `PLAYING`, `WISHLIST`, `COMPLETED`, `DROPPED`, `NOT_INTERESTED`.
- **`LibrarySortOption`**: `UPDATED_DESC`, `USER_RATING_DESC`, `TITLE_ASC`, `HOURS_PLAYED_DESC`.
- **`LibraryUiState`**:
  - `tabCounts`: `ALL` count = sum of `PLAYING`, `WISHLIST`, `COMPLETED`, `DROPPED`.
  - `filteredGames`: filter tab -> filter favorites -> filter query -> sort.
- **`LibraryGameCard`**:
  - Clickable card opening Details.
  - Dedicated `UserRatingBadge` showing `userRating/10`.
  - Non-clickable status `Surface` pill.
  - No nested clickable `IconButton` inside `Card(onClick)` (standard 5).

### 3.6. Navigation (`app/navigation`)
- `AppNavHost` wraps destinations in a `Scaffold` with `NavigationBar`.
- Bottom bar is rendered only when `currentDestination` matches `DiscoverKey` or `LibraryKey`.
- `MainActivity.kt` remains untouched.

---

## 4. Execution Slices (2 PRs)

### PR 1: Data Layer & Details Integration
- `LibraryStatus` (`WISHLIST`, `NOT_INTERESTED`), `LibraryGame`
- `GameTrackerTypeConverters` (`PLAN_TO_PLAY` -> `WISHLIST`)
- `PopulatedLibraryGameEntity` & `LibraryDao`
- `LibraryRepository` (parent-first verification)
- `GameDetailsViewModel` 4-flow combine & `EditLibrarySheet`
- Unit tests & connected DAO tests

### PR 2: Library Screen & Bottom Navigation
- `LibraryViewModel`, `LibraryScreen`, `LibraryGameCard` (1–10 badge, tabs, sorting/filter)
- `AppNavHost` Scaffold + `NavigationBar` (Discover / Library top-level only)
- Unit tests & UI checks
