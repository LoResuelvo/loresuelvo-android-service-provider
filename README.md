# LoResuelvo Android Service Provider

Aplicación Android para **prestadores** de LoResuelvo. Construida con Kotlin + Jetpack Compose.

> Para reglas de arquitectura, convenciones, comandos y skills, ver [`AGENTS.md`](./AGENTS.md). Este README es solo para humanos que arrancan.

---

## Requisitos

- **JDK 17** (`java -version`).
- **Android SDK Platform 35** + Build Tools 35 + Platform Tools + Command Line Tools.
- Variable `ANDROID_HOME` apuntando al SDK (ver abajo).
- **WSL/Linux/macOS** con `make` y `bash` (los scripts usan GNU make).

### Configurar el SDK

Agregar a `~/.bashrc` (o `~/.zshrc`):

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$ANDROID_HOME/platform-tools:$PATH
export PATH=$ANDROID_HOME/emulator:$PATH
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
```

Aplicar y verificar:

```bash
source ~/.bashrc
sdkmanager --version
```

---

## Estado del proyecto — Fase 0 (Setup de ambiente)

Esta es la rama de setup: la app **no tiene features de producto todavía**.

Lo que ya está en verde:

- Build `./gradlew assembleDevDebug` ✅
- Unit tests JVM `./gradlew testDevDebugUnitTest` ✅ (incluye Cucumber BDD smoke)
- Lint `./gradlew lintDevDebug` ✅
- Acceptance `./gradlew connectedDevDebugAndroidTest` ✅ (Compose-test contra `HomeScreen` placeholder)
- CI en GitHub Actions (`.github/workflows/ci.yml`) ✅
- Release workflow (`.github/workflows/release.yml`) ✅
- 3 flavors (`dev`/`staging`/`prod`) con variables de entorno y Auth0 scheme propio

Próxima fase (**Fase 1 — Walking skeleton BDD/TDD**):

- Wiring de Hilt + KSP + Retrofit + OkHttp + Auth0 SDK.
- Patrón Navigation Compose (NavHost, rutas, smart-router de auth).
- Primer `.feature` real y su step def siguiendo el skill `android-bdd-tdd-process`.

---

## Setup inicial

1. Clonar el repo.
2. Crear `local.properties` (NO commitear) en la raíz con tus credenciales de Auth0 por flavor:
   ```properties
   AUTH0_DOMAIN=loresuelvo-dev.auth0.com
   AUTH0_CLIENT_ID=tu_client_id_dev
   AUTH0_SCHEME=com.loresuelvo.provider
   AUTH0_AUDIENCE=http://localhost:8080
   API_URL=http://10.0.2.2:8080
   ```
   `AUTH0_AUDIENCE` es el identificador lógico de la API registrado en Auth0; no tiene que ser una URL alcanzable. En un teléfono físico, `API_URL` sí debe apuntar a una dirección alcanzable de la PC, por ejemplo `http://192.168.1.41:8080`.

   Para `staging` y `prod`, usar `AUTH0_DOMAIN_STAGING`, `AUTH0_CLIENT_ID_STAGING`, `AUTH0_SCHEME_STAGING`, `AUTH0_AUDIENCE_STAGING`, `API_URL_STAGING`, etc.
3. `./gradlew :app:assembleDevDebug` para verificar que compila.

---

## Comandos

Todos los targets aceptan `FLAVOR=Dev|Staging|Prod` (default: `Dev`).

| Comando | Qué hace |
|---|---|
| `make help` | Lista los targets disponibles. |
| `make build` | `./gradlew assemble<Flavor>Debug` |
| `make lint` | `./gradlew lint<Flavor>Debug` |
| `make test` | `./gradlew test<Flavor>DebugUnitTest` (JVM, rápido, incluye BDD) |
| `make e2e` | Acceptance tests con Compose-test / Espresso (requiere emulador o device). |
| `make test-all-once` | `make test` + `make e2e`. |
| `make ci` | `make build` + `make lint` + `make test-all-once`. Usar antes de merge. |
| `make clean` | `./gradlew clean`. |
| `make devices` | `adb devices`. |

Para invocar `./gradlew` directamente con un test focalizado:

```bash
./gradlew :app:testDevDebugUnitTest --tests "*SmokeUnitTest*"
```

Para correr solo el BDD:

```bash
./gradlew :app:testDevDebugUnitTest --tests "*BddSmokeCucumberTest"
```

---

## Estructura actual (Fase 0)

```
app/
  src/
    main/
      java/com/loresuelvo/serviceprovider/
        MainActivity.kt                       # setContent { LoResuelvoApp() }
        LoresuelvoApp.kt                      # Application class
        ui/screens/home/HomeScreen.kt         # placeholder Compose
      res/
        values/strings.xml                    # Strings de UI en español (default)
        values-en/strings.xml                 # Strings en inglés
    test/
      java/.../bdd/smoke/                     # Glue runner + steps BDD
      resources/features/smoke/               # .feature de Cucumber
    androidTest/
      java/.../acceptance/                    # Acceptance con Compose-test
```

---

## Troubleshooting

### `SDK location not found`

Crear `local.properties` en la raíz con `sdk.dir=/path/to/Android/Sdk` (o setear `ANDROID_HOME`).

Para más ayuda, ver [`AGENTS.md`](./AGENTS.md).
