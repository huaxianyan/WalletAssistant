package com.neko7ina.wallet.assistant

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.core.parser.RawDocument
import com.neko7ina.wallet.assistant.core.parser.normalizeOcrTextForStructuredParsing
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.screenshot.ScreenshotRecognitionResult
import com.neko7ina.wallet.assistant.settings.ReminderTimingConstraints
import com.neko7ina.wallet.assistant.settings.ThemeMode
import com.neko7ina.wallet.assistant.wallet.GoogleWalletPassFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class Screen {
    HOME,
    ARCHIVE,
    SETTINGS,
    GMAIL_IMPORT,
    TEXT_IMPORT,
    SCREENSHOT_IMPORT,
    CONFIRM,
}

private enum class ImportMode {
    EMAIL,
    SCREENSHOT,
}

private val Screen.depth: Int
    get() = when (this) {
        Screen.HOME -> 0
        Screen.CONFIRM -> 2
        else -> 1
    }

private enum class WalletAvailability {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWalletApp(
    requestGmailAuthorization: ((Result<String>) -> Unit) -> Unit,
    checkGoogleWalletAvailability: ((Boolean) -> Unit) -> Unit,
    addToGoogleWallet: (String) -> Unit,
    requestScreenshotRecognition: ((ScreenshotRecognitionResult) -> Unit) -> Unit,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    requestExactReminderPermission: ((Boolean) -> Unit) -> Unit,
    setDarkSystemBars: (Boolean) -> Unit,
    viewModel: TravelWalletViewModel = viewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val archivedDocuments by viewModel.archivedDocuments.collectAsStateWithLifecycle()
    val newTripsReminderEnabled by viewModel.newTripsReminderEnabled.collectAsStateWithLifecycle()
    val ignoreDepartedTripsOnImport by viewModel.ignoreDepartedTripsOnImport.collectAsStateWithLifecycle()
    val autoArchiveDepartedTrips by viewModel.autoArchiveDepartedTrips.collectAsStateWithLifecycle()
    val departureReminderMinutes by viewModel.departureReminderMinutes.collectAsStateWithLifecycle()
    val liveStatusMinutes by viewModel.liveStatusMinutes.collectAsStateWithLifecycle()
    val googleWalletActionVisible by viewModel.googleWalletActionVisible.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val gmailImportState by viewModel.gmailImportState.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var emailBody by rememberSaveable { mutableStateOf("") }
    var importMode by rememberSaveable { mutableStateOf(ImportMode.EMAIL) }
    var parseError by rememberSaveable { mutableStateOf<String?>(null) }
    var parsedDocument by remember { mutableStateOf<TravelDocument?>(null) }
    var confirmationSource by rememberSaveable { mutableStateOf(Screen.TEXT_IMPORT) }
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTrip = (documents + archivedDocuments).firstOrNull {
        it.document.stableId() == selectedTripId
    }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var walletAvailability by remember { mutableStateOf(WalletAvailability.CHECKING) }
    val walletPassFactory = remember { GoogleWalletPassFactory() }
    val context = LocalContext.current
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 && useDarkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        useDarkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    fun changeReminder(enabled: Boolean, apply: () -> Unit) {
        if (!enabled) {
            apply()
            return
        }
        requestNotificationPermission { notificationGranted ->
            if (!notificationGranted) {
                Toast.makeText(context, "请允许通知后再开启乘车提醒。", Toast.LENGTH_LONG).show()
                return@requestNotificationPermission
            }
            requestExactReminderPermission { exactReminderGranted ->
                if (exactReminderGranted) {
                    apply()
                } else {
                    Toast.makeText(context, "请允许精确提醒后再开启乘车提醒。", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun beginGmailImport() {
        screen = Screen.GMAIL_IMPORT
        viewModel.beginGmailAuthorization()
        requestGmailAuthorization { result ->
            result.fold(
                onSuccess = viewModel::loadFromGmail,
                onFailure = { viewModel.gmailAuthorizationFailed() },
            )
        }
    }

    LaunchedEffect(useDarkTheme) {
        setDarkSystemBars(useDarkTheme)
    }
    LaunchedEffect(Unit) {
        checkGoogleWalletAvailability { available ->
            walletAvailability = if (available) WalletAvailability.AVAILABLE else WalletAvailability.UNAVAILABLE
        }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.CONFIRM) confirmationSource else Screen.HOME
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState.depth > initialState.depth) {
                        (fadeIn() + slideInHorizontally { width -> width / 3 }) togetherWith
                            (fadeOut() + slideOutHorizontally { width -> -width / 3 })
                    } else {
                        (fadeIn() + slideInHorizontally { width -> -width / 3 }) togetherWith
                            (fadeOut() + slideOutHorizontally { width -> width / 3 })
                    }
                },
                label = "page transition",
            ) { targetScreen ->
                when (targetScreen) {
                Screen.HOME -> HomeScreen(
                    documents = documents,
                    onTripClick = { selectedTripId = it.document.stableId() },
                    onAddClick = { showAddSheet = true },
                    onArchiveClick = { screen = Screen.ARCHIVE },
                    onSettingsClick = { screen = Screen.SETTINGS },
                )

                Screen.ARCHIVE -> ArchiveScreen(
                    documents = archivedDocuments,
                    onBack = { screen = Screen.HOME },
                    onTripClick = { selectedTripId = it.document.stableId() },
                )

                Screen.SETTINGS -> SettingsScreen(
                    newTripsReminderEnabled = newTripsReminderEnabled,
                    ignoreDepartedTripsOnImport = ignoreDepartedTripsOnImport,
                    autoArchiveDepartedTrips = autoArchiveDepartedTrips,
                    departureReminderMinutes = departureReminderMinutes,
                    liveStatusMinutes = liveStatusMinutes,
                    googleWalletActionVisible = googleWalletActionVisible,
                    themeMode = themeMode,
                    onBack = { screen = Screen.HOME },
                    onNewTripsReminderChange = { enabled ->
                        changeReminder(enabled) { viewModel.setNewTripsReminderEnabled(enabled) }
                    },
                    onIgnoreDepartedTripsOnImportChange = viewModel::setIgnoreDepartedTripsOnImport,
                    onAutoArchiveDepartedTripsChange = viewModel::setAutoArchiveDepartedTrips,
                    onDepartureReminderMinutesChange = viewModel::setDepartureReminderMinutes,
                    onLiveStatusMinutesChange = viewModel::setLiveStatusMinutes,
                    onGoogleWalletVisibilityChange = viewModel::setGoogleWalletActionVisible,
                    onThemeModeChange = viewModel::setThemeMode,
                )

                Screen.GMAIL_IMPORT -> GmailImportScreen(
                    state = gmailImportState,
                    onBack = { screen = Screen.HOME },
                    onSelect = { document ->
                        parsedDocument = document
                        confirmationSource = Screen.GMAIL_IMPORT
                        screen = Screen.CONFIRM
                    },
                )

                Screen.TEXT_IMPORT -> ImportScreen(
                    importMode = importMode,
                    emailBody = emailBody,
                    error = parseError,
                    onBodyChange = {
                        emailBody = it
                        parseError = null
                    },
                    onUseSample = if (BuildConfig.DEBUG && importMode == ImportMode.EMAIL) {
                        {
                            emailBody = createUpcomingReminderSample()
                            parseError = null
                        }
                    } else {
                        null
                    },
                    onBack = { screen = Screen.HOME },
                    onParse = {
                        val textToParse = if (importMode == ImportMode.SCREENSHOT) {
                            normalizeOcrTextForStructuredParsing(emailBody)
                        } else {
                            emailBody
                        }
                        when (val result = ChinaRailwayEmailParser().parse(RawDocument(body = textToParse))) {
                            is ParseResult.Success -> {
                                if (ignoreDepartedTripsOnImport && result.document.hasDeparted()) {
                                    parsedDocument = null
                                    parseError = "这趟行程已经出发。如需保留历史行程，请在设置中关闭“忽略已过期行程”。"
                                } else {
                                    parsedDocument = result.document
                                    parseError = null
                                    confirmationSource = Screen.TEXT_IMPORT
                                    screen = Screen.CONFIRM
                                }
                            }

                            is ParseResult.Failure -> {
                                parseError = if (importMode == ImportMode.SCREENSHOT) {
                                    screenshotParseError(result.message)
                                } else {
                                    result.message
                                }
                            }
                        }
                    },
                )

                Screen.SCREENSHOT_IMPORT -> ScreenshotImportScreen(
                    onBack = { screen = Screen.HOME },
                )

                Screen.CONFIRM -> ConfirmationScreen(
                    document = requireNotNull(parsedDocument),
                    onBack = { screen = confirmationSource },
                    onSave = {
                        viewModel.save(requireNotNull(parsedDocument))
                        screen = Screen.HOME
                    },
                )
                }
            }
        }

        if (showAddSheet) {
            AddTripSheet(
                onDismiss = { showAddSheet = false },
                onGmailImport = {
                    showAddSheet = false
                    beginGmailImport()
                },
                onTextImport = {
                    showAddSheet = false
                    importMode = ImportMode.EMAIL
                    emailBody = ""
                    parseError = null
                    screen = Screen.TEXT_IMPORT
                },
                onScreenshotImport = {
                    showAddSheet = false
                    requestScreenshotRecognition { result ->
                        when (result) {
                            ScreenshotRecognitionResult.Processing -> {
                                screen = Screen.SCREENSHOT_IMPORT
                            }
                            is ScreenshotRecognitionResult.Success -> {
                                if (screen != Screen.SCREENSHOT_IMPORT) return@requestScreenshotRecognition
                                importMode = ImportMode.SCREENSHOT
                                emailBody = result.text
                                parseError = null
                                screen = Screen.TEXT_IMPORT
                            }
                            ScreenshotRecognitionResult.Cancelled -> screen = Screen.HOME
                            ScreenshotRecognitionResult.Failed -> {
                                screen = Screen.HOME
                                Toast.makeText(
                                    context,
                                    "未能读取截图，请重新选择清晰图片或改用粘贴正文。",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                },
            )
        }

        selectedTrip?.let { saved ->
            TripDetailDialog(
                saved = saved,
                walletAvailability = walletAvailability,
                showGoogleWalletAction = googleWalletActionVisible,
                departureReminderMinutes = departureReminderMinutes,
                liveStatusMinutes = liveStatusMinutes,
                onDismiss = { selectedTripId = null },
                onReminderChange = { enabled ->
                    changeReminder(enabled) { viewModel.setReminderEnabled(saved, enabled) }
                },
                onTestReminder = if (BuildConfig.DEBUG && saved.reminderEnabled) {
                    {
                        viewModel.scheduleReminderTest(saved.document)
                        Toast.makeText(context, "测试提醒将在 10 秒后显示。", Toast.LENGTH_LONG).show()
                    }
                } else {
                    null
                },
                onAddToGoogleWallet = {
                    addToGoogleWallet(walletPassFactory.createUnsignedPass(saved.document))
                },
                onArchive = {
                    viewModel.setArchived(saved, true)
                    selectedTripId = null
                },
                onRestore = {
                    viewModel.setArchived(saved, false)
                    selectedTripId = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    documents: List<SavedTravelDocument>,
    onTripClick: (SavedTravelDocument) -> Unit,
    onAddClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("我的行程") },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("已归档行程") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onArchiveClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onSettingsClick()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "添加行程")
            }
        },
    ) { contentPadding ->
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有行程", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "点击右下角添加行程",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(documents, key = { it.document.stableId() }) { saved ->
                    CompactTripCard(
                        saved = saved,
                        onClick = { onTripClick(saved) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(
    documents: List<SavedTravelDocument>,
    onBack: () -> Unit,
    onTripClick: (SavedTravelDocument) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("已归档行程", onBack) },
    ) { contentPadding ->
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有归档行程", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(documents, key = { it.document.stableId() }) { saved ->
                    CompactTripCard(
                        saved = saved,
                        onClick = { onTripClick(saved) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CompactTripCard(
    saved: SavedTravelDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segment = saved.document.segments.first()
    val seat = segment.seatAssignments.firstOrNull()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    segment.serviceNumber,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    segment.departureTime.format(COMPACT_DATE_FORMAT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${segment.origin.name} → ${segment.destination.name}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    segment.departureTime.format(COMPACT_TIME_FORMAT),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    seat?.let { "${it.section} 车 ${it.seat}" } ?: "座位待确认",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TripDetailDialog(
    saved: SavedTravelDocument,
    walletAvailability: WalletAvailability,
    showGoogleWalletAction: Boolean,
    departureReminderMinutes: Int,
    liveStatusMinutes: Int,
    onDismiss: () -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onTestReminder: (() -> Unit)?,
    onAddToGoogleWallet: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    val document = saved.document
    val segment = document.segments.first()
    val seat = segment.seatAssignments.firstOrNull()
    val hasDeparted = document.hasDeparted()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    segment.serviceNumber,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${segment.origin.name} → ${segment.destination.name}",
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                DetailRow("出发", segment.departureTime.format(DEPARTURE_FORMAT))
                segment.arrivalTime?.let { DetailRow("到达", it.format(DEPARTURE_FORMAT)) }
                DetailRow("座位", seat?.let { "${it.section} 车 ${it.seat}" } ?: "待确认")
                seat?.category?.let { DetailRow("席别", it) }
                DetailRow("乘车人", document.travelers.joinToString("、") { it.name })
                DetailRow("订单", document.reservation.reference)
                DetailRow("来源", document.provider.name)

                if (!saved.archived) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("乘车提醒", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hasDeparted) {
                                    "行程已结束，无法开启提醒"
                                } else {
                                    "发车前 ${formatLeadTime(departureReminderMinutes)}提醒，" +
                                        "发车前 ${formatLeadTime(liveStatusMinutes)}显示实时状态"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = saved.reminderEnabled && !hasDeparted,
                            onCheckedChange = onReminderChange,
                            enabled = !hasDeparted,
                        )
                    }
                }

                if (onTestReminder != null && !hasDeparted) {
                    TextButton(
                        onClick = onTestReminder,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("测试锁屏提醒")
                    }
                }

                if (showGoogleWalletAction && !saved.archived) {
                    when (walletAvailability) {
                        WalletAvailability.AVAILABLE -> GoogleWalletButton(
                            onClick = onAddToGoogleWallet,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp),
                        )

                        WalletAvailability.UNAVAILABLE -> Text(
                            "请安装或更新 Google Wallet 后重试。",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )

                        WalletAvailability.CHECKING -> Unit
                    }
                }

                if (saved.archived) {
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    ) {
                        Text("恢复到我的行程")
                    }
                } else {
                    OutlinedButton(
                        onClick = onArchive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    ) {
                        Text("归档行程")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTripSheet(
    onDismiss: () -> Unit,
    onGmailImport: () -> Unit,
    onTextImport: () -> Unit,
    onScreenshotImport: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "添加行程",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        ListItem(
            headlineContent = { Text("从 Gmail 导入") },
            supportingContent = { Text("查找已授权邮箱中的购票成功邮件") },
            leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onGmailImport),
        )
        ListItem(
            headlineContent = { Text("粘贴邮件正文") },
            supportingContent = { Text("手动粘贴购票成功邮件内容") },
            leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onTextImport),
        )
        ListItem(
            headlineContent = { Text("从截图识别") },
            supportingContent = { Text("选择图片并在设备上读取文字") },
            leadingContent = { Icon(Icons.Default.ImageSearch, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onScreenshotImport),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    newTripsReminderEnabled: Boolean,
    ignoreDepartedTripsOnImport: Boolean,
    autoArchiveDepartedTrips: Boolean,
    departureReminderMinutes: Int,
    liveStatusMinutes: Int,
    googleWalletActionVisible: Boolean,
    themeMode: ThemeMode,
    onBack: () -> Unit,
    onNewTripsReminderChange: (Boolean) -> Unit,
    onIgnoreDepartedTripsOnImportChange: (Boolean) -> Unit,
    onAutoArchiveDepartedTripsChange: (Boolean) -> Unit,
    onDepartureReminderMinutesChange: (Int) -> Unit,
    onLiveStatusMinutesChange: (Int) -> Unit,
    onGoogleWalletVisibilityChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("设置", onBack) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                Text(
                    "导入",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingSwitch(
                    title = "忽略已过期行程",
                    description = "导入时跳过已经出发的行程",
                    checked = ignoreDepartedTripsOnImport,
                    onCheckedChange = onIgnoreDepartedTripsOnImportChange,
                )
            }
            item {
                Text(
                    "行程管理",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingSwitch(
                    title = "自动归档已结束行程",
                    description = "只在未来行程出发时归档，不追溯已结束行程",
                    checked = autoArchiveDepartedTrips,
                    onCheckedChange = onAutoArchiveDepartedTripsChange,
                )
            }
            item {
                Text(
                    "乘车提醒",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingSwitch(
                    title = "新行程默认提醒",
                    description = "导入新行程时自动开启乘车提醒",
                    checked = newTripsReminderEnabled,
                    onCheckedChange = onNewTripsReminderChange,
                )
            }
            item {
                ReminderTimingSlider(
                    title = "发车前提醒",
                    minutes = departureReminderMinutes,
                    minimum = ReminderTimingConstraints.DEPARTURE_MIN_MINUTES,
                    maximum = ReminderTimingConstraints.DEPARTURE_MAX_MINUTES,
                    onMinutesChange = onDepartureReminderMinutesChange,
                )
            }
            item {
                ReminderTimingSlider(
                    title = "显示实时状态",
                    minutes = liveStatusMinutes,
                    minimum = ReminderTimingConstraints.LIVE_MIN_MINUTES,
                    maximum = ReminderTimingConstraints.LIVE_MAX_MINUTES,
                    onMinutesChange = onLiveStatusMinutesChange,
                )
            }
            item {
                SettingSwitch(
                    title = "显示 Google Wallet",
                    description = "在行程详情中显示添加至 Google Wallet 的入口",
                    checked = googleWalletActionVisible,
                    onCheckedChange = onGoogleWalletVisibilityChange,
                )
            }
            item {
                Text(
                    "外观",
                    modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(ThemeMode.entries) { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeModeChange(mode) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                    )
                    Text(label, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderTimingSlider(
    title: String,
    minutes: Int,
    minimum: Int,
    maximum: Int,
    onMinutesChange: (Int) -> Unit,
) {
    var pendingMinutes by remember(minutes) { mutableStateOf(minutes) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                formatLeadTime(pendingMinutes),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = pendingMinutes.toFloat(),
            onValueChange = { value ->
                pendingMinutes = (
                    value / ReminderTimingConstraints.STEP_MINUTES
                    ).roundToInt() * ReminderTimingConstraints.STEP_MINUTES
            },
            onValueChangeFinished = { onMinutesChange(pendingMinutes) },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = (maximum - minimum) / ReminderTimingConstraints.STEP_MINUTES - 1,
        )
        Text(
            "可设置为 ${formatLeadTime(minimum)}至 ${formatLeadTime(maximum)}，每次调整 15 分钟",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GmailImportScreen(
    state: GmailImportState,
    onBack: () -> Unit,
    onSelect: (TravelDocument) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("从 Gmail 导入", onBack) },
    ) { contentPadding ->
        when (state) {
            GmailImportState.Idle,
            GmailImportState.Authorizing,
            GmailImportState.Loading,
            -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        if (state == GmailImportState.Loading) "正在查找购票邮件…" else "正在连接 Gmail…",
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            is GmailImportState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("无法导入邮件", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.message,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    OutlinedButton(onClick = onBack) { Text("返回") }
                }
            }

            is GmailImportState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.documents.isEmpty()) {
                    item {
                        Text(
                            "最近两年没有找到可识别的购票成功邮件。",
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    items(state.documents) { document ->
                        CompactTripCard(
                            saved = SavedTravelDocument(document, false, false),
                            onClick = { onSelect(document) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotImportScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("截图识别", onBack) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "正在读取截图…",
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportScreen(
    importMode: ImportMode,
    emailBody: String,
    error: String?,
    onBodyChange: (String) -> Unit,
    onUseSample: (() -> Unit)?,
    onBack: () -> Unit,
    onParse: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            PageTopBar(
                if (importMode == ImportMode.SCREENSHOT) "检查识别文字" else "粘贴邮件正文",
                onBack,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                if (importMode == ImportMode.SCREENSHOT) {
                    "请检查截图中读取出的文字，修正错误后再识别行程。"
                } else {
                    "粘贴购票成功邮件的正文，解析后请核对乘车信息。"
                },
            )
            OutlinedTextField(
                value = emailBody,
                onValueChange = onBodyChange,
                label = {
                    Text(if (importMode == ImportMode.SCREENSHOT) "识别文字" else "邮件正文")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
                    .padding(top = 20.dp),
            )
            if (onUseSample != null) {
                TextButton(
                    onClick = onUseSample,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("填入提醒测试数据")
                }
            }
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onParse,
                enabled = emailBody.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(if (importMode == ImportMode.SCREENSHOT) "识别行程" else "解析邮件")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationScreen(
    document: TravelDocument,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("确认行程", onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("请确认邮件中的信息是否识别正确。")
            TripInformation(
                document = document,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text("保存行程")
            }
        }
    }
}

@Composable
private fun TripInformation(document: TravelDocument, modifier: Modifier = Modifier) {
    val segment = document.segments.first()
    val seat = segment.seatAssignments.firstOrNull()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(segment.serviceNumber, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${segment.origin.name} → ${segment.destination.name}",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            DetailRow("出发", segment.departureTime.format(DEPARTURE_FORMAT))
            DetailRow("座位", seat?.let { "${it.section} 车 ${it.seat}" } ?: "待确认")
            seat?.category?.let { DetailRow("席别", it) }
            DetailRow("乘车人", document.travelers.joinToString("、") { it.name })
            DetailRow("订单", document.reservation.reference)
        }
    }
}

@Composable
private fun GoogleWalletButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .padding(8.dp)
            .widthIn(min = 270.dp)
            .height(49.dp)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color(0xFF5F6368), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.add_to_google_wallet_button_foreground),
            contentDescription = "添加至 Google Wallet",
            modifier = Modifier
                .width(227.dp)
                .height(26.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun screenshotParseError(message: String): String = when {
    message.startsWith("这封邮件") -> "截图文字中没有找到完整的铁路购票信息，请检查识别文字。"
    else -> message
        .replace("邮件中", "截图文字中")
        .replace("邮件内容", "识别文字")
        .replace("邮件显示", "截图文字显示")
}

private fun formatLeadTime(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "$minutes 分钟"
        remainingMinutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分钟"
    }
}

private val DEPARTURE_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm")
private val COMPACT_DATE_FORMAT = DateTimeFormatter.ofPattern("M 月 d 日")
private val COMPACT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val SAMPLE_EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
private val SAMPLE_EMAIL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH:mm")
private val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val TEST_STATION_NAMES = listOf(
    "苹果站",
    "香蕉站",
    "橙子站",
    "葡萄站",
    "芒果站",
    "桃子站",
    "草莓站",
    "西瓜站",
)
private val TEST_SEAT_LETTERS = charArrayOf('A', 'B', 'C', 'D', 'F')

private fun createUpcomingReminderSample(): String {
    val now = ZonedDateTime.now(SHANGHAI_ZONE)
    val departure = now
        .plusMinutes(Random.nextLong(24 * 60L, 48 * 60L + 1))
        .withSecond(0)
        .withNano(0)
    val origin = TEST_STATION_NAMES.random()
    val destination = TEST_STATION_NAMES.filterNot { it == origin }.random()
    val serviceNumber = "G${Random.nextInt(1_000, 10_000)}"
    val carriage = Random.nextInt(1, 13)
    val seat = "${Random.nextInt(1, 25)}${TEST_SEAT_LETTERS.random()}"
    val reservationReference = "E${Random.nextLong(100_000_000L, 1_000_000_000L)}"
    return """
        尊敬的 测试乘客先生：
        您好！
        您于${now.format(SAMPLE_EMAIL_DATE_FORMAT)}在中国铁路客户服务中心网站(12306.cn)成功购买了1张车票，票款共计120.00元，订单号码 $reservationReference。所购车票信息如下：
        1.测试乘客，${departure.format(SAMPLE_EMAIL_DATE_TIME_FORMAT)}开，$origin-$destination，${serviceNumber}次列车，${carriage}车${seat}号，二等座，成人票，票价120.0元，电子客票。
    """.trimIndent()
}
