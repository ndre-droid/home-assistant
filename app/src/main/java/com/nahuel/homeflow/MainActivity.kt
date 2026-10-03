package com.nahuel.homeflow

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.ui.HomeFlowTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.devices.SpotifyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nahuel.homeflow.engine.TriggerService
import com.nahuel.homeflow.ui.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    // When non-null, the next scanned NFC tag is written with this routine's launch URI.
    private var nfcWriteRoutineId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        TriggerService.sync(this)

        enableEdgeToEdge()
        setContent {
            val cfg by Store.config.collectAsState()
            HomeFlowTheme(themeMode = cfg.themeMode, dynamicColor = cfg.dynamicColor, accentHex = cfg.accentColor) {
                AppRoot(
                    nfcWriteRoutineId = nfcWriteRoutineId.value,
                    onRequestNfcWrite = { nfcWriteRoutineId.value = it },
                    onCancelNfcWrite = { nfcWriteRoutineId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** homeflow://run/<id> — from NFC tags or links. */
    private fun handleIntent(intent: Intent?) {
        // Tag write mode has priority over tag read
        val tag: Tag? = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val writeId = nfcWriteRoutineId.value
        if (tag != null && writeId != null) {
            writeTag(tag, writeId)
            return
        }
        val data = intent?.data ?: return
        if (data.scheme == "homeflow" && data.host == "spotify") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val ok = SpotifyClient.exchangeCode(code)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            this@MainActivity,
                            if (ok.isSuccess) "Spotify verbunden ✓"
                            else "Spotify: ${ok.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                Toast.makeText(this, "Spotify-Login abgebrochen", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (data.scheme == "homeflow" && data.host == "run") {
            data.lastPathSegment?.let { id ->
                RoutineEngine.runAsync(this, id)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foreground dispatch so tags reach us while the app is open (needed for writing).
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        adapter.enableForegroundDispatch(this, pi, null, null)
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
    }

    private fun writeTag(tag: Tag, routineId: String) {
        val msg = NdefMessage(arrayOf(NdefRecord.createUri("homeflow://run/$routineId")))
        val ok = runCatching {
            Ndef.get(tag)?.let { ndef ->
                ndef.connect(); ndef.writeNdefMessage(msg); ndef.close(); return@runCatching true
            }
            NdefFormatable.get(tag)?.let { fmt ->
                fmt.connect(); fmt.format(msg); fmt.close(); return@runCatching true
            }
            false
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (ok) "NFC-Tag beschrieben ✓" else "Tag konnte nicht beschrieben werden",
            Toast.LENGTH_SHORT
        ).show()
        if (ok) nfcWriteRoutineId.value = null
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    AUTOMATIONS("Automationen", Icons.Outlined.Bolt),
    DEVICES("Geräte", Icons.Outlined.GridView),
    SETTINGS("Einstellungen", Icons.Outlined.Tune)
}

@Composable
private fun AppRoot(
    nfcWriteRoutineId: String?,
    onRequestNfcWrite: (String) -> Unit,
    onCancelNfcWrite: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    var editRoutineId by remember { mutableStateOf<String?>(null) }   // null = list, "" = new
    var showEditor by remember { mutableStateOf(false) }
    var showCapture by remember { mutableStateOf(false) }
    val rooms by HomeRepo.rooms.collectAsState()
    val cfg by Store.config.collectAsState()

    // Live device state while the app is visible: full probe on start, then light polling.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            HomeRepo.refresh(includeTv = true)
            var n = 0
            while (isActive) {
                delay(6_000)
                n++
                HomeRepo.refresh(includeTv = n % 5 == 0)
            }
        }
    }
    LaunchedEffect(cfg) { HomeRepo.rebuild() }

    val navItems = Tab.entries.map { NavItem(it.label, it.icon) }
    val selectTab: (Int) -> Unit = { i ->
        val t = Tab.entries[i]
        if (t == tab && t == Tab.HOME) roomId = null   // tapping Home again goes back to the overview
        tab = t
    }

    @Composable
    fun TabContent(t: Tab, mod: Modifier, wide: Boolean) {
        when (t) {
            Tab.HOME -> HomeScreen(
                mod,
                selectedRoomId = if (wide) (roomId ?: rooms.firstOrNull()?.id) else null,
                onOpenRoom = { roomId = it },
                onOpenAutomations = { tab = Tab.AUTOMATIONS },
                onOpenDevices = { tab = Tab.DEVICES }
            )
            Tab.AUTOMATIONS -> AutomationsScreen(
                mod,
                onEdit = { id -> editRoutineId = id; showEditor = true },
                onCaptureScene = { showCapture = true }
            )
            Tab.DEVICES -> DevicesScreen(mod)
            Tab.SETTINGS -> SettingsScreen(mod)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wide = maxWidth >= 600.dp
        when {
            showCapture -> SceneCaptureScreen(onClose = { showCapture = false })
            showEditor -> EditRoutineScreen(
                routineId = editRoutineId,
                onClose = { showEditor = false },
                onRequestNfcWrite = onRequestNfcWrite
            )
            wide -> Row(Modifier.fillMaxSize().navigationBarsPadding()) {
                FlatNavRail(navItems, tab.ordinal, selectTab)
                if (tab == Tab.HOME) {
                    val sel = roomId?.takeIf { id -> rooms.any { it.id == id } } ?: rooms.firstOrNull()?.id
                    TabContent(Tab.HOME, Modifier.width(380.dp).fillMaxHeight(), wide = true)
                    Box(Modifier.fillMaxHeight().width(RuleWidth).background(Divider))
                    if (sel != null) RoomScreen(sel, Modifier.weight(1f).fillMaxHeight(), onBack = null)
                    else Box(Modifier.weight(1f).fillMaxHeight().background(Bg))
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        TabContent(tab, Modifier.widthIn(max = 720.dp), wide = true)
                    }
                }
            }
            tab == Tab.HOME && roomId != null -> {
                BackHandler { roomId = null }
                RoomScreen(roomId!!, Modifier.fillMaxSize(), onBack = { roomId = null })
            }
            else -> Scaffold(
                containerColor = Bg,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { FlatNavBar(navItems, tab.ordinal, selectTab) }
            ) { pad ->
                TabContent(tab, Modifier.padding(pad), wide = false)
            }
        }
    }

    if (nfcWriteRoutineId != null) {
        FlatDialog(
            onDismissRequest = onCancelNfcWrite,
            title = "NFC-Tag beschreiben",
            confirmButton = { GhostButton("Abbrechen", color = Muted, onClick = onCancelNfcWrite) },
            text = {
                Caption("Halte jetzt den NFC-Tag an die Rückseite deines Handys. Der Tag startet danach diese Automation – auch wenn die App geschlossen ist.")
            }
        )
    }
}
