# Руководство по локальному запуску Ktor BFF

Руководство по настройке и запуску локального Backend-for-Frontend (`:backend`) и подключению к нему мобильного приложения GameTracker (`live` флейвор).

---

## 1. Зачем нужен BFF?

API IGDB (Twitch Developer Portal) требует для работы авторизацию OAuth2 (Client Credentials Flow) с парой ключей:
- `Client-ID`
- `Client-Secret`

Хранить эти ключи внутри мобильного APK **категорически небезопасно**: декомпиляция APK позволяет злоумышленникам извлечь секреты и исчерпать квоты вашего аккаунта. Сервис `:backend` на Kotlin/Ktor выступает изолированным шлюзом:
- Хранит секреты на сервере;
- Управляет жизненным циклом токенов OAuth2;
- Защищает квоты IGDB (строгий лимит 4 req/s);
- Кэширует повторяющиеся запросы в оперативной памяти (Caffeine).

---

## 2. Получение ключей IGDB

1. Перейдите в [Twitch Developer Console](https://dev.twitch.tv/console/apps).
2. Зарегистрируйте приложение:
   - **Name**: `GameTracker-Dev` (или любое уникальное имя).
   - **OAuth Redirect URLs**: `http://localhost`.
   - **Category**: `Application Integration`.
3. Создайте приложение и нажмите **Manage**.
4. Скопируйте **Client ID** и сгенерируйте новый **Client Secret**.

---

## 3. Запуск сервиса Ktor BFF

### Linux / macOS (Bash / Zsh)

```bash
export IGDB_CLIENT_ID="ваш_client_id"
export IGDB_CLIENT_SECRET="ваш_client_secret"

./gradlew :backend:run
```

### Windows (PowerShell)

```powershell
$env:IGDB_CLIENT_ID = "ваш_client_id"
$env:IGDB_CLIENT_SECRET = "ваш_client_secret"

.\gradlew.bat :backend:run
```

### Проверка работоспособности

Сервис запускается на порту `8080`. Откройте терминал и выполните запрос к эндпоинту `/health`:

```bash
curl http://127.0.0.1:8080/health
```

Ожидаемый ответ:
```json
{
  "status": "UP",
  "service": "GameTracker-BFF",
  "version": "1.0.0",
  "timestamp": 1741300000000
}
```

### Мониторинг кэша и лимиты памяти (/health/cache)

Кэширование ответов IGDB реализовано на базе Caffeine (`BffCache`). Каждый регион кэша ограничен лимитом в 1 000 записей (`MAX_CACHE_SIZE`) и управляется политикой TTL (`CachePolicy`):
- `POPULAR`: 60 минут (списки популярных игр)
- `SEARCH`: 15 минут (поисковые запросы)
- `GAME_DETAILS`: 120 минут (детальные данные об игре)
- `RECOMMEND`: 15 минут (кандидаты персональных рекомендаций)

Лимит задан строго по количеству записей (entry-count limit). Фактический объем heap зависит от структуры ответов IGDB и размера графа объектов, поэтому он не декларируется фиктивными статическими оценками, а замеряется эмпирически под максимальной нагрузкой.

Для инспекции текущего размера регионов кэша, счетчиков попаданий, промахов и вытеснений выполните:

```bash
curl http://127.0.0.1:8080/health/cache
```

Ожидаемый ответ:
```json
{
  "regions": [
    {
      "policy": "POPULAR",
      "estimatedSize": 1,
      "hitCount": 10,
      "missCount": 1,
      "evictionCount": 0
    },
    {
      "policy": "SEARCH",
      "estimatedSize": 4,
      "hitCount": 15,
      "missCount": 4,
      "evictionCount": 0
    },
    {
      "policy": "GAME_DETAILS",
      "estimatedSize": 12,
      "hitCount": 45,
      "missCount": 12,
      "evictionCount": 0
    },
    {
      "policy": "RECOMMEND",
      "estimatedSize": 2,
      "hitCount": 8,
      "missCount": 2,
      "evictionCount": 0
    }
  ]
}
```

---

## 4. Сетевая топология и привязка адресов (Host Binding)

По умолчанию конфигурация `ServerConfig` привязывает сокет к адресу **`0.0.0.0:8080`**:
- Это позволяет сервису принимать соединения как с локального компьютера (`127.0.0.1`), так и от внешних устройств в вашей локальной сети (по LAN IP компьютера, например `192.168.1.50`).

### Безопасность и запуск строго на loopback
Если вы работаете в публичной или недоверенной сети Wi-Fi, ограничьте привязку сокета только адресом `127.0.0.1`:

- **POSIX**:
  ```bash
  HOST=127.0.0.1 ./gradlew :backend:run
  ```
- **Windows PowerShell**:
  ```powershell
  $env:HOST = "127.0.0.1"
  .\gradlew.bat :backend:run
  ```

---

## 5. Подключение Android-приложения (`live` флейвор)

В зависимости от целевого устройства выберите соответствующий способ сетевого взаимодействия:

### Сценарий А: Официальный эмулятор Android Studio (`10.0.2.2`)

Эмулятор QEMU работает в изолированной виртуальной сети:
- `127.0.0.1` внутри эмулятора — это *сам эмулятор*, а не хостовый компьютер.
- `10.0.2.2` — специальный виртуальный псевдоним роутера эмулятора, перенаправляющий трафик на `127.0.0.1` вашего компьютера.

Это значение зашито по умолчанию для сборки `liveDebug`. Достаточно просто установить и запустить приложение:

```bash
./gradlew :app:installLiveDebug
```

---

### Сценарий Б: Физическое устройство по USB (`adb reverse`) — Рекомендуется

Наиболее быстрый и надёжный способ без необходимости настраивать сетевые маршруты и файрволы:

1. Подключите телефон по USB и убедитесь, что он виден через adb:
   ```bash
   adb devices
   ```
2. Пробросьте порт 8080 с телефона на компьютер:
   ```bash
   adb reverse tcp:8080 tcp:8080
   ```
3. Соберите и установите `liveDebug`, явно указав базовый URL:
   ```bash
   ./gradlew :app:installLiveDebug -PBFF_BASE_URL="http://127.0.0.1:8080/"
   ```

*(После переподключения USB-кабеля или перезагрузки устройства команду `adb reverse tcp:8080 tcp:8080` необходимо повторить).*

---

### Сценарий В: Подключение по локальной сети Wi-Fi (LAN IP)

Предпочтительный путь для физического устройства — сценарий Б (`adb reverse`). Один `-PBFF_BASE_URL=http://192.168...` **не** добавляет этот хост в Android cleartext allowlist.

`liveDebug` разрешает HTTP только для `10.0.2.2`, `localhost` и `127.0.0.1` (`app/src/liveDebug/res/xml/network_security_config.xml`). Release cleartext не открывается.

Если нужен именно LAN HTTP (только debug):

1. Узнайте IPv4 компьютера (`ip a` / `ipconfig`), например `192.168.1.42`.
2. Добавьте **этот** хост в debug-only `network_security_config.xml` (`liveDebug`), не в release и не как глобальный cleartext.
3. Соберите приложение:
   ```bash
   ./gradlew :app:installLiveDebug -PBFF_BASE_URL="http://192.168.1.42:8080/"
   ```
4. Откройте порт 8080 в брандмауэре хоста.

Альтернатива без cleartext: HTTPS на LAN. Не используйте этот сценарий как инструкцию «из коробки».

---

## 6. Частые проблемы и их решение

| Симптом | Причина | Решение |
| :--- | :--- | :--- |
| `IGDB credentials are missing` | Не найдены учетные данные IGDB при проверке порядка разрешения (`Ktor config` > `env IGDB_CLIENT_ID/SECRET` > `local.properties`) | Задайте переменные окружения `IGDB_CLIENT_ID` и `IGDB_CLIENT_SECRET` или добавьте их в `local.properties`. |
| `Failed to connect to /127.0.0.1:8080` на физическом телефоне | Телефон пытается подключиться к своему внутреннему loopback | Выполните `adb reverse tcp:8080 tcp:8080` по USB. |
| Ошибка сетевого тайм-аута при подключении по LAN IP | Брандмауэр хоста блокирует порт 8080 | Откройте порт 8080 в Windows Defender Firewall / ufw или переключитесь на `adb reverse`. |
| `CLEARTEXT communication ... not permitted` на LAN HTTP | Хост не входит в debug cleartext allowlist | Используйте `adb reverse` + `127.0.0.1`, либо добавьте конкретный debug-only host. Не включайте глобальный cleartext в release. |
| Ошибки 429 Too Many Requests от IGDB | Превышение лимита запросов аккаунта | BFF автоматически сглаживает запросы через `SmoothRateLimiter`, однако не выполняйте параллельных запросов в обход BFF. |
