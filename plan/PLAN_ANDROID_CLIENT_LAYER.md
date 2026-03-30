# ПЛАН: Слой заказчиков — Android-приложение
# Язык: Kotlin, архитектура: MVVM + StateFlow + Retrofit + Hilt (или ручной DI)

---

## ШАГ 1 — Retrofit API-интерфейс

Добавить в `ChatApi.kt` (или `ApiService.kt`) новые методы:

```kotlin
// --- Определение типа входа ---
@GET("api/v1/auth/login-type")
suspend fun getLoginType(@Query("login") login: String): LoginTypeResponse

// --- Вход заказчика ---
@POST("api/v1/auth/client-login")
suspend fun clientLogin(@Body body: ClientLoginRequest): ClientAuthResponse

// --- Профиль заказчика ---
@GET("api/v1/client/me")
suspend fun getClientMe(): ClientProfile

// --- Сотрудники доступные заказчику ---
@GET("api/v1/client/employees")
suspend fun getClientEmployees(): List<EmployeeItem>
```

**Data-классы:**

```kotlin
data class LoginTypeResponse(val type: String)  // "client" | "employee"

data class ClientLoginRequest(
    val login: String,
    val password: String,
    @SerializedName("deviceId") val deviceId: String,
    val platform: String = "android"
)

data class ClientAuthResponse(
    val ok: Boolean,
    val token: String,
    val refreshToken: String,
    val client: ClientProfile
)

data class ClientProfile(
    val id: String,
    val login: String,
    val name: String
)

data class EmployeeItem(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?
)
```

---

## ШАГ 2 — TokenStorage (расширить существующий)

Найти `TokenStorage.kt` (или `SessionManager.kt`) и добавить:

```kotlin
// Добавить константы
private const val KEY_ROLE       = "user_role"      // "employee" | "client"
private const val KEY_CLIENT_ID  = "client_id"
private const val KEY_CLIENT_LOGIN = "client_login"
private const val KEY_CLIENT_NAME  = "client_name"

// Добавить методы сохранения клиентской сессии
fun saveClientSession(response: ClientAuthResponse) {
    prefs.edit()
        .putString(KEY_ACCESS_TOKEN,   response.token)
        .putString(KEY_REFRESH_TOKEN,  response.refreshToken)
        .putString(KEY_ROLE,           "client")
        .putString(KEY_CLIENT_ID,      response.client.id)
        .putString(KEY_CLIENT_LOGIN,   response.client.login)
        .putString(KEY_CLIENT_NAME,    response.client.name)
        .apply()
}

fun getRole(): String = prefs.getString(KEY_ROLE, "employee") ?: "employee"
fun isClient(): Boolean = getRole() == "client"

fun getClientProfile(): ClientProfile? {
    val id    = prefs.getString(KEY_CLIENT_ID,    null) ?: return null
    val login = prefs.getString(KEY_CLIENT_LOGIN, null) ?: return null
    val name  = prefs.getString(KEY_CLIENT_NAME,  "") ?: ""
    return ClientProfile(id = id, login = login, name = name)
}

// Обновить clearSession() — добавить очистку клиентских ключей
fun clearSession() {
    prefs.edit().clear().apply()
}
```

---

## ШАГ 3 — LoginViewModel

