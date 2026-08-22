# [Navigation 3 & Type-Safe Deep Links] Implementation Plan (Final)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать типобезопасную архитектуру навигации на Navigation Compose 2.8.9 с поддержкой диплинков (`gametracker://game/{id}`), корректного Back behavior при холодном и тёплом старте (synthetic back stack со стартовым `DiscoverKey`), изоляцией фичей по паттерну Key/Entry, сохранением состояния при Activity/Process Recreation и строгим извлечением `gameId` через `toRoute<GameDetailsKey>()`.

**Architecture:** Паттерн Key/Entry (Type-Safe Navigation Compose 2.8.9 + Kotlinx Serialization, ADR-006 / ADR-008). Корневой `AppNavHost` в `:app / app/navigation` связывает изолированные фичи через коллбэки; фичи объявляют `@Serializable` ключи (`*Key`) и билдеры (`*Entry`) в собственных пакетах `feature/<name>/navigation`. `MainActivity` работает в `singleTop` с передачей `onNewIntent` в `navController.handleDeepLink(intent)`.

**Tech Stack:** Kotlin 2.2.10, Jetpack Navigation Compose 2.8.9, Kotlinx Serialization 1.8.0, AndroidX Activity Compose 1.13.0, AndroidX Lifecycle / ViewModel 2.11.0, Hilt 2.60.1, JUnit 4, AndroidX Compose UI Test. Без добавления сторонних библиотек.

**Spec:** Раздел `### 9.3. Navigation 3`, ADR-006 (Type-safe Navigation & Зоны ответственности), ADR-008 (Android Domain/Data Layer, Navigation Key/Entry, and Offline Demo Standards).

---

## Global Constraints & Back Behavior Contract

1. **Back Behavior Contract**:
   - **Cold-Start Deep Link**: `VIEW gametracker://game/{id}` при закрытом приложении $\rightarrow$ открывается экран `GameDetails(id)` (без BottomBar). Первое нажатие Back $\rightarrow$ стартовый экран `Discover` (с BottomBar). Второе нажатие Back $\rightarrow$ выход из Activity (finish).
   - **In-App Transitions**: `Discover / Library / Search` $\rightarrow$ `Details` $\rightarrow$ Back возвращает пользователя на исходный экран (не подменяя его принудительно на Discover).
   - **Similar Games Stacking**: `Details(A) -> Details(B) -> Details(C)` без `launchSingleTop` — каждый Back снимает ровно один экран деталей.
2. **Launch & Intent Handling**:
   - `MainActivity` имеет `android:launchMode="singleTop"`.
   - `MainActivity.onNewIntent(intent)` обновляет интент через `setIntent(intent)` и уведомляет `NavController` через `addOnNewIntentListener` в `AppNavHost`.
3. **Type-Safe SavedStateHandle**:
   - `GameDetailsViewModel` извлекает `val gameId: Long = savedStateHandle.toRoute<GameDetailsKey>().gameId` без `runCatching` и без ручных fallback-конструкций.
   - `SearchViewModel` хранит в `SavedStateHandle` только `query: String` (пользовательский input, не доменные сущности).
4. **State & Activity Recreation**:
   - При пересоздании Activity (`scenario.recreate()` / поворот экрана / config change) стек навигации и выбранная вкладка сохраняются через механизм Navigation Compose (`saveState = true`, `restoreState = true`).

---

## Пошаговый план реализации (Tasks)

### Task 1: Manifest, Intent Handling и Deep Link Entry

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/io/github/typenil/gametracker/MainActivity.kt`
- Modify: `app/src/main/java/io/github/typenil/gametracker/feature/details/navigation/GameDetailsEntry.kt`

**Interfaces:**
- Consumes: `@Serializable data class GameDetailsKey(val gameId: Long)`
- Produces: `navDeepLink<GameDetailsKey>(basePath = "gametracker://game")`, `launchMode="singleTop"`, `onNewIntent` support.

- [ ] **Step 1: Настроить `AndroidManifest.xml`**

Добавить `android:launchMode="singleTop"` и intent-filter схемы `gametracker`:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:launchMode="singleTop"
    android:theme="@style/Theme.App.Starting"
    android:windowSoftInputMode="adjustResize">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Deep Link: gametracker://game/{gameId} -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="gametracker"
            android:host="game" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Добавить `onNewIntent` в `MainActivity.kt`**

```kotlin
package io.github.typenil.gametracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GameTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
```

- [ ] **Step 3: Зарегистрировать deepLink в `GameDetailsEntry.kt`**

```kotlin
fun NavGraphBuilder.gameDetailsEntry(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    composable<GameDetailsKey>(
        deepLinks = listOf(
            navDeepLink<GameDetailsKey>(
                basePath = "gametracker://game"
            )
        )
    ) {
        GameDetailsRoute(
            onGameClick = onGameClick,
            onBackClick = onBackClick
        )
    }
}
```

- [ ] **Step 4: Проверить компиляцию и detekt**

Команда: `cmd /c "gradlew.bat :app:compileDemoDebugKotlin :app:detekt"`

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/io/github/typenil/gametracker/MainActivity.kt app/src/main/java/io/github/typenil/gametracker/feature/details/navigation/GameDetailsEntry.kt
git commit -m "feat(navigation): add singleTop, onNewIntent, and gametracker://game deep link registration"
```

