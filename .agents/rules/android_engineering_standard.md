# Android Engineering Standard & Magnum Opus Guide (2026 Edition)
> **Статус документа:** Фундаментальное руководство для AI-агентов и инженеров проекта GameTracker.
> **Цель:** Обеспечение наивысшего качества кода, архитектурной строгости, понимания внутренних механизмов Android/Kotlin рантайма и менторской поддержки уровня Junior+/Middle.

---

## 1. Архитектурная философия и Pragmatic Clean Architecture

### 1.1. Главные принципы
1. **Separation of Concerns (Разделение ответственности)**:
   - **UI Layer (Presentation)**: Отображает данные и отправляет пользовательские намерения (Intents/Events). Не содержит бизнес-логики и прямых обращений к базам данных/сети.
   - **Domain Layer (Бизнес-логика)**: Опциональный слой. UseCase создается **только** тогда, когда есть реальная переиспользуемая бизнес-логика (например, сложный расчет рейтинга, валидация) или оркестрация нескольких репозиториев. Мы **не создаем** однострочные `GetGamesUseCase`, которые просто вызывают `repository.getGames()` (это cargo-cult boilerplate).
   - **Data Layer (Данные)**: Управляет источниками данных. Репозиторий является единственной точкой входа для внешнего мира к этим данным.

2. **Single Source of Truth (SSOT)**:
   - Локальная база данных (Room) является единственным источником правды для кэшируемых экранов.
   - Сетевой слой (Remote DataSource) обновляет базу данных, а UI подписывается на реактивный поток данных (`Flow`) из базы данных.
   - **Почему?** Это исключает состояние "двух истин", когда локальное состояние экрана расходится с данными в БД или данными на бэкенде после повторного открытия приложения.

3. **Unidirectional Data Flow (UDF)**:
   - Состояние (**State**) течет **вниз**: от ViewModel к Composable-функциям.
   - События (**Events**) текут **вверх**: от Composable к ViewModel.

```
       ┌────────────────────────┐
       │       ViewModel        │ ◄─── State Container
       └──────┬──────────▲──────┘
              │          │
        State │ (Flow)   │ Event (Callback)
              ▼          │
       ┌─────────────────┴──────┐
       │   Composable Screen    │ ◄─── Pure Render
       └────────────────────────┘
```

---

## 2. Jetpack Compose Mastery & State Management

### 2.1. Внутреннее устройство Compose & Recomposition
Compose трансформирует данные в UI дерево через 3 фазы:
1. **Composition (Что показывать)**: Выполняются composable-функции, строится Slot Table (внутреннее представление дерева UI).
2. **Layout (Где показывать)**: Измерение (Measurement) и размещение (Placement) компонентов.
3. **Draw (Как рисовать)**: Отрисовка пикселей на `Canvas`.

> [!IMPORTANT]
> **Золотое правило производительности:** Откладывайте чтение состояния (State Read) на максимально позднюю фазу (Layout или Draw) с помощью лямбд (`Modifier.offset { ... }`, `Modifier.drawBehind { ... }`), чтобы при изменении состояния пропускать фазу Composition целиком!

### 2.2. Стабильность (Stability) и Strong Skipping Mode (SSM)
Начиная с Kotlin 2.0.20+, включен **Strong Skipping Mode**:
- Все перезапускаемые (restartable) Composable-функции становятся пропускаемыми (skippable), даже если у них есть нестабильные параметры.
- Для нестабильных параметров сравнение идет по ссылке (`===`), а для стабильных — по значению (`equals`).

**Однако стабильность остается контрактом:**
- Если вы передаете стандартный `List<T>`, компилятор считает его нестабильным (так как `List` в Kotlin — интерфейс, за которым может скрываться мутабельный `ArrayList`).
- Создание нового экземпляра списка при каждой рекомпозиции приведет к тому, что `===` вернет `false`, и Composable будет рекомпозироваться заново.

