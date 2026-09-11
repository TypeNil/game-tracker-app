# Интерактивные сценарии демонстрации (Demo Scenarios)

Пошаговые сценарии для проверки ключевых возможностей GameTracker через утилиту `adb` на подключенном Android-устройстве или эмуляторе.

---

## 1. Детерминированное тестовое уведомление о релизе

Сценарий демонстрирует работу системы локальных уведомлений, каналов уведомлений Android и бесшовного перехода на экран игры через Deep Link.

### Шаг 1: Предоставьте разрешение на показ уведомлений
Начиная с Android 13 (API 33), для показа уведомлений требуется явное разрешение пользователя в рантайме:

```bash
adb shell pm grant io.github.typenil.gametracker.demo.debug android.permission.POST_NOTIFICATIONS
```

### Шаг 2: Отправьте тестовый интент
Вызовите специальный сервисный интент `ACTION_TEST_NOTIFICATION`:

```bash
adb shell am start -W \
  -n io.github.typenil.gametracker.demo.debug/io.github.typenil.gametracker.MainActivity \
  -a io.github.typenil.gametracker.ACTION_TEST_NOTIFICATION \
  --el gameId 1942 \
  --es gameName "The Witcher 3: Wild Hunt"
```

### Шаг 3: Проверьте результат
1. В системной шторке уведомлений Android появится карточка релиза с названием игры и иконкой приложения.
2. Нажмите на уведомление: приложение автоматически откроется и выполнит переход на экран деталей игры (*The Witcher 3*, ID 1942).

---

## 2. Проверка типизированного Deep Link

GameTracker поддерживает прямую навигацию по ссылкам схемы `gametracker://`.

Выполните команду для прямого открытия карточки игры:

```bash
adb shell am start -W \
  -a android.intent.action.VIEW \
  -d "gametracker://game/1942" \
  io.github.typenil.gametracker.demo.debug
```

### Архитектурные особенности:
- Маршрутизация построена на **Type-Safe Navigation Compose (2.8+)**: ссылка десериализуется в строго типизированный объект маршрута `GameDetailsRoute(gameId = 1942L)`.
- Синтетический стек навигации позволяет пользователю нажать стрелку «Назад» в верхнем баре и корректно вернуться в главный каталог, даже если приложение было запущено по ссылке из закрытого состояния.

---

## 3. Фоновая проверка релизов через WorkManager
Приложение в фоне сверяет даты выхода игр из библиотеки. Это **best-effort**: WorkManager не обещает доставку в момент релиза, а переход даты может быть потерян, если details уже обновил UI-кэш до worker или процесс остановился до записи события. Повторные показы дедуплицируются по уникальному `eventKey`, включающему игру, тип события и релевантные даты.

1. Откройте **Настройки** с экрана Discover: кнопка с иконкой информации в верхней панели (это не вкладка нижней навигации).
2. Найдите блок **«Фоновые уведомления»** и нажмите **«Проверить релизы сейчас»**.
3. `triggerImmediateCheck()` поставит однократную задачу WorkManager с ограничением `NetworkType.CONNECTED`.

Для инспекции состояния задач WorkManager:
```bash
adb shell dumpsys jobscheduler | grep -i gametracker
```

---

## 4. Оффлайн-проверки: Demo-автономность и Room SSOT Recovery

### Сценарий 4.1: Автономность `demo` флейвора
1. Установите и запустите demo-сборку (`io.github.typenil.gametracker.demo.debug`).
2. Переведите устройство в «Режим полёта» (Airplane Mode) с отключением Wi-Fi и мобильного интернета.
3. Пройдите по экранам Discover, Поиск, Карточка игры и Библиотека:
   - Встроенные фикстуры и изображения функционируют полностью автономно без обращений к сети.
   - Пользовательские статусы, оценки и заметки сохраняются в локальную базу данных SQLite.

### Сценарий 4.2: Room SSOT Recovery (`live` флейвор с локальным BFF)
Сценарий проверяет, что **поиск и карточка details** восстанавливаются из Room, а не из памяти процесса. Лента For You в этом сценарии не обещается: она memory-only.

