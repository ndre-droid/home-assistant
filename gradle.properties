package com.nahuel.homeflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.devices.RadioBrowser
import kotlinx.coroutines.launch

/** Integrated stream search (radio-browser.info): "birds", "nature", "rain", "jazz" ... */
@Composable
fun RadioSearchDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RadioBrowser.Station>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun runSearch() {
        if (query.isBlank()) return
        busy = true; error = ""
        scope.launch {
            RadioBrowser.search(query)
                .onSuccess { results = it; if (it.isEmpty()) error = "Nichts gefunden – anderen Begriff probieren" }
                .onFailure { error = it.message ?: "Fehler" }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Sounds & Sender suchen", color = TextPrim) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("z. B. birds, nature, rain, jazz") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    TextButton(onClick = { runSearch() }, enabled = !busy) {
                        Text(if (busy) "…" else "Suchen", color = Violet)
                    }
                }
                if (error.isNotEmpty()) Text(error, color = Pink, fontSize = 13.sp)
                LazyColumn(Modifier.height(300.dp)) {
                    items(results, key = { it.url }) { st ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(st.url); onDismiss() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(st.name, color = TextPrim, fontSize = 14.sp)
                            if (st.tags.isNotBlank()) {
                                Text(
                                    st.tags.take(60), color = TextSec, fontSize = 11.sp, maxLines = 1
                                )
                            }
                        }
                        HorizontalDivider(color = Hairline)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen", color = TextSec) } }
    )
}
