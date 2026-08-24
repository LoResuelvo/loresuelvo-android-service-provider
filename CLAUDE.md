# CLAUDE.md — LoResuelvo Android Service Provider

Fuente canónica de contexto para Claude y otros agentes: **`AGENTS.md`** (este directorio).

## TL;DR

- App Android exclusiva para **prestadores** de LoResuelvo.
- Estado actual: **Fase 0 — Setup de ambiente**. La app todavía no tiene features de producto.
- Stack ya configurado: Kotlin + Compose + Navigation Compose + Material 3 + JUnit + MockK + Turbine + Robolectric + Cucumber JVM.
- Lo que **NO** está todavía: Hilt, Retrofit, OkHttp, `kotlinx-serialization`, Auth0, Coil. Llega en Fase 1.
- Regla de oro durante Fase 0: **no agregar dependencias que no estén en `libs.versions.toml`**. Escalar primero.

## Cómo trabajar acá

1. **Leer primero**: `AGENTS.md`.
2. Cargar skills desde `skills/<skill>/SKILL.md` solo cuando aplique a la tarea.
3. Mantener el scope de Fase 0: cambios de tooling, CI, plumbing, smoke tests.
4. Cualquier cambio que pida Hilt, Retrofit, Auth0 o features de negocio → **escalar antes de implementar**.
5. Validar antes de PR: `make lint && make test && make build`. Si cambia acceptance/BDD, también `make e2e`.

## Convenciones rápidas

- **Código y tests**: inglés.
- **UI y steps de BDD**: español.
- **Commits**: Conventional Commits en inglés (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`, `build:`, `ci:`).
- **PRs**: atómicos por fase. Título en inglés, descripción con bullet list de archivos tocados y comandos de validación.

## Si tenés dudas

- Reglas, comandos, flavors y reglas críticas: `AGENTS.md`.
- App espejo consumidora (referencia madura): `../loresuelvo-android-consumer/AGENTS.md`. **No** tomar dependencias que allí estén pero acá no — preguntar antes.

## NO hacer

- ❌ No agregar dependencias (Hilt, Retrofit, OkHttp, Auth0, Coil, kotlinx-serialization, navigation-safeargs, etc.) sin discutir.
- ❌ No introducir `object` global mutable.
- ❌ No hardcodear literales en español en `app/src/main/java/`.
- � No loguear tokens ni payloads.
- ❌ No mergear a `main` sin `make ci` verde.
- ❌ No escribir features de negocio todavía. Solo BDD smoke.
