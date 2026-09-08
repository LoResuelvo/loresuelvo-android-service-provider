# android-clean-architecture

Load this skill when changing `domain/`, `data/`, `ui/`,
`domain/usecase/`, or reviewing a dependency boundary.

## Do not load

Do not load it for documentation, Gradle, CI, script-only, or delivery-policy
changes that do not alter application-layer boundaries.

## Dependency direction

```text
ui → domain/usecase → domain
data ───────────────→ domain
```

- `domain/` contains pure entities, ports, outcomes, and errors.
- `domain/usecase/` orchestrates domain ports without infrastructure imports.
- `data/` implements ports and owns HTTP, Auth0, Android storage, DTOs, and
  mappers.
- `ui/` observes state and emits events; it does not construct repositories or
  call `data/` directly.

## Hard rules

- Domain code must not import `data`, `ui`, `android.*`, Dagger/Hilt, OkHttp,
  Retrofit, or serialization.
- DTOs live only in `data/api/dto/`; backend `snake_case` must not enter domain
  or UI.
- Mappers in `data/api/mapper/` contain no business rules.
- Each use case is one class with one `operator fun invoke(...)`, named
  `VerbSubjectUseCase`.
- Outcomes and failures are typed `sealed interface`s, never generic strings.
- Do not add mutable global `object`s; use Hilt injection.

Provider examples include `domain/category/CategoryRepository.kt`,
`domain/usecase/category/GetCategoriesUseCase.kt`,
`data/api/ApiCategoryRepository.kt`, and `ui/auth/WelcomeViewModel.kt`.

## Validation

Run these checks from the repository root (zero matches are expected):

```bash
grep -RInE 'import (com\.loresuelvo\.serviceprovider\.(data|ui)|android\.|dagger|hilt|okhttp3|retrofit2|kotlinx\.serialization)' \
  app/src/main/java/com/loresuelvo/serviceprovider/domain/
grep -RIn 'import com\.loresuelvo\.serviceprovider\.data\.' \
  app/src/main/java/com/loresuelvo/serviceprovider/ui/
```

Then run the smallest relevant JVM check and the policy-selected delivery
gate before committing.

## Review checklist

- Does every dependency point inward toward the domain?
- Is infrastructure hidden behind a domain port?
- Are DTO conversion and error translation at the data boundary?
- Is UI state immutable and exposed through `StateFlow`?
- Are strings, logging, and Android context kept in their outer layers?

The repository contract in `AGENTS.md` is authoritative when this skill and a
local implementation detail appear to differ.
