@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
        setContent {
            MeshRankerTheme {
                AppRoot(store, this)
            }
        }
    }
}

@Composable
fun MeshRankerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mesh_settings", android.content.Context.MODE_PRIVATE) }
    var themeId by remember { mutableStateOf(prefs.getString("theme_id", AppTheme.DEFAULT_ID) ?: AppTheme.DEFAULT_ID) }
    var themeColors by remember { mutableStateOf(prefs.getString("theme_colors", null)) }
    var design by remember { mutableStateOf(Design.migrate(prefs)) }
    val glass = design == Design.GLASS
    val liquid = design == Design.LIQUID
    val frosted = design == Design.FROSTED
    val language = UiLanguage.code
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val localized = remember(context, configuration, language) { UiLanguage.context(context) }
    val dark = isSystemInDarkTheme()
    val baseColors = remember(themeId, themeColors, dark) {
        if (themeId == "dynamic") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                // Material You (динамические цвета) есть только на Android 12+.
                AppTheme.colors(if (dark) AppTheme.DEFAULT_ID else "light", null)
            }
        } else {
            AppTheme.colors(themeId, themeColors)
        }
    }
    val colors = remember(baseColors, design) {
        when (design) {
            Design.GLASS -> Glass.glassify(baseColors)
            Design.FROSTED -> Frosted.scheme(baseColors)
            else -> baseColors
        }
    }
    val glassStyle = remember(themeId, baseColors) { Glass.style(themeId, baseColors) }
    // В стекле карточки скруглены сильнее (20dp) — крупные мягкие углы, как у пластины.
    // В «матовом стекле» — ровные 16dp у карточек, как в Uxaoro.
    val shapes = remember(glass, frosted) {
        Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(if (glass) 20.dp else 16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(
                when {
                    glass -> 28.dp
                    frosted -> 16.dp
                    else -> 24.dp
                }
            ),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(if (frosted) 24.dp else 32.dp)
        )
    }

    val applyTheme: (String) -> Unit = { id ->
        themeId = id
        prefs.edit().putString("theme_id", id).apply()
        // Перечитываем пользовательские цвета: их могли изменить в настройках.
        themeColors = prefs.getString("theme_colors", null)
    }
    val applyLanguage: (String) -> Unit = { code ->
        UiLanguage.select(code)
        (context.applicationContext as MeshApp).createNotificationChannel()
        prefs.edit().putString("language", code).apply()
    }
    val applyDesign: (String) -> Unit = { value ->
        design = value
        prefs.edit().putString(Design.PREF_KEY, value).apply()
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        androidx.compose.ui.platform.LocalConfiguration provides localized.resources.configuration,
        LocalAppTheme provides applyTheme,
        LocalAppLanguageSetter provides applyLanguage,
        LocalDesign provides design,
        LocalDesignSetter provides applyDesign,
        LocalGlass provides glass,
        LocalGlassStyle provides glassStyle
    ) {
        MaterialTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            shapes = shapes
        ) {
            when {
                liquid -> LiquidBackdrop(content)
                frosted -> FrostedBackdrop {
                    // Фон рисует FrostedBackdrop: прозрачная Surface задаёт только цвет текста.
                    Surface(color = androidx.compose.ui.graphics.Color.Transparent, content = content)
                }
                glass -> GlassBackdrop {
                    // Фон рисует GlassBackdrop: прозрачная Surface задаёт только цвет текста.
                    Surface(color = androidx.compose.ui.graphics.Color.Transparent, content = content)
                }
                else -> Surface(color = MaterialTheme.colorScheme.background, content = content)
            }
        }
    }
}