Найти `LoginViewModel.kt` и переписать / дополнить:

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: ChatApi,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    // Состояния экрана
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class ShowPasswordField(val login: String) : UiState()
        object WaitingOtp : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class NavEvent {
        data class GoToOtp(val email: String) : NavEvent()
        object GoToMain : NavEvent()        // успешный вход заказчика
        object GoToProfile : NavEvent()     // новый сотрудник без профиля
    }

    val uiState   = MutableStateFlow<UiState>(UiState.Idle)
    val navEvent  = MutableSharedFlow<NavEvent>()

    // Debounce-проверка типа входа
    private var checkJob: Job? = null

    fun onLoginChanged(login: String) {
        checkJob?.cancel()
        uiState.value = UiState.Idle
        if (login.length < 3) return

        checkJob = viewModelScope.launch {
            delay(700)
            checkLoginType(login.trim().lowercase())
        }
    }

    private suspend fun checkLoginType(login: String) {
        uiState.value = UiState.Loading
        try {
            val response = api.getLoginType(login)
            uiState.value = when (response.type) {
                "client"   -> UiState.ShowPasswordField(login)
                else       -> UiState.Idle  // для employee кнопка просто отправит OTP
            }
        } catch (e: Exception) {
            // При ошибке сети — не блокируем, fallback на OTP
            uiState.value = UiState.Idle
        }
    }

    // Нажали "Войти" в режиме заказчика
    fun loginAsClient(login: String, password: String) {
        if (password.isBlank()) {
            uiState.value = UiState.Error("Введите пароль")
            return
        }
        viewModelScope.launch {
            uiState.value = UiState.Loading
            try {
                val body = ClientLoginRequest(
                    login    = login.trim().lowercase(),
                    password = password,
                    deviceId = DeviceUtils.getDeviceId()
                )
                val result = api.clientLogin(body)
                tokenStorage.saveClientSession(result)
                navEvent.emit(NavEvent.GoToMain)
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    401  -> "Неверный логин или пароль"
                    429  -> "Слишком много попыток, подождите"
                    else -> "Ошибка входа (${e.code()})"
                }
                uiState.value = UiState.Error(msg)
            } catch (e: Exception) {
                uiState.value = UiState.Error("Нет соединения с сервером")
            }
        }
    }

    // Нажали "Войти" / "Получить код" в режиме сотрудника
    fun loginAsEmployee(email: String) {
        if (!email.contains('@')) {
            uiState.value = UiState.Error("Введите корректный email")
            return
        }
        viewModelScope.launch {
            uiState.value = UiState.Loading
            try {
                api.requestOtp(email.trim().lowercase())
                navEvent.emit(NavEvent.GoToOtp(email))
            } catch (e: Exception) {
                uiState.value = UiState.Error("Не удалось отправить код")
            }
        }
    }

    fun clearError() {
        if (uiState.value is UiState.Error) uiState.value = UiState.Idle
    }
}
```

---

## ШАГ 4 — LoginFragment (разметка поведения)

Найти `LoginFragment.kt` и обновить `onViewCreated`:

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Поле пароля изначально скрыто
    binding.passwordLayout.isVisible = false
    binding.passwordLayout.alpha = 0f

    // Debounce-проверка при вводе логина
    binding.loginInput.doAfterTextChanged { text ->
        viewModel.onLoginChanged(text?.toString() ?: "")
    }

    // Кнопка "Войти"
    binding.btnLogin.setOnClickListener {
        val login    = binding.loginInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        when (viewModel.uiState.value) {
            is LoginViewModel.UiState.ShowPasswordField ->
                viewModel.loginAsClient(login, password)
            else ->
                viewModel.loginAsEmployee(login)
        }
    }

    // Реакция на состояние
    viewLifecycleOwner.lifecycleScope.launch {
        viewModel.uiState.collect { state ->
            when (state) {
                is LoginViewModel.UiState.Loading -> {
                    binding.btnLogin.isEnabled = false
                    binding.progressBar.isVisible = true
                    binding.errorText.isVisible = false
                }
                is LoginViewModel.UiState.ShowPasswordField -> {
                    binding.progressBar.isVisible = false
                    binding.btnLogin.isEnabled = true
                    // Анимация появления поля пароля
                    if (!binding.passwordLayout.isVisible) {
                        binding.passwordLayout.isVisible = true
                        binding.passwordLayout.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(250)
                            .start()
                        binding.passwordInput.requestFocus()
                    }
                    binding.btnLogin.text = "Войти"
                    binding.loginHint.text = "Введите пароль"
                }
                is LoginViewModel.UiState.Idle -> {
                    binding.progressBar.isVisible = false
                    binding.btnLogin.isEnabled = true
                    // Скрыть поле пароля если было показано
                    if (binding.passwordLayout.isVisible) {
                        binding.passwordLayout.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                binding.passwordLayout.isVisible = false
                            }.start()
                    }
                    binding.btnLogin.text = "Получить код"
                    binding.loginHint.text = "Введите email для входа"
                }
                is LoginViewModel.UiState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.btnLogin.isEnabled = true
                    binding.errorText.text = state.message
                    binding.errorText.isVisible = true
                }
                else -> Unit
            }
        }
    }

    // Навигация
    viewLifecycleOwner.lifecycleScope.launch {
        viewModel.navEvent.collect { event ->
            when (event) {
                is LoginViewModel.NavEvent.GoToOtp ->
                    findNavController().navigate(
                        LoginFragmentDirections.actionToOtp(event.email)
                    )
                is LoginViewModel.NavEvent.GoToMain ->
                    findNavController().navigate(
                        LoginFragmentDirections.actionToMain()
                    )
                is LoginViewModel.NavEvent.GoToProfile ->
                    findNavController().navigate(
                        LoginFragmentDirections.actionToProfile()
                    )
            }
        }
    }
}
```

---

## ШАГ 5 — fragment_login.xml (изменения в разметке)

Найти поле пароля в `fragment_login.xml`. Если его нет — добавить ПОСЛЕ поля логина:

