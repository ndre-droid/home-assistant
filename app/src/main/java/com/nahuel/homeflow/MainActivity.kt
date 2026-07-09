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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService
import com.nahuel.homeflow.ui.*

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

private enum class Tab(val label: String) { AUTOMATIONS("Automationen"), DEVICES("Geräte"), SETTINGS("Einstellungen") }

@Composable
private fun AppRoot(
    nfcWriteRoutineId: String?,
    onRequestNfcWrite: (String) -> Unit,
    onCancelNfcWrite: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.AUTOMATIONS) }
    var editRoutineId by remember { mutableStateOf<String?>(null) }   // null = list, "" = new
    var showEditor by remember { mutableStateOf(false) }
    var showCapture by remember { mutableStateOf(false) }

    if (showCapture) {
        SceneCaptureScreen(onClose = { showCapture = false })
    } else if (showEditor) {
        EditRoutineScreen(
            routineId = editRoutineId,
            onClose = { showEditor = false },
            onRequestNfcWrite = onRequestNfcWrite
        )
    } else {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                androidx.compose.foundation.layout.Column {
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp)
                    NavigationBar(containerColor = Bg, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                label = { Text(t.label, fontSize = 11.sp) },
                                icon = { TabIcon(t.ordinal, tab == t) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrim,
                                    selectedTextColor = TextPrim,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unselectedIconColor = TextSec,
                                    unselectedTextColor = TextSec
                                )
                            )
                        }
                    }
                }
            }
        ) { pad ->
            val mod = Modifier.padding(pad)
            when (tab) {
                Tab.AUTOMATIONS -> AutomationsScreen(mod, onEdit = { id -> editRoutineId = id; showEditor = true }, onCaptureScene = { showCapture = true })
                Tab.DEVICES -> DevicesScreen(mod)
                Tab.SETTINGS -> SettingsScreen(mod)
            }
        }
    }

    if (nfcWriteRoutineId != null) {
        AlertDialog(
            onDismissRequest = onCancelNfcWrite,
            confirmButton = { TextButton(onClick = onCancelNfcWrite) { Text("Abbrechen") } },
            title = { Text("NFC-Tag beschreiben") },
            text = { Text("Halte jetzt den NFC-Tag an die Rückseite deines Handys. Der Tag startet danach diese Automation – auch wenn die App geschlossen ist.") }
        )
    }
}
