package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.SonosSpeaker
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TargetType
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import com.nahuel.homeflow.engine.AlarmScheduler
import com.nahuel.homeflow.engine.RoutineEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Colour presets offered on a colour lamp (same values as the device screen). */
private val homeColorPresets = listOf(
    "Warm" to "#FFB74D", "Hell" to "#FFFFFF", "Rosa" to "#F062A6", "Mint" to "#34D399",
    "Blau" to "#3D8BFD", "Lila" to "#8B7CF7", "Rot" to "#EF4444"
)

/** Gradient colours of a routine: the Hue colours it sets, else a stable palette. */
fun routineColors(r: Routine): List<Color> {
    val hexes = r.variants.flatMap { v -> v.actions }
        .filter { it.target == TargetType.HUE }
        .mapNotNull { it.params["color"] }
        .distinct()
    val cols = hexes.mapNotNull { hexColor(it) }
    return when {
        cols.size >= 2 -> listOf(androidx.compose.ui.graphics.lerp(cols[0], Color.White, 0.3f), cols[0], cols[1])
        cols.size == 1 -> lightGradient(hexes[0])
        else -> paletteFor(r.id)
    }
}

/** Next scheduled run among enabled TIME/SUN routines, or null. */
fun nextScheduled(routines: List<Routine>, lat: Double, lon: Double): Pair<Routine, Long>? =
    routines.filter { it.enabled }
        .mapNotNull { r ->
            r.triggers.filter { it.type == TriggerType.TIME || it.type == TriggerType.SUN }
                .mapNotNull { t -> AlarmScheduler.nextFireMillis(t.type, t, lat, lon) }
                .minOrNull()?.let { r to it }
        }
        .minByOrNull { it.second }

fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(ms))

private data class SpeakerState(val playing: Boolean, val title: String)

private fun parseTitle(meta: String): String =
    Regex("<dc:title>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(meta)?.groupValues?.get(1)?.trim() ?: ""

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onOpenDevices: () -> Unit, onOpenFlows: () -> Unit) {
    val config by Store.config.collectAsState()
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var lights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var hueError by remember { mutableStateOf("") }
    var speakers by remember { mutableStateOf<Map<String, SpeakerState>>(emptyMap()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var lastScene by remember { mutableStateOf<String?>(null) }

    // Keep light + speaker state fresh while the screen is visible.
    LaunchedEffect(config.hueAppKey, config.hueBridgeIp, config.lightOrder) {
        while (config.hueAppKey.isNotEmpty()) {
            HueClient.lights()
                .onSuccess { lights = orderLights(it, Store.config.value.lightOrder); hueError = "" }
                .onFailure { hueError = it.message ?: "Bridge nicht erreichbar" }
            delay(8000)
        }
    }
    LaunchedEffect(config.sonos) {
        while (config.sonos.isNotEmpty()) {
            val m = mutableMapOf<String, SpeakerState>()
            config.sonos.forEach { s ->
                val playing = SonosClient.isPlaying(s.ip)
                val title = SonosClient.getMedia(s.ip).getOrNull()?.second?.let { parseTitle(it) } ?: ""
                m[s.ip] = SpeakerState(playing, title)
            }
            speakers = m
            delay(10000)
        }
    }

    fun update(l: HueLight) { lights = lights.map { if (it.id == l.id) l else it } }
    fun toggle(l: HueLight, v: Boolean) {
        update(l.copy(on = v))
        scope.launch { HueClient.setLight(l.id, on = v, brightness = null, colorHex = null).onFailure { hueError = it.message ?: "" } }
    }
    fun dim(l: HueLight, b: Int) {
        update(l.copy(brightness = b))
        scope.launch { HueClient.setLight(l.id, on = null, brightness = b, colorHex = null).onFailure { hueError = it.message ?: "" } }
    }
    fun color(l: HueLight, hex: String) {
        update(l.copy(colorHex = hex))
        scope.launch { HueClient.setLight(l.id, on = null, brightness = null, colorHex = hex).onFailure { hueError = it.message ?: "" } }
    }
    fun allOff() {
        lights = lights.map { it.copy(on = false) }
        scope.launch {
            HueClient.setLight("all", on = false, brightness = null, colorHex = null)
            config.sonos.forEach { s -> scope.launch { SonosClient.pause(s.ip) } }
            config.tvs.forEach { t -> scope.launch { LgTvClient.powerOff(t.ip, t.clientKey) } }
        }
    }

    val glow = lights.firstOrNull { it.on }?.let { lightGradient(it.colorHex)[1] } ?: MaterialTheme.colorScheme.primary
    val next = nextScheduled(routines, config.latitude, config.longitude)

    BoxWithConstraints(modifier.fillMaxSize().ambientGlow(glow)) {
        val wide = maxWidth >= 840.dp
        val tall = !wide && maxWidth >= 600.dp && maxHeight >= 800.dp
        val split = wide || tall
        val selected = lights.firstOrNull { it.id == selectedId } ?: if (split) lights.firstOrNull() else null

        val list: @Composable (Modifier) -> Unit = { m ->
            HomeList(
                modifier = m,
                lights = lights, routines = routines, speakers = config.sonos, speakerState = speakers,
                hueReady = config.hueAppKey.isNotEmpty(), hueError = hueError,
                selectedId = if (split) selected?.id else null, lastScene = lastScene,
                next = next, compactRooms = tall,
                onRunScene = { r -> lastScene = r.id; RoutineEngine.runAsync(ctx, r) },
                onToggle = ::toggle, onDim = ::dim, onOpen = { selectedId = it.id },
                onAllOff = ::allOff, onOpenDevices = onOpenDevices, onOpenFlows = onOpenFlows,
                onPlay = { s -> scope.launch { SonosClient.setMute(s.ip, false); SonosClient.play(s.ip) }
                    speakers = speakers + (s.ip to (speakers[s.ip]?.copy(playing = true) ?: SpeakerState(true, ""))) },
                onPause = { s -> scope.launch { SonosClient.pause(s.ip) }
                    speakers = speakers + (s.ip to (speakers[s.ip]?.copy(playing = false) ?: SpeakerState(false, ""))) }
            )
        }
        val detail: @Composable (Modifier, HueLight, Boolean) -> Unit = { m, l, back ->
            LightDetail(m, l, showBack = back, onBack = { selectedId = null },
                onToggle = { toggle(l, it) }, onDim = { dim(l, it) }, onColor = { color(l, it) })
        }

        when {
            wide -> Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp, start = 16.dp)) {
                    if (selected != null) DetailPanel { detail(Modifier, selected, false) }
                }
            }
            tall -> Column(Modifier.fillMaxSize()) {
                list(Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    if (selected != null) DetailPanel { detail(Modifier, selected, false) }
                }
            }
            selected != null -> detail(Modifier.fillMaxSize().statusBarsPadding(), selected, true)
            else -> list(Modifier.fillMaxSize())
        }
    }
}

