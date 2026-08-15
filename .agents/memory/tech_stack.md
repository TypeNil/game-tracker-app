# Tech Stack Specification — GameTracker (2026)

| Категория | Технология / Библиотека | Назначение |
| :--- | :--- | :--- |
| **Язык** | Kotlin 2.2.10 | Современный нативный Kotlin с новым компилятором K2 |
| **UI Framework** | Jetpack Compose + BOM 2026.02.01 | Декларативный UI с Material 3 |
| **Архитектура** | MVVM / UDF (Unidirectional Data Flow) | Предсказуемый поток состояния и событий |
| **Dependency Injection** | Hilt 2.60.1 + KSP | Compile-time DI со скоупингом жизненного цикла |
| **Локальная БД (SSOT)** | Room 2.6.1 + KTX + KSP | Реактивное хранилище (Flow) с миграциями |
| **Сетевой клиент** | Retrofit 2.11.0 + OkHttp 4.12.0 | HTTP вызовы к Ktor BFF с интерцепторами логирования |
| **Сериализация** | Kotlinx Serialization 1.8.0 | Высокопроизводительный парсинг JSON без reflection |
| **Асинхронность** | Kotlin Coroutines 1.10.1 & Flow | Структурированная многопоточность и реактивные потоки |
| **Загрузка изображений**| Coil 3.1.0 | Асинхронная подгрузка обложек и скриншотов игр |
| **Навигация** | Navigation Compose 2.8.9 | Типобезопасная декларативная навигация между экранами |
| **Тестирование** | JUnit 4, Turbine, MockK, Coroutines Test | Unit-тестирование ViewModel, Repository, DAO |
| **Оптимизация сборки**| R8, ProGuard, Resource Shrinking | Минификация и оптимизация релизного байткода |
