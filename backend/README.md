# GameTracker BFF

Backend-for-Frontend (BFF) service for GameTracker built with Kotlin and Ktor 3. It acts as a secure, rate-limiting, and caching proxy in front of the Twitch IGDB API, keeping credentials off mobile clients.

## Quickstart

### 1. Set environment variables
Provide Twitch IGDB API credentials (see `.env.example` or root `local.properties`):

```bash
export IGDB_CLIENT_ID="your_client_id"
export IGDB_CLIENT_SECRET="your_client_secret"
```

Windows (PowerShell):
```powershell
$env:IGDB_CLIENT_ID = "your_client_id"
$env:IGDB_CLIENT_SECRET = "your_client_secret"
```

Alternatively, add `IGDB_CLIENT_ID` and `IGDB_CLIENT_SECRET` to `local.properties` at the repository root.

### 2. Run the service
```bash
./gradlew :backend:run
```

Windows (PowerShell / Command Prompt):
```powershell
.\gradlew.bat :backend:run
```

### 3. Verify health
```bash
curl http://127.0.0.1:8080/health
```

Inspect Caffeine cache metrics and region sizes:
```bash
curl http://127.0.0.1:8080/health/cache
```

For complete local configuration, mobile emulator routing, and USB physical device (`adb reverse`) instructions, see [LOCAL_BFF_SETUP.md](../docs/LOCAL_BFF_SETUP.md).