---

### Task 2: Type-Safe SavedStateHandle в GameDetailsViewModel

**Files:**
- Modify: `app/src/main/java/io/github/typenil/gametracker/feature/details/GameDetailsViewModel.kt`
- Modify: `app/src/test/java/io/github/typenil/gametracker/feature/details/GameDetailsViewModelTest.kt`

**Interfaces:**
- Consumes: `savedStateHandle.toRoute<GameDetailsKey>()`
- Produces: `val gameId: Long`

- [ ] **Step 1: Обновить извлечение `gameId` в `GameDetailsViewModel.kt`**

Заменить ручной `get<Long>` на типобезопасный `toRoute<GameDetailsKey>()`:

```kotlin
@HiltViewModel
class GameDetailsViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val gameId: Long = savedStateHandle.toRoute<GameDetailsKey>().gameId
```

- [ ] **Step 2: Обновить unit-тесты `GameDetailsViewModelTest.kt`**

Убедиться, что фикстура `SavedStateHandle` инициализируется через `mapOf("gameId" to 1942L)`:

```kotlin
private fun createViewModel(
    gameId: Long = 1942L,
    gameRepo: GameRepository = fakeGameRepository,
    libraryRepo: LibraryRepository = fakeLibraryRepository
): GameDetailsViewModel {
    return GameDetailsViewModel(
        gameRepository = gameRepo,
        libraryRepository = libraryRepo,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId))
    )
}
```

- [ ] **Step 3: Запустить unit-тесты `GameDetailsViewModelTest`**

Команда: `cmd /c "gradlew.bat :app:testDemoDebugUnitTest --tests io.github.typenil.gametracker.feature.details.GameDetailsViewModelTest"`

- [ ] **Step 4: Коммит**

```bash
git add app/src/main/java/io/github/typenil/gametracker/feature/details/GameDetailsViewModel.kt app/src/test/java/io/github/typenil/gametracker/feature/details/GameDetailsViewModelTest.kt
git commit -m "refactor(details): use type-safe toRoute<GameDetailsKey>() in GameDetailsViewModel"
```

---

### Task 3: GameTrackerAppState, NewIntent Listener и координация NavHost

**Files:**
- Modify: `app/src/main/java/io/github/typenil/gametracker/navigation/AppBackStack.kt`
- Modify: `app/src/main/java/io/github/typenil/gametracker/navigation/AppNavigation.kt`

**Interfaces:**
- Consumes: `NavHostController`, `ComponentActivity.addOnNewIntentListener`
- Produces: `GameTrackerAppState` (ADR-006), автоматическая обработка intent без пересоздания контроллера

- [ ] **Step 1: Реализовать `GameTrackerAppState` в `AppBackStack.kt`**

Удалить устаревший неиспользуемый `AppNavigationState`, заменив его на `GameTrackerAppState`:

```kotlin
package io.github.typenil.gametracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.typenil.gametracker.feature.details.navigation.navigateToGameDetails
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.feature.search.navigation.navigateToSearch

/**
 * State holder managing top-level navigation, tab switching, and back stack state (ADR-006).
 */
@Stable
class GameTrackerAppState(
    val navController: NavHostController
) {
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val isTopLevelDestination: Boolean
        @Composable get() {
            val destination = currentDestination
            return destination?.hasRoute<DiscoverKey>() == true ||
                destination?.hasRoute<LibraryKey>() == true
        }

    fun navigateToDiscover() {
        navController.navigate(DiscoverKey) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToLibrary() {
        navController.navigate(LibraryKey) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToSearch() {
        navController.navigateToSearch()
    }

    fun navigateToGameDetails(gameId: Long) {
        navController.navigateToGameDetails(gameId = gameId)
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberGameTrackerAppState(
    navController: NavHostController = rememberNavController()
): GameTrackerAppState {
    return remember(navController) {
        GameTrackerAppState(navController = navController)
    }
}
```

