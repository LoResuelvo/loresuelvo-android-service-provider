# android-api-client-governance

Load this skill when adding or changing a Retrofit endpoint, DTO, mapper,
interceptor, authenticator, API configuration, or network test.

## Do not load

Do not load it for a domain-only, UI-only, documentation-only, or delivery
tooling change that does not alter the HTTP boundary.

## Boundary

The domain must not know HTTP, Retrofit, OkHttp, Android networking, or
serialization. Define a domain port and implement it in `data/`. Keep
Retrofit interfaces, DTOs, and wire-format details inside `data/api/`.

Provider examples are `domain/category/CategoryRepository.kt`,
`data/api/ApiCategoryRepository.kt`, and `data/api/BackendApi.kt`.

## DTOs and mappers

- DTOs live only in `data/api/dto/`.
- Use `@Serializable` and `@SerialName` for backend field names.
- Keep backend `snake_case` out of domain and UI types.
- Mappers live in `data/api/mapper/` and only translate data; they do not
  contain business rules.
- Preserve every field required by the wire contract, even when the domain
  uses fewer fields. Test nullable fields, defaults, and field-name mapping.

```kotlin
@Serializable
data class CategoryDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)
```

The existing category path is
`data/api/dto/CategoryDto.kt` → `data/api/mapper/CategoryMapper.kt` →
`domain/category/Category.kt`.

## Errors and authentication

- Keep the shared `ApiError` hierarchy pure in `domain/api/`.
- Repositories map transport and response failures to typed `ApiError`
  values; use cases translate them into typed outcomes.
- Distinguish network/timeouts, unauthorized responses, server responses with
  status and a safe message, and unknown failures.
- `AuthInterceptor` reads the token through `AuthSessionStore`.
- If the token is missing or blank, forward the request unchanged.
- Never log tokens, credentials, request/response bodies, or payloads.
- Do not retry ordinary 4xx responses. Any future refresh/retry adapter must
  terminate after a second unauthorized response.

See `data/api/AuthInterceptor.kt`, `data/api/ApiErrorMapping.kt`,
`domain/api/ApiError.kt`, and `domain/auth/AuthSessionStore.kt`.

## Network configuration

- Use `BuildConfig.API_URL` for the Retrofit base URL.
- Keep timeouts and client construction centralized in `data/api/ApiConfig.kt`
  and the appropriate Hilt module.
- Provide shared clients through `SingletonComponent`; do not construct a
  client in a composable, ViewModel, or repository call.
- Preserve the Dev-only cleartext overlay. Staging and Prod endpoints remain
  HTTPS-only.

## Tests

Use JVM tests for repositories, mappers, interceptors, and HTTP behavior.
Use MockWebServer for request/response contracts and assert method, path,
headers, body, status mapping, and failure behavior. Run:

```bash
make test FLAVOR=Dev
./gradlew :app:testDevDebugUnitTest --tests '*WelcomeViewModelTest*'
```

For a boundary review, verify that domain code has no transport imports:

```bash
grep -RInE 'import (okhttp3|retrofit2|kotlinx\.serialization|android\.|com\.loresuelvo\.serviceprovider\.data)' \
  app/src/main/java/com/loresuelvo/serviceprovider/domain/
```

## Anti-patterns

- DTOs in `domain/` or `ui/`.
- `@SerializedName` instead of `@SerialName`.
- Business rules inside mappers.
- Broad catches that hide the failure type.
- Runtime flags that silently replace the configured `BuildConfig.API_URL`.
- Logging credentials, tokens, headers, or response bodies.
