package com.kye.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x: Float,        // start, as a share of the width
    val drift: Float,    // sideways sway
    val speed: Float,    // fall speed, screen heights per run
    val delay: Float,    // share of the run before it appears
    val spin: Float,     // turns per run
    val w: Float,
    val h: Float,
    val color: Color
)

private val COLORS = listOf(
    Color(0xFF6C5CE7), Color(0xFF00B894), Color(0xFFFDCB6E), Color(0xFFFF7675),
    Color(0xFF74B9FF), Color(0xFFE84393), Color(0xFFFF9F43)
)

/**
 * Confetti falling over the whole screen, once each time [trigger] goes up. Taps pass through.
 */
@Composable
fun ConfettiBurst(trigger: Int) {
    var shown by remember { mutableStateOf(0) }
    val progress = remember { Animatable(0f) }
    val pieces = remember(trigger) {
        val r = Random(trigger)
        List(140) {
            Piece(
                x = r.nextFloat(),
                drift = (r.nextFloat() - 0.5f) * 0.12f,
                speed = 0.9f + r.nextFloat() * 0.8f,
                delay = r.nextFloat() * 0.35f,
                spin = 1f + r.nextFloat() * 3f,
                w = 6f + r.nextFloat() * 6f,
                h = 10f + r.nextFloat() * 8f,
                color = COLORS[r.nextInt(COLORS.size)]
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        shown = trigger
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2800, easing = LinearEasing))
        shown = 0
    }
    if (shown == 0) return
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            // Falls with a little gravity, sways side to side, fades at the very end.
            val y = -40f + (size.height + 80f) * p.speed * local * (0.6f + 0.4f * local)
            val x = size.width * (p.x + p.drift * sin(local * 9f + p.x * 6f))
            val alpha = if (local > 0.85f) (1f - local) / 0.15f else 1f
            val density = density
            rotate(p.spin * 360f * local, pivot = Offset(x, y)) {
                drawRect(
                    p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.w * density / 2, y - p.h * density / 2),
                    size = Size(p.w * density, p.h * density)
                )
            }
        }
    }
}
