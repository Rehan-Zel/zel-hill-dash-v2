package com.hilldash.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            HillDashGame()
        }
    }
}

// ───────────────────────────── Game screen ─────────────────────────────

@Composable
fun HillDashGame() {
    val engine = remember { GameEngine() }
    val tick = remember { mutableLongStateOf(0L) }
    var best by rememberSaveable { mutableIntStateOf(0) }
    var recorded by remember { mutableStateOf(false) }

    // Game loop: withFrameNanos, fixed 60 Hz physics step inside the engine
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    engine.update(dt)
                    if (engine.crashed && !recorded) {
                        recorded = true
                        best = max(best, engine.distanceM)
                    }
                }
                last = now
                tick.longValue = now
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val frame = tick.longValue // subscribe to frame updates
            if (frame >= 0L) drawScene(engine)
        }

        Hud(engine, tick, best)

        // Controls
        ControlButton(
            label = "BRAKE",
            color = Color(0xFFD32F2F),
            onPressChange = { engine.brake = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 20.dp)
        )
        ControlButton(
            label = "GAS",
            color = Color(0xFF2E7D32),
            onPressChange = { engine.gas = it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp)
        )

        GameOverOverlay(engine, tick, best) {
            engine.reset()
            recorded = false
        }
    }
}

@Composable
fun ControlButton(
    label: String,
    color: Color,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(170.dp)
            .height(96.dp)
            .alpha(if (down) 1f else 0.75f)
            .background(
                if (down) color else color.copy(alpha = 0.85f),
                RoundedCornerShape(24.dp)
            )
            .border(3.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down = true
                    onPressChange(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        down = false
                        onPressChange(false)
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ───────────────────────────── HUD ─────────────────────────────

@Composable
fun Hud(engine: GameEngine, tick: State<Long>, best: Int) {
    val frame = tick.value // recompose only this composable each frame
    val speed = engine.speedKmh
    val distance = engine.distanceM

    Box(Modifier.fillMaxSize().padding(16.dp)) {
        // Speedometer
        Box(
            modifier = Modifier.align(Alignment.TopStart).size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black.copy(alpha = 0.45f))
                val stroke = 12.dp.toPx()
                val inset = stroke / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(
                    color = Color.White.copy(alpha = 0.25f),
                    startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val frac = (speed / 160f).coerceIn(0f, 1f)
                drawArc(
                    color = Color(0xFFFFC107).copy(alpha = if (frame >= 0) 1f else 0f),
                    startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                // Needle
                val a = Math.toRadians((135f + 270f * frac).toDouble())
                val r = size.width / 2f - inset - stroke
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(
                    Color.Red,
                    Offset(cx, cy),
                    Offset(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat()),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
                )
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 56.dp)
            ) {
                Text(
                    "${speed.toInt()}",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("km/h", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }

        // Distance
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("DISTANCE", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            Text(
                "$distance m",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "BEST ${max(best, distance)} m",
                color = Color(0xFFFFC107),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GameOverOverlay(engine: GameEngine, tick: State<Long>, best: Int, onRestart: () -> Unit) {
    val frame = tick.value
    if (!engine.crashed || frame < 0L) return
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "GAME OVER",
                color = Color(0xFFFF5252),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text("You traveled ${engine.distanceM} m", color = Color.White, fontSize = 22.sp)
            Text("Best: $best m", color = Color(0xFFFFC107), fontSize = 18.sp)
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.padding(top = 12.dp).height(56.dp).width(200.dp)
            ) {
                Text("RESTART", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ───────────────────────────── Rendering ─────────────────────────────

private const val VIEW_H = 640f

private fun DrawScope.drawScene(e: GameEngine) {
    val sc = size.height / VIEW_H
    val viewW = size.width / sc
    val camX = e.x - viewW * 0.3f
    val camY = e.camY

    drawBackground(camX, sc)

    scale(sc, sc, pivot = Offset.Zero) {
        translate(-camX, -camY) {
            drawTerrain(camX, camY, viewW)
            drawCar(e)
        }
    }
}

private fun DrawScope.drawBackground(camX: Float, sc: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFFE1F5FE)))
    )
    drawCircle(
        Color(0xFFFFF59D),
        radius = size.height * 0.09f,
        center = Offset(size.width * 0.82f, size.height * 0.2f)
    )

    // Clouds (slow parallax)
    val span = size.width + 400f
    for (i in 0 until 6) {
        var cx = (i * span / 6f - camX * sc * 0.08f) % span
        if (cx < 0f) cx += span
        cx -= 200f
        val cy = size.height * (0.1f + 0.07f * ((i * 37) % 5))
        val r = size.height * 0.04f
        val cloud = Color.White.copy(alpha = 0.85f)
        drawCircle(cloud, r, Offset(cx, cy))
        drawCircle(cloud, r * 1.3f, Offset(cx + r * 1.2f, cy - r * 0.3f))
        drawCircle(cloud, r, Offset(cx + r * 2.4f, cy))
    }

    // Mountain layers (parallax)
    fun mountains(color: Color, factor: Float, baseFrac: Float, amp: Float, freq: Float) {
        val p = Path()
        p.moveTo(0f, size.height)
        var sx = 0f
        while (sx <= size.width + 16f) {
            val t = (sx + camX * sc * factor) / freq
            val my = size.height * baseFrac -
                (sin(t) * 0.6f + sin(t * 2.3f + 1f) * 0.4f + 1f) * amp * size.height
            p.lineTo(sx, my)
            sx += 16f
        }
        p.lineTo(size.width + 16f, size.height)
        p.close()
        drawPath(p, color)
    }
    mountains(Color(0xFF90A4AE), 0.15f, 0.72f, 0.10f, 260f)
    mountains(Color(0xFF78909C), 0.30f, 0.82f, 0.08f, 180f)
}

private fun DrawScope.drawTerrain(camX: Float, camY: Float, viewW: Float) {
    val bottom = camY + VIEW_H + 200f
    val fill = Path()
    val top = Path()
    var wx = camX - 24f
    val end = camX + viewW + 24f
    val first = Terrain.heightAt(wx)
    fill.moveTo(wx, bottom)
    fill.lineTo(wx, first)
    top.moveTo(wx, first)
    while (wx <= end) {
        val gy = Terrain.heightAt(wx)
        fill.lineTo(wx, gy)
        top.lineTo(wx, gy)
        wx += 12f
    }
    fill.lineTo(wx, bottom)
    fill.close()

    drawPath(fill, Brush.verticalGradient(
        colors = listOf(Color(0xFF8D6E4B), Color(0xFF4E342E)),
        startY = camY + 200f,
        endY = bottom
    ))
    // Grass line
    drawPath(top, Color(0xFF558B2F), style = Stroke(26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(top, Color(0xFF8BC34A), style = Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Distance flags every 1000 world units (25 m)
    var fx = (camX / 1000f).toInt() * 1000f
    while (fx < end) {
        if (fx > 0f && fx >= camX - 50f) {
            val gy = Terrain.heightAt(fx)
            drawLine(Color.White, Offset(fx, gy), Offset(fx, gy - 70f), strokeWidth = 5f)
            val flag = Path().apply {
                moveTo(fx, gy - 70f)
                lineTo(fx + 36f, gy - 58f)
                lineTo(fx, gy - 46f)
                close()
            }
            drawPath(flag, Color(0xFFE53935))
        }
        fx += 1000f
    }
}

private fun DrawScope.drawCar(e: GameEngine) {
    val cx = e.x
    val cy = e.y
    rotate(degrees = (e.angle * 180f / PI.toFloat()), pivot = Offset(cx, cy)) {
        // Wheels (behind body)
        for (wxOff in floatArrayOf(-GameEngine.WHEEL_X, GameEngine.WHEEL_X)) {
            val wc = Offset(cx + wxOff, cy + GameEngine.WHEEL_Y)
            val r = GameEngine.WHEEL_R
            drawCircle(Color(0xFF212121), r, wc)
            drawCircle(Color(0xFF424242), r * 0.72f, wc)
            drawCircle(Color(0xFFBDBDBD), r * 0.4f, wc)
            for (k in 0 until 3) {
                val a = e.wheelSpin + k * (2f * PI.toFloat() / 3f)
                drawLine(
                    Color(0xFF212121),
                    wc,
                    Offset(wc.x + cos(a) * r * 0.7f, wc.y + sin(a) * r * 0.7f),
                    strokeWidth = 4f
                )
            }
        }

        // Body
        drawRoundRect(
            Color(0xFFE53935),
            topLeft = Offset(cx - 62f, cy - 20f),
            size = Size(124f, 36f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        // Cabin
        val cabin = Path().apply {
            moveTo(cx - 24f, cy - 20f)
            lineTo(cx - 12f, cy - 48f)
            lineTo(cx + 24f, cy - 48f)
            lineTo(cx + 38f, cy - 20f)
            close()
        }
        drawPath(cabin, Color(0xFFC62828))
        val window = Path().apply {
            moveTo(cx - 17f, cy - 22f)
            lineTo(cx - 8f, cy - 43f)
            lineTo(cx + 21f, cy - 43f)
            lineTo(cx + 31f, cy - 22f)
            close()
        }
        drawPath(window, Color(0xFFB3E5FC))
        // Driver
        drawCircle(Color(0xFFFFCC80), 9f, Offset(cx + 8f, cy - 33f))
        drawCircle(Color(0xFFFFEB3B), 10f, Offset(cx + 8f, cy - 37f))
        // Stripe, headlight, bumper
        drawRect(Color.White.copy(alpha = 0.85f), Offset(cx - 58f, cy - 6f), Size(116f, 5f))
        drawCircle(Color(0xFFFFF59D), 6f, Offset(cx + 56f, cy - 8f))
        drawRoundRect(
            Color(0xFF37474F),
            topLeft = Offset(cx - 66f, cy + 8f),
            size = Size(132f, 8f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}
