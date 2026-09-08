# Подпись demoRelease

Портфолио-релиз собирает **signed `demoRelease`**: R8, resource shrinking, `applicationId` `io.github.typenil.gametracker.demo`, non-debuggable. Production `liveRelease` использует **другой** keystore (`RELEASE_*`) и этот документ его не касается.

Keystore и пароли **не коммитятся**. `*.jks` / `*.keystore` уже в `.gitignore`.

## 1. Создать отдельный demo keystore

Один раз, локально. Пароли придумайте сами и никуда кроме GitHub Secrets их не кладите.

```bash
keytool -genkeypair -v \
  -keystore demo-release.jks \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10950 \
  -alias demo-release
```

`keytool` спросит store password, key password, CN и прочие поля Distinguished Name. Для портфолио достаточно, например, `CN=GameTracker Demo`.

Не используйте production / Play App Signing ключ и не копируйте `RELEASE_*` секреты.

Файл `demo-release.jks` храните вне репозитория.

## 2. Base64 для секрета

Linux:

```bash
base64 -w0 demo-release.jks
```

macOS:

```bash
base64 -i demo-release.jks | tr -d '\n'
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("demo-release.jks"))
```

Вывод целиком — значение `DEMO_KEYSTORE_B64`. Не коммитьте его.

## 3. SHA-256 сертификата (не секрет)

Нужен формат `apksigner`: строчные hex **без** двоеточий.

Linux / macOS (нужен `openssl`):

```bash
keytool -exportcert -keystore demo-release.jks -alias demo-release | openssl dgst -sha256
```

Уберите префикс `(stdin)= `, оставьте 64 hex-символа. Это значение `DEMO_CERT_SHA256`.

Его же можно снять с уже подписанного APK:

```bash
apksigner verify --print-certs app-demo-release.apk
# Signer #1 certificate SHA-256 digest: <это значение>
```

## 4. GitHub Secrets и Variables

Repository **Settings → Secrets and variables → Actions**.

### Secrets

| Name | Что положить |
| :--- | :--- |
| `DEMO_KEYSTORE_B64` | Base64 всего `demo-release.jks` |
| `DEMO_STORE_PASSWORD` | store password keystore |
| `DEMO_KEY_ALIAS` | alias, например `demo-release` |
| `DEMO_KEY_PASSWORD` | key password |

### Variables

| Name | Что положить |
| :--- | :--- |
| `DEMO_CERT_SHA256` | SHA-256 сертификата, 64 hex-символа без двоеточий |

Не кладите demo-ключ в environment `production` и не переиспользуйте `RELEASE_KEYSTORE_B64` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`.

Workflow `.github/workflows/portfolio-release.yml` читает только `DEMO_*`. PR CI (`ci.yml`) эти секреты не использует и по-прежнему проверяет, что `demoRelease`/`liveRelease` в обычном CI **unsigned**.

## 5. Локальная проверка без секретов

```bash
./gradlew :app:assembleDemoRelease
```

Без `DEMO_KEYSTORE_PATH` APK остаётся unsigned — так и задумано. Подпись появляется только в portfolio-release job.

## 6. Когда секреты уже стоят

Тег `v1.0.1` создавать не из этой инструкции: сначала зелёный PR, затем секреты, затем отдельный тег с `main`. Существующий тег `v1.0.0` не двигать.