```xml
<!-- Поле пароля — изначально gone, появляется для заказчиков -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/passwordLayout"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:hint="Пароль"
    android:visibility="gone"
    app:passwordToggleEnabled="true"
    app:translationY="16dp">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/passwordInput"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textPassword"
        android:imeOptions="actionDone" />

</com.google.android.material.textfield.TextInputLayout>

<!-- Текст подсказки под полем логина -->
<TextView
    android:id="@+id/loginHint"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:text="Введите email для входа"
    android:textSize="12sp"
    android:textColor="?attr/colorOnSurfaceVariant" />
```

---

## ШАГ 6 — MainActivity / SplashActivity (определение роли при старте)

Найти место где при старте проверяется токен и перенаправляется пользователь:

```kotlin
private fun handleStartup() {
    if (!tokenStorage.hasValidSession()) {
        navigateToLogin()
        return
    }

    // Роутинг по роли
    when (tokenStorage.getRole()) {
        "client"   -> navigateToClientMain()
        "employee" -> navigateToEmployeeMain()
        else       -> navigateToLogin()
    }
}

private fun navigateToClientMain() {
    // Заказчик видит урезанный интерфейс — только чат с допущенными сотрудниками
    startActivity(Intent(this, ClientMainActivity::class.java))
    finish()
}
```

---

## ШАГ 7 — ClientMainActivity (новый файл)

Создать `ClientMainActivity.kt`:

```kotlin
@AndroidEntryPoint
class ClientMainActivity : AppCompatActivity() {

    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var api: ChatApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_main)
        // Загружает ClientMainFragment через NavHostFragment
    }
}
```

Создать `ClientMainFragment.kt`:

```kotlin
@AndroidEntryPoint
class ClientMainFragment : Fragment() {

    private val viewModel: ClientMainViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Загружаем список допущенных сотрудников
        viewModel.loadEmployees()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.employees.collect { employees ->
                // Показываем список сотрудников для начала DM-чата
                renderEmployeeList(employees)
            }
        }
    }

    private fun renderEmployeeList(employees: List<EmployeeItem>) {
        // Заказчик видит только этих сотрудников
        // При нажатии — открывается DM-чат (переиспользовать существующий ChatFragment)
    }
}
```

Создать `ClientMainViewModel.kt`:

```kotlin
@HiltViewModel
class ClientMainViewModel @Inject constructor(
    private val api: ChatApi
) : ViewModel() {

    val employees = MutableStateFlow<List<EmployeeItem>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val error     = MutableStateFlow<String?>(null)

    fun loadEmployees() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                employees.value = api.getClientEmployees()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    error.value = "Сессия истекла, войдите снова"
                } else {
                    error.value = "Ошибка загрузки (${e.code()})"
                }
            } catch (e: Exception) {
                error.value = "Нет соединения"
            } finally {
                isLoading.value = false
            }
        }
    }
}
```

---

## ШАГ 8 — AuthInterceptor (добавить поддержку refresh для client-токена)

Найти `AuthInterceptor.kt` — логика refresh уже работает для сотрудников.
Убедиться что она работает и для заказчиков — refresh-эндпоинт `/auth/refresh` один и тот же, токен обновляется автоматически. Дополнительных изменений не требуется, если refresh реализован корректно.

---

## ШАГ 9 — DeviceUtils (если не существует)

Создать `DeviceUtils.kt`:

```kotlin
object DeviceUtils {
    fun getDeviceId(): String {
        // Стабильный ID установки
        return Settings.Secure.getString(
            App.context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }
}
```

---

## Итоговые изменения по файлам

| Файл | Действие |
|------|----------|
| `ChatApi.kt` | Добавить 4 метода + 5 data-классов |
| `TokenStorage.kt` | Добавить role, clientId, clientLogin, clientName + saveClientSession() |
| `LoginViewModel.kt` | Переписать с UiState sealed class + debounce |
| `LoginFragment.kt` | Добавить реакцию на ShowPasswordField, анимацию поля пароля |
| `fragment_login.xml` | Добавить passwordLayout (gone по умолчанию) + loginHint |
| `MainActivity.kt` | Добавить роутинг по роли при старте |
| `ClientMainActivity.kt` | Создать новый |
| `ClientMainFragment.kt` | Создать новый |
| `ClientMainViewModel.kt` | Создать новый |
| `DeviceUtils.kt` | Создать если нет |

## Порядок реализации

1. Сначала серверная часть (PLAN_SERVER) — проверить все эндпоинты через curl или Postman
2. Добавить data-классы и API-методы в Retrofit
3. Обновить TokenStorage
4. Обновить LoginViewModel + LoginFragment — протестировать debounce и появление поля пароля
5. Создать ClientMainActivity + Fragment + ViewModel
6. Протестировать полный флоу: ввод логина заказчика → появление пароля → вход → список сотрудников