@Composable
fun AppRoot(store: SessionStore, activity: MainActivity) {
    var token by remember { mutableStateOf(store.token ?: "") }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var activeStudentId by remember { mutableStateOf(store.studentId) }
    var activePersonId by remember { mutableStateOf(store.personId) }
    var booting by remember { mutableStateOf(store.token != null && (store.studentId == null || store.personId == null)) }
    var bootError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(if (store.token != null) "main" else "login") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val client = remember {
        MeshClient(
            baseUrl = MeshCore.HOST,
            token = store.token,
            profileId = store.studentId,
            nameCache = store.loadNameCache(),
            onToken = { newToken -> store.token = newToken },
            onName = { id, name -> store.saveName(id, name) }
        )
    }

    fun applyProfile(prof: JSONObject): Boolean {
        profile = prof
        val ch = (0 until (prof.optJSONArray("children")?.length() ?: 0))
            .mapNotNull { prof.optJSONArray("children")?.optJSONObject(it) }
        val child = ch.firstOrNull()
        val studentId = child?.optString("id")?.takeIf { it.isNotBlank() }
        val personId = child?.optString("personId")?.takeIf { it.isNotBlank() }
            ?: child?.optString("contingent_guid")?.takeIf { it.isNotBlank() }
        if (studentId != null) {
            activeStudentId = studentId
            store.studentId = studentId
            store.profileId = studentId
            client.profileId = studentId
        }
        if (personId != null) {
            activePersonId = personId
            store.personId = personId
        }
        store.save(
            store.token,
            studentId,
            studentId,
            personId,
            prof.optJSONObject("parent")?.optString("name")
        )
        return studentId != null && personId != null
    }

    fun logout() {
        store.clear()
        token = ""
        profile = null
        activeStudentId = null
        activePersonId = null
        client.token = null
        client.profileId = null
        screen = "login"
        bootError = null
        booting = false
        loginError = null
    }

    // Проверка обновлений на GitHub Releases: не чаще одного раза в 15 минут.
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (now - store.lastUpdateCheck < UpdateChecker.CHECK_INTERVAL_MS) return@LaunchedEffect
        store.lastUpdateCheck = now
        val found = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
        if (found != null) updateInfo = found
    }

    // При каждом старте приложения сначала добираем профиль. Если id ученика ещё нет,
    // экран рейтинга не показываем — иначе первый запрос уходит с пустым personId и МЭШ отвечает 400.
    LaunchedEffect(store.token, retryKey) {
        val savedToken = store.token
        if (savedToken.isNullOrBlank()) {
            screen = "login"
            booting = false
            return@LaunchedEffect
        }
        client.token = savedToken
        client.profileId = store.studentId
        val hasIds = !store.studentId.isNullOrBlank() && !store.personId.isNullOrBlank()
        if (!hasIds) {
            booting = true
            bootError = null
        }
        try {
            val prof = withContext(Dispatchers.IO) { client.fetchProfile() }
            if (!applyProfile(prof)) {
                bootError = Words.NoStudentInThisProfileSignInAgain.text
            } else {
                bootError = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val meshError = e as? MeshCore.MeshError
            if (meshError?.code == "unauthorized") {
                // Токен устарел: уходим на экран входа, но куки НЕ чистим —
                // QR-панель восстановит по ним сессию без повторного QR.
                screen = "login"
                booting = false
                bootError = null
                return@LaunchedEffect
            }
            if (!hasIds) bootError = MeshCore.describeError(e)
        } finally {
            if (isActive) booting = false
        }
    }

    updateInfo?.let { info ->
        AlertDialog(
            modifier = Modifier.dialogGlass(),
            containerColor = Color.Transparent,
            onDismissRequest = { updateInfo = null },
            title = { Text(Words.UpdateAvailable.text) },
            text = {
                Text(
                    Words.UpdateVersionAvailable(info.versionName)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateInfo = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                        }
                    }
                ) {
                    Text(Words.Download.text)
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text(Words.Later.text)
                }
            }
        )
    }

    when {
        screen == "login" -> LoginScreen(
            onLogin = { rawToken ->
                loginLoading = true
                loginError = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { client.signIn(rawToken) }
                        val tokenValue = result.optString("token", rawToken)
                        val prof = result.optJSONObject("profile")
                            ?: withContext(Dispatchers.IO) { client.fetchProfile() }
                        store.token = tokenValue
                        client.token = tokenValue
                        token = tokenValue
                        if (!applyProfile(prof)) {
                            throw MeshCore.MeshError(
                                Words.NoStudentFoundSignInAsAParent.text,
                                "no-student"
                            )
                        }
                        bootError = null
                        screen = "main"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loginError = MeshCore.describeError(e)
                    } finally {
                        if (isActive) loginLoading = false
                    }
                }
            },
            loading = loginLoading,
            error = loginError
        )

        booting -> BootScreen()

        bootError != null && (activePersonId == null || activeStudentId == null) -> BootErrorScreen(
            message = bootError ?: Words.CouldNotLoadProfile.text,
            onRetry = { retryKey++ },
            onLogout = { logout() }
        )

        else -> MainScaffold(
            store = store,
            client = client,
            profile = profile,
            personId = activePersonId,
            studentId = activeStudentId,
            onLogout = { logout() }
        )
    }
}

