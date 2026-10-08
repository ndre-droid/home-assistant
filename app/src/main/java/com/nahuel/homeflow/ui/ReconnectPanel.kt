package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.engine.Candidate
import com.nahuel.homeflow.engine.DeviceKind
import com.nahuel.homeflow.engine.DeviceRelocator
import com.nahuel.homeflow.engine.Match
import com.nahuel.homeflow.engine.Relocation

/**
 * "Neu verbinden" review: every configured device with its old and new address. Proven matches
 * are preselected; name guesses and missing devices can be picked by hand. Nothing is saved
 * until "Übernehmen".
 */
@Composable
fun ReconnectPanel(items: List<Relocation>, onRescan: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val config by Store.config.collectAsState()
    // index -> chosen target (null = leave unchanged)
    val picks = remember(items) { mutableStateMapOf<Int, Candidate?>().apply { items.forEachIndexed { i, r -> put(i, r.target) } } }
    val ssidNow = remember { currentSsid(ctx) }
    val ssidStale = config.homeWifiSsid.isNotBlank() && ssidNow != null && !ssidNow.equals(config.homeWifiSsid, ignoreCase = true)

    Column(Modifier.fillMaxWidth().background(AccentTint).padding(horizontal = 16.dp, vertical = 14.dp)) {
        SectionTitle("Neu verbinden")
        Spacer(Modifier.height(4.dp))
        Caption("Geräte werden über ihre feste Kennung wiedergefunden. Automationen und Räume ziehen automatisch mit.")
        Spacer(Modifier.height(10.dp))

        items.forEachIndexed { i, r ->
            val pick = picks[i]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                StatusDot(pick != null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Caption(statusLine(r, pick))
                }
                if (r.kind != DeviceKind.HUE && r.options.isNotEmpty() && r.match != Match.SAME) {
                    CandidatePicker(r.options, pick) { picks[i] = it }
                }
            }
            if (pick == null) missingHint(r.kind)?.let { HintText(it) }
            Rule()
        }

        if (ssidStale) {
            Spacer(Modifier.height(10.dp))
            HintText("„WLAN verlassen\"-Trigger hört noch auf „${config.homeWifiSsid}\". Aktuelles WLAN: „$ssidNow\".")
            Spacer(Modifier.height(6.dp))
            SecondaryButton("„$ssidNow\" als Heim-WLAN") {
                Store.updateConfig { it.copy(homeWifiSsid = ssidNow!!) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Übernehmen") {
                val chosen = items.mapIndexed { i, r ->
                    val p = picks[i]
                    when {
                        p == null -> r.copy(target = null)
                        p == r.target -> r
                        else -> r.copy(target = p, match = Match.GUESS)
                    }
                }
                DeviceRelocator.apply(chosen)
                val n = chosen.count { it.moves }
                onDone(if (n == 0) "Alles aktuell ✓" else "$n Gerät${if (n == 1) "" else "e"} neu verbunden ✓")
            }
            SecondaryButton("Erneut suchen", onClick = onRescan)
            GhostButton("Abbrechen", color = Muted) { onDone("") }
        }
    }
    Rule()
}

private fun statusLine(r: Relocation, pick: Candidate?): String = when {
    pick == null -> if (r.match == Match.MISSING) "Nicht gefunden · bleibt ${r.oldIp}" else "Unverändert · ${r.oldIp}"
    pick.ip == r.oldIp -> "Erreichbar ✓ · ${r.oldIp}"
    pick != r.target || r.match == Match.GUESS -> "${r.oldIp} → ${pick.ip} · bitte prüfen"
    else -> "${r.oldIp} → ${pick.ip} ✓"
}

private fun missingHint(kind: DeviceKind): String? = when (kind) {
    DeviceKind.HUE -> "Bridge per LAN-Kabel am neuen Router? Falls ja und weiter nicht gefunden: Hue neu koppeln."
    DeviceKind.TV -> "TV einschalten (aus = unsichtbar), dann „Erneut suchen\"."
    DeviceKind.SONOS -> "Speaker an Strom und im neuen WLAN (Sonos-App)?"
}

@Composable
private fun CandidatePicker(options: List<Candidate>, selected: Candidate?, onSelect: (Candidate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GhostButton(if (selected == null) "Wählen" else "Ändern") { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Bg).border(RuleWidth, Divider)
        ) {
            options.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.name} · ${c.ip}", color = Ink, fontSize = 13.sp) },
                    onClick = { onSelect(c); open = false }
                )
            }
            DropdownMenuItem(
                text = { Text("Nicht ändern", color = Muted, fontSize = 13.sp) },
                onClick = { onSelect(null); open = false }
            )
        }
    }
}
