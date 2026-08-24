# AGENTS.md — LoResuelvo Android Service Provider

Última actualización: 2026-08-24 (Fase 0 — Setup de ambiente)

Fuente canónica para agentes. Leer este archivo primero y cargar skills locales solo cuando apliquen. La documentación para humanos vive en `README.md` (setup y comandos).

## Modo skills-first

1. Leer las reglas globales de este archivo.
2. Elegir la skill adecuada del índice de skills.
3. Cargar solo `skills/<skill>/SKILL.md` y referencias puntuales cuando apliquen.
4. Evitar abrir documentación o código no relacionado con la tarea.

## Estado actual: Fase 0 (Setup de ambiente)

**Objetivo de esta fase**: tener CI/CD verde con build + lint + unit tests + acceptance tests funcionando sobre una app mínima (Compose + Material 3 + Navigation Compose + Cucumber BDD + MockK + Robolectric + Turbine). **No** hay features de producto todavía.

**Lo que NO está todavía** (llega en Fase 1 — Walking skeleton):

- Hilt + KSP + KAPT.
- Retrofit + OkHttp + `kotlinx-serialization`.
- Auth0 SDK.
- Navigation Compose real con smart-router (el `LoResuelvoNav`).
- DTOs, mappers, repositorios, use cases.
- ViewModels.
- Cualquier `.feature` de negocio (sólo existe `BddSmoke.feature`).

Regla para los agentes durante Fase 0: **no** agregar dependencias que no estén en `libs.versions.toml`. Si la tarea las requiere, escalar primero.

---

## Stack tecnológico

- **Lenguaje/Build**: Kotlin 2.0.21, AGP 8.13.2, Gradle Wrapper, `libs.versions.toml` como catálogo de versiones.
- **UI**: Jetpack Compose (BOM 2024.09), Material 3, Navigation Compose (`androidx.navigation:navigation-compose`), `minSdk 24` / `targetSdk 35`.
- **Estado**: aún no se introdujo `StateFlow` / UDF (no hay ViewModels todavía). Cuando lleguen, usar el patrón del consumer.
- **DI**: Hilt **NO** instalado todavía. Llega en Fase 1.
- **Auth**: Auth0 SDK **NO** instalado todavía. Llega en Fase 1.
- **Networking**: Retrofit + OkHttp + `kotlinx-serialization` **NO** instalado. Llega en Fase 1.
- **Testing**: JUnit4, MockK, Turbine, `kotlinx-coroutines-test`, Robolectric, `okhttp-mockwebserver`, Compose-test, Cucumber JVM 7.x para BDD.

## Arquitectura (lo que viene)

El proyecto va a seguir la misma **Clean Architecture liviana + Ports & Adapters** que `loresuelvo-android-consumer`. Una vez que comencemos Fase 1:

- **Capa de Dominio (`domain/`)**: tipos puros, entidades, value objects, **puertos** (interfaces) y casos de uso. Sin imports de `data/`, `ui/`, `android.*`, ni libs externas.
- **Capa de Infraestructura (`data/`)**: adapters que implementan los puertos del dominio. DTOs snake_case del backend con `@SerialName`, mappers DTO↔dominio, `ApiClient`, `Auth0AuthProvider`, etc.
- **Capa de Aplicación (`domain/usecase/`)**: casos de uso que orquestan puertos. Sin estado. Outcomes tipados.
- **Capa de Presentación (`ui/`)**: composables, ViewModels, navegación, theme, componentes reutilizables.

Referencia: leer `../loresuelvo-android-consumer/AGENTS.md` para el detalle maduro de cada capa.

---

## Estructura de carpetas

```txt
app/
  src/
    main/
      java/com/loresuelvo/serviceprovider/
        MainActivity.kt                       # setContent { LoResuelvoApp() }
        LoresuelvoApp.kt                      # Application class (vacía por ahora)
        ui/screens/home/HomeScreen.kt         # placeholder Compose (Fase 0)
      res/
        values/strings.xml                    # Strings de UI en español (default)
        values-en/strings.xml                 # Strings en inglés
        xml/                                  # backup_rules, data_extraction_rules, locales_config
    test/
      java/.../SmokeUnitTest.kt               # Unit JVM smoke (JUnit + MockK)
      java/.../bdd/smoke/                     # Glue runner + steps BDD
      resources/features/smoke/               # .feature de Cucumber (BDD)
    androidTest/
      java/.../ExampleInstrumentedTest.kt
      java/.../acceptance/                    # Acceptance con Compose-test
```