1. Запустите локальный Ktor BFF и приложение `liveDebug` по USB (`adb reverse`) — предпочтительный путь, см. [LOCAL_BFF_SETUP.md](LOCAL_BFF_SETUP.md):
   ```bash
   adb reverse tcp:8080 tcp:8080
   ./gradlew :app:installLiveDebug -PBFF_BASE_URL="http://127.0.0.1:8080/"
   ```
2. Выполните поиск игры (например, `Witcher`) и откройте детальную карточку, чтобы данные сохранились в Room.
3. Остановите Ktor BFF (Ctrl+C). Это **не** то же самое, что отсутствие сети у устройства: `NetworkMonitor` смотрит на validated internet у телефона, а не на здоровье BFF. Индикатор offline в приложении при живом Wi-Fi/LTE не появится. Для проверки offline-pill переведите устройство в Airplane Mode.
4. Принудительно завершите процесс:
   ```bash
   adb shell am force-stop io.github.typenil.gametracker.debug
   ```
   `force-stop` не восстанавливает предыдущий экран навигации. Это проверка persistent Room cache, не process-death restoration task state.
5. Запустите приложение повторно:
   ```bash
   adb shell monkey -p io.github.typenil.gametracker.debug -c android.intent.category.LAUNCHER 1
   ```
6. Снова откройте тот же поисковый запрос или карточку. **Результат**: кэшированные search/details читаются из Room. For You после рестарта строится заново и в live без BFF будет пустой или ошибочной, а не «оффлайн-лентой из Room».

---

## 5. Debug-only developer tools

Debug-варианты содержат отдельный экран **Developer tools** в Settings. Он позволяет посмотреть диагностику, заполнить библиотеку детерминированным набором данных и выборочно очистить пользовательское состояние. В подписанные release APK этот компонент не попадает.

Те же операции можно запускать через `adb` по URI `gamertracker://dev/`:

```bash
# Состояние базы и сборки.
adb shell am start -W \
  -a android.intent.action.VIEW \
  -d "gamertracker://dev/state" \
  io.github.typenil.gametracker.demo.debug

# Детерминированный набор для проверки всех статусов.
adb shell am start -W \
  -a android.intent.action.VIEW \
  -d "gamertracker://dev/seed?preset=COVERAGE&count=12&mode=replace" \
  io.github.typenil.gametracker.demo.debug

# Большая библиотека для проверки прокрутки и производительности.
adb shell am start -W \
  -a android.intent.action.VIEW \
  -d "gamertracker://dev/seed?preset=STRESS&count=150&mode=replace" \
  io.github.typenil.gametracker.demo.debug

# Очистить только пользовательскую библиотеку.
adb shell am start -W \
  -a android.intent.action.VIEW \
  -d "gamertracker://dev/wipe?target=LIBRARY" \
  io.github.typenil.gametracker.demo.debug
```

Доступные preset: `REALISTIC`, `COVERAGE`, `EDGE`, `STRESS`, `NOTIFICATIONS`. Для полного сброса используйте `target=ALL`; после успешного сброса приложение перезапускается, чтобы Discover заново построил свои in-memory состояния.

## 6. Решение проблем с установкой и подписью

Если при установке APK возникает ошибка несовпадения сертификатов подписи:
```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ... signatures do not match newer version]
```

Это означает, что на устройстве уже установлена сборка, подписанная другим ключом (например, вашей локальной студией).

Выполните полную чистую переустановку:

```bash
# Вариант А: скачанный GitHub Release.
# v1.0.0 = debug (`io.github.typenil.gametracker.demo.debug`).
# v1.0.1+ = signed demoRelease (`io.github.typenil.gametracker.demo`).
adb uninstall io.github.typenil.gametracker.demo.debug
adb uninstall io.github.typenil.gametracker.demo
adb install -r app-demo.apk

# Вариант Б: локальная debug-сборка из Gradle:
adb uninstall io.github.typenil.gametracker.demo.debug
adb install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk

# Для локальной live-сборки:
adb uninstall io.github.typenil.gametracker.debug
adb install -r app/build/outputs/apk/live/debug/app-live-debug.apk
```
