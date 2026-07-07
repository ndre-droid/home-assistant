package com.nahuel.homeflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular HSV color wheel like the Hue app: angle = hue, distance from center = saturation.
 * Tap or drag anywhere on the wheel; live preview bar below; returns "#RRGGBB".
 */
@Composable
fun ColorWheelDialog(initialHex: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var hue by remember { mutableStateOf(30f) }
    var sat by remember { mutableStateOf(0.85f) }

    LaunchedEffect(Unit) {
        initialHex?.let {
            runCatching {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(it), hsv)
                hue = hsv[0]; sat = hsv[1]
            }
        }
    }

    fun currentHex(): String =
        String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))

    val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Farbe wählen", color = TextPrim) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(260.dp)
                        .pointerInput(Unit) {
                            fun update(pos: Offset) {
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val dx = pos.x - cx; val dy = pos.y - cy
                                val radius = min(cx, cy)
                                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                if (angle < 0) angle += 360f
                                hue = angle
                                sat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                            }
                            detectDragGestures(
                                onDragStart = { update(it) },
                                onDrag = { change, _ -> change.consume(); update(change.position) }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val dx = pos.x - cx; val dy = pos.y - cy
                                val radius = min(cx, cy)
                                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                if (angle < 0) angle += 360f
                                hue = angle
                                sat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        // hue ring: sweep through the full spectrum
                        drawCircle(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            ),
                            radius = radius
                        )
                        // saturation: white core fading out
                        drawCircle(
                            Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f))),
                            radius = radius
                        )
                        // selection knob
                        val rad = Math.toRadians(hue.toDouble())
                        val dist = sat * radius
                        val knob = Offset(
                            center.x + (dist * cos(rad)).toFloat(),
                            center.y + (dist * sin(rad)).toFloat()
                        )
                        drawCircle(Color.Black.copy(alpha = 0.35f), 12.dp.toPx(), center = knob)
                        drawCircle(Color.White, 10.dp.toPx(), center = knob, style = Stroke(3.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(preview)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(currentHex()); onDismiss() }) { Text("Übernehmen", color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = TextSec) } }
    )
}