---

## Comandos de validación

Comandos actuales del repo:

```bash
make help
make build         # assembleDevDebug
make lint          # lintDevDebug
make test          # testDevDebugUnitTest (incluye BDD Cucumber)
make e2e           # connectedDevDebugAndroidTest con package=...acceptance
make test-all-once # test + e2e
make ci            # build + lint + test-all-once
make clean
make devices
```

Variables: `FLAVOR=Dev|Staging|Prod` (default: `Dev`).

### Política

1. **Durante Fase 0**: cualquier cambio debe pasar `make lint && make test && make build`. Si toca acceptance, también `make e2e`.
2. **Desde Fase 1**: se incorpora TDD/BDD primero; tests antes del impl.
3. **Fail-fast**: detenerse en la primera falla, corregir y re-ejecutar.

---

## Flavors

Tres flavors (`dev`/`staging`/`prod`) sobre el dimension `environment`. Cada uno expone `BuildConfig.API_URL`, `BuildConfig.AUTH0_DOMAIN`, `BuildConfig.AUTH0_CLIENT_ID`, `BuildConfig.AUTH0_SCHEME` y `BuildConfig.AUTH0_AUDIENCE`.

| Flavor    | applicationId                       | versionNameSuffix |
|-----------|-------------------------------------|-------------------|
| `dev`     | `com.loresuelvo.serviceprovider.dev`| `-dev`            |
| `staging` | `com.loresuelvo.serviceprovider.staging` | `-staging`    |
| `prod`    | `com.loresuelvo.serviceprovider`    | (none)            |

El scheme de Auth0 por flavor:

| Flavor    | `AUTH0_SCHEME` por defecto          |
|-----------|-------------------------------------|
| `dev`     | `com.loresuelvo.provider`           |
| `staging` | `com.loresuelvo.provider.staging`   |
| `prod`    | `com.loresuelvo.provider.prod`      |

Lectura de variables: prioridad `local.properties` > gradle property > env > default. Ver `app/build.gradle.kts:34-39` (`envVar(...)`).

---

## CI / CD

- **CI** (`.github/workflows/ci.yml`): corre en cada push a `main` y cada PR. Ejecuta `make lint`, `make test`, `make e2e` (con `android-emulator-runner`) y `make build` para `FLAVOR=Staging`. Las credenciales se inyectan desde GitHub Secrets.
- **Release** (`.github/workflows/release.yml`): corre cuando se pushea un tag `v*.*.*`. Construye Staging APK y Prod AAB, los sube como artifacts, y depende de los environments `staging` y `production` para protección.

Secrets requeridos en GitHub:

- `AUTH0_DOMAIN_STAGING`, `AUTH0_CLIENT_ID_STAGING`, `AUTH0_AUDIENCE_STAGING`, `API_URL_STAGING`.
- `AUTH0_DOMAIN_PROD`, `AUTH0_CLIENT_ID_PROD`, `AUTH0_AUDIENCE_PROD`, `API_URL_PROD`.
- `AUTH0_CLIENT_SECRET_STAGING`, `AUTH0_CLIENT_SECRET_PROD` (sólo release).
- `AUTH0_SCHEME_STAGING`, `AUTH0_SCHEME_PROD` (sólo release).
- `NEXT_PUBLIC_PUBLIC_MEDIA_BASE_URL_STAGING`, `NEXT_PUBLIC_PUBLIC_MEDIA_BASE_URL_PROD` (sólo release).

`AUTH0_SCHEME_STAGING` y `AUTH0_SCHEME_PROD` tienen defaults en `app/build.gradle.kts`; el CI los sobrescribe explícitamente para self-documentar el contrato.

---

## BDD con Cucumber JVM

La capa BDD vive **en el source set de JVM** (`src/test/`), no en `androidTest/`:

- `.feature`: `app/src/test/resources/features/<area>/<user-journey>.feature`
- Step definitions + glue runner: `app/src/test/java/com/loresuelvo/serviceprovider/bdd/<area>/<journey>/...`

Filtro actual: `cucumber.filter.tags=not @wip` configurado en `app/build.gradle.kts:84-93`. Cada `.feature` nuevo arranca con escenarios marcados `@wip` y se destraban cuando el step def + impl están listos. El smoke actual (`BddSmoke.feature`) verifica la infra y está siempre activo.

Referencia completa del proceso BDD/TDD cuando entremos en Fase 1: skill `android-bdd-tdd-process` (espejo del consumer, ajustar copy cuando aplique).

---

## Reglas críticas (Fase 0)

### Topología (regla de `MainActivity`)

- `MainActivity.onCreate` debe limitarse a:
  ```kotlin
  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContent { LoResuelvoApp() }
      }
  }
  ```
- Sin `@AndroidEntryPoint` hasta que se introduzca Hilt en Fase 1.

### Logging

- **Cero** `Log.d/w/e` directo en `app/src/main/`. Por ahora tampoco se usa; cuando llegue networking/auth se introduce `Logger` gated por `BuildConfig.DEBUG`.

### i18n

- Todo texto visible al usuario va en `app/src/main/res/values/strings.xml` (es) y `values-en/strings.xml` (en).
- Cero literales en español en `app/src/main/java/.../`.

### Seguridad

- Cero secretos en código. Las vars se leen de `BuildConfig` con fallbacks; los valores reales vienen de `local.properties` o del pipeline.
- No commitear `local.properties`. Está en `.gitignore`.

### Dependencias

- **No** agregar deps nuevas sin discutirlo. Hoy el set está deliberadamente recortado a Compose + testing.
- En Fase 1 entra Hilt + KSP + Retrofit + OkHttp + `kotlinx-serialization` + Auth0 + Coil (ver consumer).

---

## Índice de skills (a poblar en Fase 1)

Cuando copiemos/adaptemos skills del consumer, deberían vivir en `skills/<skill>/SKILL.md`. En Fase 0 todavía no se carga ninguno.

---

## Mapa rápido de decisión

- "¿Toco una capa o import entre capas?": `android-clean-architecture` (espejo del consumer).
- "¿Voy a escribir código con tests?": `android-bdd-tdd-process` (espejo del consumer).
- "¿Voy a cerrar un PR / quiero validar antes de pushear?": `android-testing-gates` (espejo del consumer).
- "¿Voy a tocar el cliente HTTP, DTOs, mappers, interceptors?": `android-api-client-governance` (espejo del consumer).
- "¿Voy a agregar Hilt o un `@HiltViewModel`?": `android-hilt-governance` (espejo del consumer).
- "¿Voy a tocar `AGENTS.md`, `CLAUDE.md`, skills o `README.md`?": `android-doc-governance` (espejo del consumer).
- "¿Voy a hacer commit o PR?": `android-commit-governance` (espejo del consumer).

---

## Idioma y estilo

- Texto visible para usuarios: español, centralizado en `strings.xml`.
- Código, tests, nombres de variables, comentarios técnicos: inglés.
- Steps de BDD: español (alineado con el webapp y consumer).
- Commits y mensajes de PR: inglés, Conventional Commits.
- Comentarios explicativos (que agreguen info, no describan lo obvio) en español.

---

## Checklist final para agentes (Fase 0)

1. **No** agregar dependencias nuevas. Si la tarea las pide, abrir thread antes.
2. Diff revisado: sin archivos generados accidentales, sin archivos debug.
3. Sin secretos, sin logs sensibles, sin literales en español en código.
4. Cero `Log.d/e/w` directo en código de producción.
5. Strings de UI en `strings.xml` (es + en).
6. `make lint && make test && make build` verde.
7. Si cambió el flujo BDD o un composable, `make e2e` verde.
8. `AGENTS.md` actualizado si cambió arquitectura, convención, o comandos.
9. Resumen final conciso con archivos tocados, validación y riesgos residuales.