- [ ] **Step 2: Обновить `AppNavHost.kt` с поддержкой `GameTrackerAppState` и `addOnNewIntentListener`**

```kotlin
package io.github.typenil.gametracker.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.feature.details.navigation.gameDetailsEntry
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.discover.navigation.discoverEntry
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.feature.library.navigation.libraryEntry
import io.github.typenil.gametracker.feature.search.navigation.searchEntry

/**
 * Root Navigation Host coordinating destinations, bottom navigation, deep links, and transitions.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    appState: GameTrackerAppState = rememberGameTrackerAppState()
) {
    val activity = LocalActivity.current ?: (LocalContext.current as? ComponentActivity)
    DisposableEffect(activity, appState.navController) {
        val listener = Consumer<Intent> { intent ->
            appState.navController.handleDeepLink(intent)
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    val isTopLevelDestination = appState.isTopLevelDestination
    val currentDestination = appState.currentDestination

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<DiscoverKey>() == true,
                        onClick = appState::navigateToDiscover,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = stringResource(R.string.nav_discover)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_discover)) }
                    )

                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<LibraryKey>() == true,
                        onClick = appState::navigateToLibrary,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CollectionsBookmark,
                                contentDescription = stringResource(R.string.nav_library)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_library)) }
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = appState.navController,
            startDestination = DiscoverKey,
            modifier = Modifier.padding(innerPadding)
        ) {
            discoverEntry(
                onGameClick = appState::navigateToGameDetails,
                onSearchClick = appState::navigateToSearch
            )

            libraryEntry(
                onGameClick = appState::navigateToGameDetails,
                onNavigateToDiscover = appState::navigateToDiscover
            )

            searchEntry(
                onGameClick = appState::navigateToGameDetails,
                onBackClick = appState::navigateBack
            )

            gameDetailsEntry(
                onGameClick = appState::navigateToGameDetails,
                onBackClick = appState::navigateBack
            )
        }
    }
}
```

- [ ] **Step 3: Запустить unit-тесты и проверку detekt**

Команда: `cmd /c "gradlew.bat :app:testDemoDebugUnitTest detekt"`

- [ ] **Step 4: Коммит**

```bash
git add app/src/main/java/io/github/typenil/gametracker/navigation/
git commit -m "refactor(navigation): integrate GameTrackerAppState and OnNewIntent listener"
```

---

### Task 4: Instrumented Tests для Deep Links, Back Behavior и Recreation

**Files:**
- Create: `app/src/androidTest/java/io/github/typenil/gametracker/navigation/DeepLinkNavigationTest.kt`

**Interfaces:**
- Consumes: `ActivityScenario.launch<MainActivity>(intent)`
- Produces: Доказательство cold-start диплинка (`gametracker://game/1942`), synthetic back stack, и выживания стейта при `scenario.recreate()`.

- [ ] **Step 1: Написать `DeepLinkNavigationTest.kt` (с учетом множественных нод заголовка и строковых ресурсов)**

