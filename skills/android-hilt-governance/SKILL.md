# android-hilt-governance

Load this skill when adding or changing Hilt modules, bindings, scopes,
`@HiltViewModel`s, or `@HiltAndroidTest`s.

## Do not load

Do not load it for UI-only changes with no dependency wiring, or for an HTTP
change that does not change dependency construction.

## Production graph

- `LoresuelvoApp` uses `@HiltAndroidApp`.
- `MainActivity` uses `@AndroidEntryPoint` and only hosts Compose navigation.
- ViewModels use `@HiltViewModel` and constructor injection.
- Routes obtain ViewModels with `hiltViewModel()`.
- Production code must not use `viewModelFactory { initializer { ... } }`.
- New mutable global `object`s are forbidden.

Current provider examples are `LoresuelvoApp.kt`, `MainActivity.kt`,
`ui/auth/WelcomeViewModel.kt`, and `ui/navigation/LoResuelvoNav.kt`.

## Modules and scopes

Use `SingletonComponent` for process-wide infrastructure: Retrofit, OkHttp,
JSON, repositories, and `EncryptedAuthSessionStore`. Use
`ViewModelComponent` only for dependencies that must live exactly as long as
one ViewModel. Network clients must not be ViewModel-scoped.

Prefer:

- `@Binds` for interfaces whose implementation has an `@Inject` constructor;
- `@Provides` for third-party types or values that cannot be constructor-
  injected;
- `@Singleton` for shared infrastructure.

Provider modules are `di/NetworkModule.kt`, `di/RepositoryModule.kt`,
`di/AuthModule.kt`, and `data/auth/SessionStoreModule.kt`.

## ViewModels

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val useCase: ExampleUseCase,
) : ViewModel()
```

```kotlin
@Composable
fun ExampleRoute(
    viewModel: ExampleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ExampleScreen(state = state, onEvent = viewModel::onEvent)
}
```

Keep state and event handling in the ViewModel. Keep composables free of
manual dependency construction.

## Instrumented tests with Hilt

Every instrumented test that launches `MainActivity` declares Hilt before its
Compose rule:

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()
}
```

The custom runner is
`app/src/androidTest/java/com/loresuelvo/serviceprovider/HiltTestRunner.kt`.
Use `@TestInstallIn(..., replaces = [...])` for suite-wide fakes and
`@UninstallModules(...)` for a class-local override. JVM tests under
`app/src/test/` construct ViewModels with fakes or mocks and do not use Hilt.

## Anti-patterns

- Creating repositories, Retrofit, or OkHttp clients in UI code.
- Using `@Provides` where `@Binds` is sufficient.
- Using `@ActivityScoped` for process-wide state.
- Omitting `HiltAndroidRule` or declaring it after the Compose rule.
- Replacing the production singleton with a separately constructed test store.
