package com.nahuel.homeflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.devices.RadioBrowser
import kotlinx.coroutines.launch

/** Integrated stream search (radio-browser.info): "birds", "nature", "rain", "jazz" ... */
@Composable
fun RadioSearchDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(RadioBrowser.CURATED) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun runSearch() {
        if (query.isBlank()) return
        busy = true; error = ""
        scope.launch {
            RadioBrowser.search(query)
                .onSuccess { results = it; if (it.isEmpty()) error = "Nichts gefunden, anderen Begriff probieren" }
                .onFailure { error = it.message ?: "Fehler" }
            busy = false
        }
    }

    FlatDialog(
        onDismissRequest = onDismiss,
        title = "Sounds & Sender suchen",
        dismissButton = { GhostButton("Schließen", color = Muted, onClick = onDismiss) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        FlatField("Suche", query, placeholder = "z. B. birds, nature, rain, jazz") { query = it }
                    }
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton(if (busy) "…" else "Suchen", enabled = !busy) { runSearch() }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quick = listOf("Natur", "Regen", "Meer", "Wald", "Jazz", "Lofi", "Klassik", "Chill", "News")
                    items(quick) { q ->
                        ChoiceChip(q, selected = query == q) { query = q; runSearch() }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (results === RadioBrowser.CURATED) Caption("Freie Sender – direkt spielbar auf Sonos:")
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.height(300.dp)) {
                    items(results, key = { it.url }) { st ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(st.url); onDismiss() }
                                .padding(vertical = 9.dp)
                        ) {
                            Text(st.name, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (st.tags.isNotBlank()) Caption(st.tags.take(60))
                        }
                        Rule()
                    }
                }
            }
        }
    )
}