@Composable
fun BootScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingIndicator()
        Spacer(Modifier.height(16.dp))
        Text(Words.LoadingMeshProfile.text)
    }
}

@Composable
fun BootErrorScreen(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(Words.CouldNotOpenProfile.text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(Words.Retry.text) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onLogout) { Text(Words.SignInAgain.text) }
    }
}

/** Состояние экрана для истории кнопки/жеста «назад». */
private data class NavState(
    val tab: String,
    val rankingSubjectId: String? = null,
    val marksSubjectId: String? = null,
    val marksSubjectName: String? = null,
    val target: HomeworkTarget? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    store: SessionStore,
    client: MeshClient,
    profile: JSONObject?,
    personId: String?,
    studentId: String?,
    onLogout: () -> Unit
) {
    var currentTab by remember { mutableStateOf("ranking") }
    val hapticView = LocalView.current
    // Вибрация при нажатиях: общий переключатель в Настройках (mesh_settings),
    // читается при каждом событии — применяется сразу после переключения.
    val hapticContext = LocalContext.current
    val hapticPrefs = remember(hapticContext) {
        hapticContext.getSharedPreferences("mesh_settings", android.content.Context.MODE_PRIVATE)
    }
    fun hapticTap() {
        if (hapticPrefs.getBoolean("haptics_enabled", true)) {
            hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    var rankingSubjectId by remember { mutableStateOf<String?>(null) }
    var marksSubjectId by remember { mutableStateOf<String?>(null) }
    var marksSubjectName by remember { mutableStateOf<String?>(null) }
    var homeworkTarget by remember { mutableStateOf<HomeworkTarget?>(null) }
    val backStack = remember { mutableStateListOf<NavState>() }

    fun currentState() = NavState(
        tab = currentTab,
        rankingSubjectId = rankingSubjectId,
        marksSubjectId = marksSubjectId,
        marksSubjectName = marksSubjectName,
        target = homeworkTarget
    )

    fun navigateTo(next: NavState) {
        if (next == currentState()) return
        backStack.add(currentState())
        currentTab = next.tab
        rankingSubjectId = next.rankingSubjectId
        marksSubjectId = next.marksSubjectId
        marksSubjectName = next.marksSubjectName
        homeworkTarget = next.target
    }

    val hasContext = backStack.isNotEmpty() || rankingSubjectId != null ||
        marksSubjectId != null || homeworkTarget != null

    // Жест/кнопка «назад» сначала проходят по истории экранов, а не сворачивают приложение.
    BackHandler(enabled = hasContext) {
        hapticTap()
        if (backStack.isNotEmpty()) {
            val previous = backStack.removeAt(backStack.lastIndex)
            currentTab = previous.tab
            rankingSubjectId = previous.rankingSubjectId
            marksSubjectId = previous.marksSubjectId
            marksSubjectName = previous.marksSubjectName
            homeworkTarget = previous.target
        } else {
            rankingSubjectId = null
            marksSubjectId = null
            marksSubjectName = null
            homeworkTarget = null
        }
    }

    val screenTitle = when (currentTab) {
        "ranking" -> Words.Ranking.text
        "marks" -> if (marksSubjectName.isNullOrBlank()) Words.GradesTab.text else "${Words.GradesTab.text} · $marksSubjectName"
        "homeworks" -> Words.HomeworkLabel.text
        "schedule" -> Words.Schedule.text
        "moskvenok" -> Words.Moskvenok.text
        "food" -> Words.MealsAndBalance.text
        "settings" -> Words.Settings.text
        else -> Words.MeshRanking.text
    }

    val design = LocalDesign.current
    val glass = design == Design.GLASS
    val liquid = design == Design.LIQUID
    val frosted = design == Design.FROSTED
    val glassStyle = LocalGlassStyle.current
    val navItems = listOf(
        Triple("ranking", Words.Ranking.text, R.drawable.ic_nav_ranking),
        Triple("marks", Words.GradesTab.text, R.drawable.ic_nav_marks),
        Triple("homeworks", Words.Tasks.text, R.drawable.ic_nav_homeworks),
        Triple("schedule", Words.ScheduleTab.text, R.drawable.ic_nav_schedule),
        Triple("moskvenok", Words.Moskvenok.text, R.drawable.ic_nav_moskvenok)
    )
    val topActions: @Composable RowScope.() -> Unit = {
        if (currentTab == "marks" && marksSubjectId != null) {
            TextButton(
                onClick = {
                    navigateTo(NavState(tab = "ranking", rankingSubjectId = marksSubjectId))
                }
            ) {
                Text(Words.Ranking.text)
            }
        }
        IconButton(onClick = { navigateTo(NavState(tab = "settings")) }) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = Words.SettingsAndProfile.text
            )
        }
    }
    Scaffold(
        containerColor = if (glass || liquid || frosted) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Final)
                    if (downEvent.type != PointerEventType.Press) continue
                    val downPos = downEvent.changes.firstOrNull()?.position ?: continue
                    var isTap = true
                    var consumed = downEvent.changes.any { it.isConsumed }
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        when (event.type) {
                            PointerEventType.Release -> {
                                if (isTap && (consumed || event.changes.any { it.isConsumed })) {
                                    hapticTap()
                                }
                                break
                            }
                            PointerEventType.Move -> {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null && (pos - downPos).getDistance() > 24f) {
                                    isTap = false
                                }
                                if (event.changes.any { it.isConsumed }) consumed = true
                            }
                            else -> {}
                        }
                    }
                }
            }
        },
        topBar = {
            if (frosted) {
                FrostedTopBar(screenTitle, topActions)
                return@Scaffold
            }
            TopAppBar(
                // В стекле и liquid-дизайне шапка прозрачная: под ней виден фон со свечениями.
                colors = if (glass || liquid) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                title = {
                    Text(screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = topActions
            )
        },
        bottomBar = {
            if (frosted) {
                FrostedNavBar(
                    items = navItems.map { (key, label, iconRes) -> FrostedNavItem(key, label, painterResource(iconRes)) },
                    selected = currentTab,
                    onSelect = { navigateTo(NavState(tab = it)) }
                )
                return@Scaffold
            }
            ShortNavigationBar(
                modifier = when {
                    // Нижняя панель в стекле — стеклянная пластина с тонкой светлой кромкой сверху.
                    glass -> Modifier.glassBar(glassStyle.dark)
                    // В liquid-дизайне панель — плавающая стеклянная пластина без системных отступов.
                    liquid -> Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp).glassPane()
                    else -> Modifier
                },
                windowInsets = if (liquid) WindowInsets(0, 0, 0, 0) else ShortNavigationBarDefaults.windowInsets,
                containerColor = when {
                    glass -> androidx.compose.ui.graphics.Color.White.copy(alpha = if (glassStyle.dark) 0.06f else 0.55f)
                    liquid -> Color.Transparent
                    else -> ShortNavigationBarDefaults.containerColor
                }
            ) {
                navItems.forEach { (key, label, iconRes) ->
                    ShortNavigationBarItem(
                        selected = currentTab == key,
                        onClick = { navigateTo(NavState(tab = key)) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = label
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentTab == "schedule",
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.6f) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.6f) +
                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                ExtendedFloatingActionButton(
                    // В «матовом стекле» — «чернильная» кнопка (цвет текста на фоне) со скруглением 16dp.
                    containerColor = if (frosted) MaterialTheme.colorScheme.onBackground else FloatingActionButtonDefaults.containerColor,
                    contentColor = if (frosted) MaterialTheme.colorScheme.background else contentColorFor(FloatingActionButtonDefaults.containerColor),
                    shape = if (frosted) Frosted.CardShape else FloatingActionButtonDefaults.extendedFabShape,
                    text = { Text(Words.Meals.text) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_food),
                            contentDescription = null
                        )
                    },
                    onClick = { navigateTo(NavState(tab = "food")) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            val motion = MaterialTheme.motionScheme
            val tabOrder = listOf("ranking", "marks", "homeworks", "schedule", "moskvenok", "food", "settings")
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val forward = tabOrder.indexOf(targetState) >= tabOrder.indexOf(initialState)
                    val slide = motion.defaultSpatialSpec<IntOffset>()
                    val fade = motion.defaultEffectsSpec<Float>()
                    val scale = motion.defaultSpatialSpec<Float>()
                    val enterX: (Int) -> Int = { w -> (if (forward) w else -w) / 4 }
                    val exitX: (Int) -> Int = { w -> (if (forward) -w else w) / 4 }
                    (
                        slideInHorizontally(animationSpec = slide, initialOffsetX = enterX) +
                            fadeIn(animationSpec = fade) +
                            scaleIn(animationSpec = scale, initialScale = 0.96f)
                    ) togetherWith (
                        slideOutHorizontally(animationSpec = slide, targetOffsetX = exitX) +
                            fadeOut(animationSpec = fade) +
                            scaleOut(animationSpec = scale, targetScale = 0.96f)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    "ranking" -> RankingScreen(
                        store,
                        client,
                        personId,
                        onLogout,
                        selectedSubjectId = rankingSubjectId,
                        onSubjectChange = { rankingSubjectId = it }
                    )
                    "marks" -> MarksScreen(
                        store,
                        client,
                        studentId,
                        personId = personId,
                        subjectId = marksSubjectId,
                        onOpenSubject = { id, name ->
                            if (id.isNotBlank()) {
                                navigateTo(
                                    NavState(
                                        tab = "marks",
                                        marksSubjectId = id,
                                        marksSubjectName = name
                                    )
                                )
                            }
                        }
                    )
                    "homeworks" -> HomeworksScreen(
                        store,
                        client,
                        studentId,
                        target = homeworkTarget,
                        onTargetConsumed = { homeworkTarget = null }
                    )
                    "schedule" -> ScheduleScreen(
                        store,
                        client,
                        studentId,
                        personId = personId,
                        onHomeworkClick = { navigateTo(NavState(tab = "homeworks", target = it)) },
                        onFoodClick = { navigateTo(NavState(tab = "food")) }
                    )
                    "moskvenok" -> MoskvenokScreen(store)
                    "food" -> FoodScreen(store, client, personId)
                    "settings" -> SettingsScreen(store, client, profile, onLogout)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String) -> Unit, loading: Boolean, error: String?) {
    var tokenInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val url = MeshCore.loginUrl()
    val webLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = result.data?.getStringExtra(SchoolLoginActivity.EXTRA_TOKEN)
            if (!token.isNullOrBlank()) onLogin(token)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.align(Alignment.End)) { LanguagePicker() }
        Text(Words.SignInToMesh.text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))

        // Способ 1 — QR: панель сама чинит устаревший токен по сохранённым кукам
        // и только при неудаче показывает QR-ссылку.
        QrLoginPanel(onLogin = onLogin)

        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().glassPane().padding(16.dp)) {
            Text(
                Words.SignInOnTheMeshWebsite.text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                Words.SignInAtSchoolMosRuThisWindow.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    webLogin.launch(Intent(context, SchoolLoginActivity::class.java))
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(Words.OpenSchoolMosRu.text)
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().glassPane().padding(16.dp)) {
            Text(
                Words.EnterATokenManually.text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Words.Open(url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            )
            Text(
                Words.CopyTheTokenFromThePageAndPaste.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text(Words.MeshToken.text) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onLogin(tokenInput) },
                enabled = !loading && tokenInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text(Words.SignInWithToken.text)
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}