package com.macedonga.visor.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.macedonga.visor.R
import com.macedonga.visor.core.design.Visor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The launch mark lights, holds, then lifts into the pair as the black goes. */
@Stable
class MarkIntro(armed: Boolean) {
    private var armed by mutableStateOf(armed)
    internal val lit = Animatable(if (armed) 0f else 1f)
    internal val gone = Animatable(if (armed) 0f else 1f)

    /** The mark still holds the dark. */
    val holding: Boolean get() = armed || gone.value == 0f
    val showing: Boolean get() = armed || gone.value < 1f

    /** Hold the dark from this frame on, before [play] gets to run. */
    fun arm() {
        armed = true
    }

    /** The whole opening. [onLit] runs when the mark finishes lighting. */
    suspend fun play(onLit: () -> Unit = {}) {
        try {
            lit.snapTo(0f)
            gone.snapTo(0f)
            armed = false
            delay(DARK_MS)
            lit.animateTo(1f, tween(LIGHT_MS, easing = LIGHT_EASE))
            onLit()
            delay(HOLD_MS)
            gone.animateTo(1f, tween(LET_GO_MS, easing = SETTLE_EASE))
        } finally {
            // Cut short, it lets go at once rather than leave the dark over the screen.
            armed = false
            if (gone.value < 1f) {
                withContext(NonCancellable) {
                    lit.snapTo(1f)
                    gone.snapTo(1f)
                }
            }
        }
    }

    /** Returns once the mark has let go of the dark; at once when it is not playing. */
    suspend fun awaitLetGo() {
        snapshotFlow { holding }.first { !it }
    }
}

/** Draws [stage]'s [MarkIntro] while it shows, and takes the touches meant for the screen it hides. */
@Composable
fun MarkOverlay(stage: StageHolder) {
    val intro = stage.intro
    val showing by remember { derivedStateOf { intro.showing } }
    if (!showing) return
    val canvas = Visor.colors.canvas
    val dark = Visor.colors.raised
    val warm = Visor.colors.lightWarm
    val mark = stringResource(R.string.django_mark_path)
    val path = remember(mark) { PathParser().parsePathString(mark).toPath().apply { fillType = PathFillType.EvenOdd } }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        val going = SETTLE_EASE.transform(intro.gone.value)
        drawRect(canvas, alpha = 1f - intro.gone.value)
        // Where the system splash draws it, then up to the middle of the pair as it lets go.
        val centre = Offset(size.width / 2, lerp(size.height / 2, stage.onWindow(0.5f, 0.5f, size.width).y, going))
        val k = MARK_BOX.toPx() / MARK_GRID * (1f - 0.18f * going)
        val alpha = 1f - going
        withTransform({
            translate(centre.x - MARK_GRID / 2 * k, centre.y - MARK_GRID / 2 * k)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawPath(path, dark, alpha = alpha)
            clipRect(right = MARK_GRID, bottom = lerp(MARK_TOP, MARK_BOTTOM, intro.lit.value)) {
                drawPath(path, Brush.verticalGradient(listOf(Color.White, warm), startY = MARK_TOP, endY = MARK_BOTTOM), alpha = alpha)
            }
        }
    }
}

private const val MARK_GRID = 256f
private const val MARK_TOP = 46f
private const val MARK_BOTTOM = 210f

/** The splash draws the 108 dp icon grid at 288 dp, so the mark's 60 dp middle is 160 dp on screen. */
private val MARK_BOX = 160.dp
private val LIGHT_EASE = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
/** 1.75 s in all, most of it the mark lighting. */
private const val DARK_MS = 100L
private const val LIGHT_MS = 850
private const val HOLD_MS = 200L
private const val LET_GO_MS = 600