**Решения:**
1. Оборачивать UI-модели в `@Immutable` или `@Stable` data-классы.
2. Использовать `ImmutableList<T>` из `kotlinx.collections.immutable` или `@Immutable data class UiState(val items: List<Item>)`.
3. Всегда указывать стабильный `key` в списках:
   ```kotlin
   LazyColumn {
       items(
           items = games,
           key = { game -> game.id } // ОБЯЗАТЕЛЬНО: стабильный ключ предотвращает лишние перерисовки
       ) { game ->
           GameCard(game = game)
       }
   }
   ```

### 2.3. Инструменты работы с состоянием
- **`derivedStateOf`**: Используется, когда состояние меняется очень часто (например, пиксели скролла `firstVisibleItemIndex`), но UI должен реагировать только на превышение определенного порога:
  ```kotlin
  val showScrollToTop by remember {
      derivedStateOf { listState.firstVisibleItemIndex > 5 }
  }
  ```
- **`rememberUpdatedState`**: Используется внутри эффектов (`LaunchedEffect`), чтобы зафиксировать актуальное значение колбэка без перезапуска самого эффекта:
  ```kotlin
  @Composable
  fun LandingScreen(onTimeout: () -> Unit) {
      val currentOnTimeout by rememberUpdatedState(onTimeout)
      LaunchedEffect(Unit) {
          delay(3000)
          currentOnTimeout() // Вызовет актуальную лямбду, даже если родитель рекомпозировался
      }
  }
  ```

---

## 3. Kotlin Coroutines & Reactive Flow

### 3.1. Structured Concurrency (Структурированная многопоточность)
Корутины не должны "висеть в воздухе" (`GlobalScope` строго запрещен). Каждая корутина привязана к `CoroutineScope` с иерархией `Job`.

- **ViewModelScope**: Привязан к жизненному циклу `ViewModel`. При вызове `onCleared()` все дочерние корутины автоматически отменяются.
- **Инжекция Dispatchers**: Никогда не хардкодьте `Dispatchers.IO` внутри классов репозиториев или юзкейсов. Инжектируйте `@IoDispatcher CoroutineDispatcher`. Это необходимо для подмены диспетчера на `StandardTestDispatcher` / `UnconfinedTestDispatcher` в unit-тестах.

### 3.2. Lifecycle-Aware Flow Collection
В UI слое всегда используйте `collectAsStateWithLifecycle()`:
```kotlin
@Composable
fun SearchRoute(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(uiState = uiState, onAction = viewModel::onAction)
}
```
**Почему не обычный `collectAsState()`?**
`collectAsState()` слушает поток даже тогда, когда приложение свернуто (Activity в `STOPPED`). Это приводит к напрасному расходу батареи, трафика и потенциальным утечкам ресурсов. `collectAsStateWithLifecycle()` автоматически останавливает подписку, когда жизненный цикл падает ниже `Lifecycle.State.STARTED`.

### 3.3. Паттерн `stateIn(SharingStarted.WhileSubscribed(5_000))`
Во `ViewModel` холодные потоки из репозитория трансформируются в горячий `StateFlow`:
```kotlin
val uiState: StateFlow<SearchUiState> = repository.getGamesFlow()
    .map { games -> SearchUiState.Success(games) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState.Loading
    )
```
**Почему 5000 мс?**
При повороте экрана (Configuration Change) Activity пересоздается. Предыдущая Activity отписывается (`STOPPED` -> `DESTROYED`), а новая подписывается заново через несколько миллисекунд. Задержка в 5 секунд предотвращает перезапуск запроса к БД/сети во время этой ротации.

### 3.4. Опасность `runCatching` и CancellationException
> [!CAUTION]
> Стандартный `runCatching { ... }` в Kotlin ловит **все** `Throwable`, включая `CancellationException`!
> Если корутина отменяется, `CancellationException` перехватывается, корутина считает, что произошла ошибка, и ломает механизм Structured Concurrency.

**Правильный подход:**
```kotlin
inline fun <T, R> T.runSuspendCatching(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e // Пробрасываем отмену дальше!
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
```

---

## 4. Room Database & Offline-First SSOT

### 4.1. Реактивные запросы (Flow Queries)
DAO методы, возвращающие данные для UI, должны возвращать `Flow<List<Entity>>`:
```kotlin
@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE isFavorite = 1")
    fun getFavoriteGamesFlow(): Flow<List<GameEntity>>

    @Upsert
    suspend fun upsertGames(games: List<GameEntity>)
}
```
Room под капотом использует механизм `InvalidationTracker`. Когда таблица `games` изменяется через `@Insert` или `@Upsert`, Room автоматически триггерит повторный запрос и эмитит свежий список в `Flow`.

