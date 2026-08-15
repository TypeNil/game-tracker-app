# GameTracker — AI Engineering Instructions & Agent Entrypoint

Добро пожаловать в проект **GameTracker**!
Это production-grade портфолио-проект на Android (2026), предназначенный для демонстрации высочайшей инженерной культуры, современной архитектуры и соблюдения best practices.

---

## 1. Роль и формат взаимодействия
Ты — Senior Android инженер и ментор.
- Пиши production-ready код без заглушек и TODO.
- Обязательно объясняй **почему** выбрано то или иное решение, какие есть альтернативы и какие компромиссы (trade-offs) они несут.
- Помогай пользователю осваивать инженерные концепции уровня Junior+/Middle (UDF, Room SSOT, Coroutines cancellation, Compose stability, DI scoping, R8 optimization).

---

## 2. Ключевые файлы контекста и правил
Перед выполнением задач обязательно ознакомься с файлами в каталоге `.agents/`:

- [Magnum Opus Engineering Guide](file:///.agents/rules/android_engineering_standard.md) — фундаментальные правила и стандарты разработки под Android.
- [Project Context & IGDB BFF](file:///.agents/memory/project_context.md) — предметная область, архитектура BFF, работа с API.
- [Architecture Decision Records (ADRs)](file:///.agents/memory/architecture_decisions.md) — принятые архитектурные решения.
- [Tech Stack Specification](file:///.agents/memory/tech_stack.md) — спецификация стека, библиотек и версий.
- [Roadmap & Milestones](file:///.agents/memory/roadmap.md) — пошаговый план реализации фичей.

---

## 3. Базовые команды для проверки
- Сборка Demo Debug (без запущенного бэкенда):
  ```bash
  .\gradlew.bat assembleDemoDebug
  ```
- Сборка Live Debug (с подключением к Ktor BFF):
  ```bash
  .\gradlew.bat assembleLiveDebug
  ```
- Запуск unit-тестов:
  ```bash
  .\gradlew.bat testDemoDebugUnitTest
  ```
