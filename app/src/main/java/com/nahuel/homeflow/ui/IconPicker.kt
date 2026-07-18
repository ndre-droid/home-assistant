package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.TriggerType

/** Big curated icon pack for automations. */
val ICON_PACK = listOf(
    "💡", "🌙", "🌅", "🌇", "☀️", "⭐", "✨", "🔥",
    "🎬", "📺", "🍿", "🎮", "🎵", "🎶", "🎧", "🔊",
    "🎉", "🪩", "🥳", "🍾", "🍷", "🍕", "🍳", "☕",
    "😴", "🛏️", "🛁", "🚿", "🧘", "📖", "💻", "🏠",
    "🚪", "🔑", "🚗", "🏃", "🐦", "🐋", "🌊", "🌧️",
    "🌲", "🌸", "❤️", "💜", "💚", "🧡", "🩵", "🤍",
    "⚡", "🔴", "🟢", "🔵", "🟣", "⚫", "🏆", "⚽",
    "🎄", "🎃", "🥂", "🕯️", "🪄", "🎯", "⏰", "🔕",
    // Poker & Casino
    "♠️", "♥️", "♦️", "♣️", "🃏", "🎰", "🎲", "🀄",
    "💰", "💵", "💎", "🪙", "🤑", "🏦", "👑", "🎩",
    // Games
    "🕹️", "👾", "♟️", "🎱", "🧩", "🪀", "🏹", "🗡️",
    "🛡️", "🧙", "🐉", "🚩", "🥷", "🤖", "💣", "🪃",
    // Sport
    "⚽", "🏀", "🏈", "🎾", "🏐", "🏓", "🏸", "🥊",
    "🏋️", "🚴", "🏊", "⛷️", "🛹", "🥋", "🏇", "🥇",
    // Musik & Kultur
    "🎸", "🎹", "🎷", "🎺", "🥁", "🎻", "🎤", "📻",
    "🎨", "🎭", "📚", "✏️", "📷", "🎥", "📽️", "🎪",
    // Essen & Trinken
    "🍺", "🍻", "🥃", "🍸", "🍹", "🥤", "🍔", "🍟",
    "🌮", "🍣", "🍜", "🍰", "🍪", "🥐", "🫖", "🧊",
    // Tiere & Natur
    "🐱", "🐶", "🦊", "🐻", "🦉", "🦄", "🌵", "🌴",
    "🍀", "🌺", "🌈", "⛅", "❄️", "🌩️", "🌪️", "🌫️",
    // Reise & Technik
    "✈️", "🚀", "🛸", "⛵", "🏖️", "🏔️", "⛺", "🗺️",
    "📱", "⌚", "🖥️", "📡", "🔋", "🔌", "🛰️", "💾",
    // Zuhause & Symbole
    "🛋️", "🪑", "🧺", "🧹", "🪟", "🖼️", "🕰️", "🚽",
    "💤", "🔔", "📢", "✅", "❌", "🔄", "⏸️", "⏭️"
)

/** Keyword-based suggestion from the routine name. */
fun suggestIcon(name: String): String? {
    val n = name.lowercase()
    val map = listOf(
        listOf("film", "kino", "movie", "netflix") to "🎬",
        listOf("party", "rave", "feier") to "🪩",
        listOf("schlaf", "nacht", "bett", "gute nacht") to "🌙",
        listOf("aufwach", "morgen", "wecker", "aufsteh") to "🌅",
        listOf("bad", "dusche", "wanne") to "🛁",
        listOf("vogel", "bird") to "🐦",
        listOf("wal", "ozean", "meer", "ocean") to "🐋",
        listOf("musik", "song", "sound", "radio") to "🎵",
        listOf("tv", "fernseh") to "📺",
        listOf("aus", "verlassen", "gehen", "tschüss") to "🚪",
        listOf("ankommen", "zuhause", "heim") to "🏠",
        listOf("koch", "küche", "essen") to "🍳",
        listOf("lesen", "buch") to "📖",
        listOf("fokus", "arbeit", "büro") to "💻",
        listOf("romantik", "date", "liebe") to "❤️",
        listOf("gäste", "besuch") to "🥂",
        listOf("sport", "tor", "fußball") to "⚽",
        listOf("strobe", "blitz") to "⚡",
        listOf("chill", "entspann", "relax") to "🧘"
    )
    return map.firstOrNull { (keys, _) -> keys.any { it in n } }?.second
}

/** Effective icon of a routine: chosen > name-based suggestion > trigger emoji. */
fun routineIcon(r: Routine): String =
    r.icon.ifEmpty { suggestIcon(r.name) ?: triggerEmoji(r.triggers.firstOrNull()?.type) }

@Composable
fun IconPickerDialog(
    suggestion: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Icon wählen", color = TextPrim) },
        text = {
            Column {
                if (suggestion != null) {
                    TextButton(onClick = { onPick(suggestion); onDismiss() }) {
                        Text("Vorschlag: $suggestion  übernehmen", color = Violet)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ICON_PACK) { emoji ->
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Surface2)
                                .bouncyClick { onPick(emoji); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 20.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(""); onDismiss() }) { Text("Automatisch", color = TextSec) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = TextSec) } }
    )
}
