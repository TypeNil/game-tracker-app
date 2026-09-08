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

Приложение отслеживает даты выхода добавленных пользователем игр в фоновом режиме:

1. Откройте вкладку **Настройки** (Settings) в нижнем навигационном баре.
2. Найдите блок **«Фоновые уведомления»** и нажмите кнопку **«Проверить релизы сейчас»**.
3. Метод `triggerImmediateCheck()` немедленно поставит однократную задачу в очередь WorkManager с сетевыми ограничениями (`NetworkType.CONNECTED`) и дедупликацией уведомлений в Room.

Для инспекции состояния задач WorkManager через консоль:
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

### Сценарий 4.2: Room SSOT Recovery (`live` флейвор с реальным BFF)
Сценарий доказывает, что экран восстанавливает данные именно из транзакционного кэша Room SQLite, а не из памяти процесса:
1. Запустите локальный Ktor BFF и приложение `liveDebug`:
   ```bash
   ./gradlew :app:installLiveDebug -PBFF_BASE_URL="http://127.0.0.1:8080/"
   ```
2. Выполните поиск игры (например, `Witcher`) и откройте детальную карточку, чтобы данные сохранились в Room.
3. Остановите локальный сервис Ktor BFF (Ctrl+C) или переведите телефон в Airplane Mode.
4. Принудительно завершите процесс приложения:
   ```bash
   adb shell am force-stop io.github.typenil.gametracker.debug
   ```
5. Запустите приложение повторно:
   ```bash
   adb shell monkey -p io.github.typenil.gametracker.debug -c android.intent.category.LAUNCHER 1
   ```
6. **Результат**: Список игр и карточка восстанавливаются моментально из Room SSOT. В верхнем баре отображается аккуратный статус отсутствия подключения к BFF без сбоев интерфейса и пустых экранов.
---

## 5. Решение проблем с установкой и подписью

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