/** Frosted panel that holds the light detail on the inner display. */
@Composable
private fun DetailPanel(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxSize()
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(CardGradient)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (IsDark) 0.08f else 0.5f), Color.Transparent)), shape)
    ) { content() }
}

@Composable
private fun HomeList(
    modifier: Modifier,
    lights: List<HueLight>,
    routines: List<Routine>,
    speakers: List<SonosSpeaker>,
    speakerState: Map<String, SpeakerState>,
    hueReady: Boolean,
    hueError: String,
    selectedId: String?,
    lastScene: String?,
    next: Pair<Routine, Long>?,
    compactRooms: Boolean,
    onRunScene: (Routine) -> Unit,
    onToggle: (HueLight, Boolean) -> Unit,
    onDim: (HueLight, Int) -> Unit,
    onOpen: (HueLight) -> Unit,
    onAllOff: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenFlows: () -> Unit,
    onPlay: (SonosSpeaker) -> Unit,
    onPause: (SonosSpeaker) -> Unit
) {
    val today = remember { SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date()) }
    Column(
        modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(today + (next?.let { " · ${it.first.name} um ${hhmm(it.second)}" } ?: ""),
                    color = TextSec, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                ScreenTitle("Zuhause")
            }
            GlassIconButton(Icons.Filled.PowerSettingsNew, "Alles aus", onClick = onAllOff)
        }

        // Scenes = routines
        if (routines.isNotEmpty()) {
            Text("Szenen", color = TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(routines, key = { it.id }) { r ->
                    SceneOrb(
                        colors = routineColors(r), label = r.name, emoji = routineIcon(r),
                        selected = r.id == lastScene, dimmed = !r.enabled, onClick = { onRunScene(r) }
                    )
                }
            }
        }

        // Lights
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Lichter", color = TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (lights.isNotEmpty()) Text("${lights.count { it.on }} von ${lights.size} an", color = TextSec, fontSize = 13.sp)
        }
        if (!hueReady) {
            GradientCard {
                Text("Hue noch nicht verbunden", color = TextPrim, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                HintText("Koppel deine Bridge unter Geräte, dann erscheinen hier deine Lampen.")
                Spacer(Modifier.height(12.dp))
                Text("Zu Geräten", color = Violet, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).bouncyClick(onOpenDevices).padding(vertical = 6.dp))
            }
        } else {
            if (hueError.isNotEmpty()) Text("Hue: $hueError", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            if (lights.isEmpty() && hueError.isEmpty()) HintText("Lade Lampen …")
            if (compactRooms) {
                lights.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { l ->
                            DimmerCard(
                                title = l.name, subtitle = "", colors = lightGradient(l.colorHex), on = l.on,
                                brightness = l.brightness, onBrightness = { onDim(l, it) }, onToggle = { onToggle(l, it) },
                                modifier = Modifier.weight(1f), height = 84.dp, selected = l.id == selectedId, onClick = { onOpen(l) }
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                lights.forEach { l ->
                    DimmerCard(
                        title = l.name, subtitle = if (l.supportsColor) "Farbe" else "Weiß", colors = lightGradient(l.colorHex), on = l.on,
                        brightness = l.brightness, onBrightness = { onDim(l, it) }, onToggle = { onToggle(l, it) },
                        selected = l.id == selectedId, onClick = { onOpen(l) }
                    )
                }
            }
        }

        // Next flow
        if (next != null) {
            val (r, at) = next
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(TextPrim.copy(alpha = 0.03f))
                    .border(1.dp, TextPrim.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                    .bouncyClick(onOpenFlows)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val cols = routineColors(r)
                Box(Modifier.size(28.dp).shadow(10.dp, CircleShape, ambientColor = cols[1], spotColor = cols[1]).clip(CircleShape).background(Brush.linearGradient(cols)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${r.name} um ${hhmm(at)}", color = TextPrim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Nächster Flow", color = TextSec, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSec)
            }
        }

        // Music
        if (speakers.isNotEmpty()) {
            Text("Musik", color = TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            speakers.forEach { s ->
                val st = speakerState[s.ip]
                SpeakerCard(s, st?.playing == true, st?.title.orEmpty(), onPlay = { onPlay(s) }, onPause = { onPause(s) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SpeakerCard(s: SonosSpeaker, playing: Boolean, title: String, onPlay: () -> Unit, onPause: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val warm = Color(0xFFFFB46A)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(CardGradient)
            .drawBehind {
                if (playing) drawRect(Brush.radialGradient(listOf(warm.copy(alpha = 0.16f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, size.height / 2), radius = size.width * 0.6f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (IsDark) 0.07f else 0.5f), Color.Transparent)), shape)
            .padding(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (playing) Brush.linearGradient(listOf(Color(0xFFFFD890), Color(0xFFF2786A))) else Brush.linearGradient(listOf(Surface2, Surface2)))
        ) { Icon(Icons.Filled.Speaker, null, tint = if (playing) Color(0xFF1A1208) else TextSec, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (playing && title.isNotEmpty()) title else s.name, color = TextPrim, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (playing) (if (title.isNotEmpty()) "${s.name} · spielt" else "Spielt") else "Pausiert",
                color = TextSec, fontSize = 13.sp, maxLines = 1)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFECE7DF), Color(0xFFD9D3C9))))
                .bouncyClick { if (playing) onPause() else onPlay() }
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Abspielen", tint = Color(0xFF141210), modifier = Modifier.size(20.dp))
        }
    }
}

/** One lamp in detail: big level, brightness slider, colours. Used full-screen or as a pane. */
@Composable
fun LightDetail(
    modifier: Modifier,
    light: HueLight,
    showBack: Boolean,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDim: (Int) -> Unit,
    onColor: (String) -> Unit
) {
    var level by remember(light.id) { mutableStateOf(light.brightness.toFloat()) }
    LaunchedEffect(light.brightness) { level = light.brightness.toFloat() }
    var showWheel by remember { mutableStateOf(false) }
    val cols = lightGradient(light.colorHex)
    if (showBack) androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier
            .ambientGlow(cols[1], cx = 0.5f, cy = 0.12f, radius = 0.7f, strength = if (light.on) 0.4f else 0.08f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onClick = onBack); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(light.name, color = TextPrim, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (light.supportsColor) "Farblampe" else "Weißlicht", color = TextSec, fontSize = 13.sp)
            }
            GlowSwitch(checked = light.on, onCheckedChange = onToggle, label = light.name)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (light.on) "${level.roundToInt()}" else "Aus",
                color = TextPrim, fontSize = 76.sp, fontWeight = FontWeight.Medium, letterSpacing = (-3).sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(cols[1].copy(alpha = if (light.on) 0.6f else 0f), blurRadius = 40f))
            )
            if (light.on) Text(" %", color = TextSec, fontSize = 28.sp, modifier = Modifier.padding(bottom = 14.dp))
        }
        if (light.on) {
            BigBrightnessSlider(
                value = level, onValueChange = { level = it },
                onValueChangeFinished = { onDim(level.roundToInt().coerceIn(1, 100)) }, colors = cols
            )
            if (light.supportsColor) {
                Text("Farbe", color = TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                homeColorPresets.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (name, hex) ->
                            ColorTile(name, lightGradient(hex), selected = light.colorHex.equals(hex, ignoreCase = true), modifier = Modifier.weight(1f)) { onColor(hex) }
                        }
                        if (row.size < 4) {
                            ColorTile("Farbrad", listOf(Color(0xFFFF7A7A), Color(0xFFFFD37A), Color(0xFF7AC4FF), Color(0xFFC69BFF)),
                                selected = false, modifier = Modifier.weight(1f)) { showWheel = true }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            HintText("Einschalten, um Helligkeit und Farbe zu ändern.")
        }
    }
    if (showWheel) {
        ColorWheelDialog(initialHex = light.colorHex, onDismiss = { showWheel = false }, onPick = { onColor(it) })
    }
}

@Composable
private fun ColorTile(label: String, colors: List<Color>, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .height(64.dp)
            .then(if (selected) Modifier.border(2.dp, Violet, RoundedCornerShape(21.dp)).padding(3.dp) else Modifier)
            .shadow(10.dp, shape, ambientColor = colors[1], spotColor = colors[1])
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)), shape)
            .bouncyClick(onClick)
            .padding(10.dp)
    ) { Text(label, color = inkOn(colors), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}
