# GameTracker Development Roadmap

## Phase 1: Базовая настройка и фундамент проекта [IN PROGRESS]
- [x] Настройка `libs.versions.toml`, Gradle Kotlin DSL, Java 17, KSP, Hilt, Room, Compose, Retrofit.
- [x] Настройка Build Variants (`demo` / `live`, `debug` / `release`) и R8 ProGuard rules.
- [x] Создание структуры памяти агентов (`.agents/memory/`, `.agents/rules/`) и Magnum Opus руководства.
- [x] Инициализация локального Git-репозитория и production `.gitignore`.
- [x] Настройка Application-класса с Hilt и базового модуля Coroutine Dispatchers.

## Phase 2: Core Domain & Data Layer (SSOT)
- [ ] Определение чистых моделей домена в `core/model`: `Game`, `GameDetails`, `Genre`, `Platform`, `ReleaseSchedule`.
- [ ] Настройка Room в `core/database`: сущности (`GameEntity`), `GameDao`, `GameTrackerDatabase`.
- [ ] Определение сетевых DTO и контрактов API в `core/network` (`BffApiService`, `GameDto`).
- [ ] Реализация `FakeRemoteDataSource` с реалистичными JSON-фикстурами для `demo` сборки.
- [ ] Реализация `GameRepository` в `core/data` с координацией Room SSOT + Remote Sync.
- [ ] Unit-тесты для Room DAO и Repository с использованием Turbine и MockK/Fake.

## Phase 3: Design System & Navigation Root
- [ ] UI Theme (Цветовые токены, типографика Material 3, кастомные формы карточек) в `core/designsystem`.
- [ ] Базовые UI-компоненты: `GameCard`, `RatingBadge`, `SearchBar`, `StateViews` (Loading, Error, Empty).
- [ ] Корневой Scaffold с BottomNavigationBar и Compose Navigation маршрутизацией.

## Phase 4: Feature Implementations
- [ ] **Feature Search & Discover**: экран поиска, фильтрация по жанрам, пагинация.
- [ ] **Feature Game Details**: детальная страница игры, скриншоты (Coil), похожие игры, смена статуса в библиотеке.
- [ ] **Feature User Library**: вкладки ("Играю", "В планах", "Пройдено"), сортировка, быстрые действия.
- [ ] **Feature Releases & Calendar**: календарь релизов, локальные уведомления через WorkManager.

## Phase 5: Backend for Frontend (Ktor BFF)
- [ ] Инициализация подпроекта `./backend` на Kotlin/Ktor.
- [ ] Реализация OAuth2 авторизации в IGDB API и кэширования ответов.
- [ ] Эндпоинты `/api/games/search`, `/api/games/popular`, `/api/games/{id}`.

## Phase 6: Quality, Polish & Release
- [ ] Unit & UI тесты (покрытие основных сценариев).
- [ ] Проверка сборки `demoRelease` с R8 минификацией.
- [ ] Подготовка README.md для портфолио с гифками, архитектурными схемами и бейджами.