```kotlin
package io.github.typenil.gametracker.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.MainActivity
import io.github.typenil.gametracker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkNavigationTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun coldStartDeepLink_opensGameDetails_andBackReturnsToDiscover() {
        val targetGameId = 1942L
        val targetTitle = "The Witcher 3: Wild Hunt"
        val discoverLabel = context.getString(R.string.nav_discover)
        val deepLinkUri = Uri.parse("gametracker://game/$targetGameId")

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setClass(context, MainActivity::class.java)
            setPackage(BuildConfig.APPLICATION_ID)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // 1. Assert: Details screen is rendered with title from fixture (The Witcher 3: Wild Hunt)
            // Title exists in TopAppBar and in Header; pick first matching node to avoid ambiguity.
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText(targetTitle)[0].assertIsDisplayed()

            // 2. Assert: Bottom navigation bar is hidden on sub-screen
            composeTestRule.onNodeWithContentDescription(discoverLabel).assertDoesNotExist()

            // 3. Press Back -> Synthetic back stack returns to Discover
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            // 4. Assert: Discover screen and bottom navigation bar are displayed
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithContentDescription(discoverLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription(discoverLabel).assertIsDisplayed()
        }
    }

    @Test
    fun deepLinkDetails_survivesActivityRecreation() {
        val targetGameId = 1942L
        val targetTitle = "The Witcher 3: Wild Hunt"
        val deepLinkUri = Uri.parse("gametracker://game/$targetGameId")

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setClass(context, MainActivity::class.java)
            setPackage(BuildConfig.APPLICATION_ID)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            }

            // Recreate activity (simulating config change / orientation change)
            scenario.recreate()

            // Assert: Game Details is still the active destination
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText(targetTitle)[0].assertIsDisplayed()
        }
    }

    @Test
    fun libraryTab_survivesActivityRecreation() {
        val libraryNavLabel = context.getString(R.string.nav_library)
        val libraryTitle = context.getString(R.string.library_title)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Switch to Library tab
            composeTestRule.onNodeWithContentDescription(libraryNavLabel).performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(libraryTitle).fetchSemanticsNodes().isNotEmpty()
            }

            // Recreate activity
            scenario.recreate()

            // Assert: Library tab is still selected
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(libraryTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText(libraryTitle)[0].assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Запустить компиляцию androidTest и проверку detekt**

Команда: `cmd /c "gradlew.bat :app:compileDemoDebugAndroidTestKotlin :app:detekt"`

- [ ] **Step 3: Коммит**

```bash
git add app/src/androidTest/java/io/github/typenil/gametracker/navigation/DeepLinkNavigationTest.kt
git commit -m "test(navigation): add instrumented tests for deep links, synthetic back stack, and activity recreation"
```

---

### Task 5: Верификация и закрытие чек-листа

**Files:**
- Modify: `.agents/memory/roadmap.md`

- [ ] **Step 1: Запустить полный набор локальных проверок**

Команды:
1. `cmd /c "gradlew.bat :backend:test :app:testDemoDebugUnitTest detekt"`
2. `cmd /c "gradlew.bat assembleDemoDebug assembleLiveDebug"`
3. `cmd /c "gradlew.bat :app:connectedDemoDebugAndroidTest --tests io.github.typenil.gametracker.navigation.DeepLinkNavigationTest"` *(при наличии подключенного эмулятора)*

- [ ] **Step 2: Верификация через ADB (при наличии подключенного устройства/эмулятора)**

```bash
# Холодный старт через диплинк на demoDebug сборке
adb shell am start -W -a android.intent.action.VIEW -d "gametracker://game/1942" io.github.typenil.gametracker.demo.debug
```

- [ ] **Step 3: Обновление Roadmap**

Добавить в `.agents/memory/roadmap.md` в секцию Phase 4:
`- [x] Type-Safe Navigation & Deep Links: gametracker://game/{id}, singleTop, onNewIntent, synthetic back stack, recreation support.`

- [ ] **Step 4: Коммит**

```bash
git add .agents/memory/roadmap.md
git commit -m "docs(roadmap): update roadmap with navigation 3 deep links completion"
```

---

## Reviewer Acceptance Criteria (Критерии приёмки)

- [ ] **Root back stack**: `GameTrackerAppState` изолирован в `app/navigation/`, `NavController` не передаётся во `ViewModel` и UI-экраны.
- [ ] **Feature keys/entries**: фичи объявляют `@Serializable` `*Key` и `*Entry` в своих пакетах.
- [ ] **`GameDetailsKey(gameId)`**: типобезопасный класс с поддержкой стека similar-игр.
- [ ] **Deep Link `gametracker://game/{id}`**: зарегистрирован в манифесте и `GameDetailsEntry.kt`.
- [ ] **Cold-Start Deep Link**: открывает `GameDetails(1942)`, первое нажатие Back ведет на `DiscoverKey`, второе — завершает Activity.
- [ ] **Back Behavior**: корректный pop по одному экрану для цепочки similar-игр и сохранение исходного экрана для in-app переходов.
- [ ] **Process / Activity Recreation**: `scenario.recreate()` подтверждает сохранность активного экрана (Details и Library tab).
- [ ] **SavedStateHandle hygiene**: `GameDetailsViewModel` извлекает `gameId` через `toRoute<GameDetailsKey>()`, `SearchViewModel` держит только `query`.
