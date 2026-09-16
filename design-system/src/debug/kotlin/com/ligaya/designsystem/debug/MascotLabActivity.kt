package com.ligaya.designsystem.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.components.LigayaCameraView
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.LigayaMascotController
import com.ligaya.designsystem.components.LigayaMotionMode
import com.ligaya.designsystem.components.rememberLigayaMascotController

/**
 * Debug-only test bench for the mascot. Every state is reachable by tapping, and scriptable from adb:
 *
 *   adb shell am start -n com.ligaya.app/com.ligaya.designsystem.debug.MascotLabActivity --es cmd "emotion:happy,listen:on"
 *
 * Commands: emotion:<name> listen:on|off speak:on|off camera:front|left|right frame:bust|portrait|head
 * motion:system|calm|still speed:<0.5-2.0> depth:<0-1> dark:on|off blink smile reset
 */
class MascotLabActivity : ComponentActivity() {
    private val pending = mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending.value = commands(intent)
        setContent { MaterialTheme { Lab(pending) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending.value = commands(intent)
    }

    private fun commands(intent: Intent?): List<String> =
        intent?.getStringExtra("cmd")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lab(pending: androidx.compose.runtime.MutableState<List<String>>) {
    val controller = rememberLigayaMascotController()
    var frame by remember { mutableStateOf(LigayaFrame.Bust) }
    var motion by remember { mutableStateOf(LigayaMotionMode.System) }
    var dark by remember { mutableStateOf(false) }
    var emotion by remember { mutableStateOf(LigayaEmotion.Neutral) }
    var speed by remember { mutableStateOf(1.0f) }
    var depth by remember { mutableStateOf(0.6f) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(LigayaCameraView.Front) }

    fun run(cmd: String, c: LigayaMascotController) {
        val (key, value) = cmd.split(':').let { it[0] to it.getOrNull(1) }
        when (key) {
            "emotion" -> LigayaEmotion.entries.firstOrNull { it.name.equals(value, true) }?.let { emotion = it; c.setEmotion(it) }
            "speed" -> value?.toFloatOrNull()?.let { speed = it.coerceIn(0.5f, 2f); c.setAnimationSpeed(speed) }
            "depth" -> value?.toFloatOrNull()?.let { depth = it.coerceIn(0f, 1f); c.setDepthStrength(depth) }
            "listen" -> { listening = value == "on"; speaking = speaking && !listening; if (listening) c.startListening() else c.stopListening() }
            "speak" -> { speaking = value == "on"; listening = listening && !speaking; if (speaking) c.startSpeaking() else c.stopSpeaking() }
            "camera" -> {
                camera = when (value) { "left" -> LigayaCameraView.LeftThreeQuarter; "right" -> LigayaCameraView.RightThreeQuarter; else -> LigayaCameraView.Front }
                c.setCameraView(camera)
            }
            "frame" -> LigayaFrame.entries.firstOrNull { it.name.equals(value, true) }?.let { frame = it }
            "motion" -> LigayaMotionMode.entries.firstOrNull { it.name.equals(value, true) }?.let { motion = it }
            "dark" -> dark = value == "on"
            "blink" -> c.blink()
            "smile" -> c.smile()
            "reset" -> {
                emotion = LigayaEmotion.Neutral
                listening = false
                speaking = false
                camera = LigayaCameraView.Front
                c.reset()
            }
        }
    }

    if (pending.value.isNotEmpty()) {
        val cmds = pending.value
        pending.value = emptyList()
        cmds.forEach { run(it, controller) }
    }

    val state by controller.state
    val ready by controller.isReady
    val bg = if (dark) Color(0xFF15100E) else Color(0xFFFBF7F3)
    val fg = if (dark) Color(0xFFF3E9E2) else Color(0xFF2A1F1C)

    Column(Modifier.fillMaxSize().background(bg).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LigayaMascot(controller = controller, modifier = Modifier.fillMaxSize(), frame = frame, motion = motion)
        }
        Text(
            "ready=$ready  ${state?.let { "emotion=${it.emotion} listening=${it.listening} speaking=${it.speaking} camera=${it.camera} speed=${it.animationSpeed} depth=${it.depthStrength}" } ?: "engine not started"}",
            color = fg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Column(
            Modifier.fillMaxWidth().weight(0.8f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LigayaEmotion.entries.forEach { e ->
                    FilterChip(selected = emotion == e, onClick = { run("emotion:${e.name}", controller) }, label = { Text(e.name) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 1.0f, 2.0f).forEach { v ->
                    FilterChip(selected = speed == v, onClick = { run("speed:$v", controller) }, label = { Text("Speed ${v}x") })
                }
                listOf(0f, 0.6f, 1f).forEach { v ->
                    FilterChip(selected = depth == v, onClick = { run("depth:$v", controller) }, label = { Text("Depth $v") })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = listening, onClick = { run("listen:${if (listening) "off" else "on"}", controller) }, label = { Text("Listening") })
                FilterChip(selected = speaking, onClick = { run("speak:${if (speaking) "off" else "on"}", controller) }, label = { Text("Speaking") })
                FilterChip(selected = false, onClick = { run("blink", controller) }, label = { Text("Blink") })
                FilterChip(selected = false, onClick = { run("smile", controller) }, label = { Text("Smile") })
                FilterChip(selected = false, onClick = { run("reset", controller) }, label = { Text("Reset") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("left" to LigayaCameraView.LeftThreeQuarter, "front" to LigayaCameraView.Front, "right" to LigayaCameraView.RightThreeQuarter).forEach { (k, v) ->
                    FilterChip(selected = camera == v, onClick = { run("camera:$k", controller) }, label = { Text("Cam $k") })
                }
                FilterChip(selected = dark, onClick = { dark = !dark }, label = { Text("Dark bg") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LigayaFrame.entries.forEach { fr -> FilterChip(selected = frame == fr, onClick = { frame = fr }, label = { Text(fr.name) }) }
                LigayaMotionMode.entries.forEach { m -> FilterChip(selected = motion == m, onClick = { motion = m }, label = { Text(m.name) }) }
            }
        }
    }
}