### 4.2. Связи, внешние ключи и индексы
Всегда индексируйте внешние ключи (`ForeignKey`), иначе SQLite будет выполнять full table scan при проверке каскадных обновлений/удалений:
```kotlin
@Entity(
    tableName = "screenshots",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["gameId"])] // КРИТИЧНО ДЛЯ ПРОИЗВОДИТЕЛЬНОСТИ
)
data class ScreenshotEntity(
    @PrimaryKey val id: Long,
    val gameId: Long,
    val imageUrl: String
)
```

---

## 5. Dependency Injection с Hilt

### 5.1. Правила скоупинга и связывания
1. **`@Binds` вместо `@Provides`**: Когда нужно связать интерфейс с реализацией, всегда используйте абстрактный `@Binds` метод в абстрактном модуле. Это позволяет Dagger генерировать более компактный код без лишних фабрик создания инстансов:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   abstract class RepositoryModule {
       @Binds
       @Singleton
       abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository
   }
   ```
2. **Не злоупотребляйте `@Singleton`**: Помещайте в `@Singleton` только те объекты, которые действительно хранят разделяемое состояние приложения (например, `RoomDatabase`, `OkHttpClient`, `DataStore`). Бесстейтовые классы создаются быстро и не требуют удержания в памяти на протяжении всей жизни процесса.
3. **Fakes в тестах вместо Mocking**: Создавайте `FakeGameRepository : GameRepository` с `MutableStateFlow` внутри для тестирования ViewModel. Это надежнее, читаемее и не зависит от внутренних вызовов методов, в отличие от тяжелых mock-библиотек.

---

## 6. Сетевой слой, BFF и обработка ошибок

### 6.1. Разделение моделей: DTO vs Entity vs Domain UI Model
- **DTO (`core/network`)**: Точно отражает контракт JSON ответа BFF (`@Serializable data class GameDto(...)`).
- **Entity (`core/database`)**: Отражает структуру таблиц в SQLite (`@Entity data class GameEntity(...)`).
- **Domain Model (`core/model`)**: Чистые неизменяемые классы, используемые бизнес-логикой и UI (`data class Game(...)`).

**Почему нельзя использовать DTO напрямую в UI?**
1. Если бэкенд изменит формат поля или типы данных, придется переписывать весь UI.
2. В DTO часто присутствуют лишние служебные поля или nullable-структуры, которые загромождают код Compose.
3. Маппер (`toDomain()`, `toEntity()`) изолирует изменения на границе слоя.

### 6.2. Моделирование ошибок и событий
- Ошибки представляются через явный `AppError` / `Result` sealed interface:
  ```kotlin
  sealed interface AppResult<out T> {
      data class Success<out T>(val data: T) : AppResult<T>
      data class Error(val error: AppError) : AppResult<Nothing>
  }
  ```
- Одноразовые события (показ Snackbar, навигация) моделируются через явные `UiEvent` или сброс состояния после обработки (`onMessageShown()`), а не через случайные nullable-поля.

---

## 7. Протокол менторства: как AI объясняет код разработчику

Каждый раз, предлагая архитектурное решение, модуль или алгоритм, агент должен придерживаться следующего менторского формата:

1. **Суть решения**: Что именно мы делаем и какой компонент пишем.
2. **Почему именно так (The "Why")**: Какую инженерную проблему это решает (производительность, утечка памяти, race condition, тестируемость).
3. **Альтернативы и компромиссы (Trade-offs)**:
   - *Альтернатива А*: например, использовать `LiveData` вместо `StateFlow` (почему устарело).
   - *Альтернатива Б*: использовать `Ktor Client` вместо `Retrofit` (в чем плюсы/минусы).
4. **Что происходит под капотом**: Механика компилятора Kotlin, Compose runtime или Android OS.
5. **Интервью-инсайт**: Как этот вопрос звучит на собеседованиях на Junior+/Middle грейд и как на него отвечать.
