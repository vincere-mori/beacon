package app.mayak.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private enum class LhState { OFF, CONNECTING, ON, DISCONNECTING }

private data class LhStar(
    val nx: Float, val ny: Float, val size: Float,
    val baseAlpha: Float, val phase: Long,
    val p1: Float, val p2: Float
)

private fun buildStars(n: Int): List<LhStar> {
    val rng = java.util.Random(42)
    return (0 until n).map {
        val b = rng.nextFloat()
        LhStar(
            nx = rng.nextFloat(), ny = rng.nextFloat(),
            size = when { b > 0.92f -> 3f; b > 0.7f -> 2f; else -> 1f },
            baseAlpha = 0.35f + b * 0.65f,
            phase = (rng.nextDouble() * 3000).toLong(),
            p1 = 500f + rng.nextFloat() * 1500f,
            p2 = 800f + rng.nextFloat() * 2200f
        )
    }
}

private fun lerpC(a: Int, b: Int, t: Float): Float =
    ((a + (b - a) * t).toInt().coerceIn(0, 255)) / 255f

@Composable
fun LighthouseHero(
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val heroState = when {
        disconnecting -> LhState.DISCONNECTING
        connected -> LhState.ON
        connecting -> LhState.CONNECTING
        else -> LhState.OFF
    }
    val heroRef by rememberUpdatedState(heroState)

    var sweepPhase by remember { mutableDoubleStateOf(0.0) }
    var sweepSpeed by remember { mutableDoubleStateOf(0.0) }
    val waveOffsets = remember { FloatArray(4) }
    var bulbColorT by remember { mutableFloatStateOf(0f) }
    var bulbColorTarget by remember { mutableFloatStateOf(0f) }
    var nextColorShift by remember { mutableLongStateOf(5000L) }
    var frameTime by remember { mutableLongStateOf(0L) }
    var lightAlpha by remember { mutableFloatStateOf(0f) }
    val stars = remember { buildStars(70) }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (isActive) {
            withFrameMillis { t ->
                val hs = heroRef
                val targetLight = when (hs) {
                    LhState.OFF, LhState.DISCONNECTING -> 0f
                    LhState.CONNECTING -> 0.62f
                    LhState.ON -> 1f
                }
                val minStep = when {
                    hs == LhState.CONNECTING || hs == LhState.DISCONNECTING -> 16L
                    abs(lightAlpha - targetLight) > 0.01f -> 16L
                    hs == LhState.ON -> 32L
                    else -> 48L
                }
                if (t - lastFrame < minStep) return@withFrameMillis
                val dt = if (lastFrame == 0L) minStep else t - lastFrame
                lastFrame = t
                frameTime = t
                val k = dt / 32.0 // нормировка под разный fps, чтобы скорость не плавала
                val targetSpeed = when (hs) {
                    LhState.ON -> 0.024
                    LhState.CONNECTING -> 0.09
                    LhState.OFF, LhState.DISCONNECTING -> 0.0
                }
                sweepSpeed += (targetSpeed - sweepSpeed) * 0.12 * k
                sweepPhase += sweepSpeed * k
                for (i in waveOffsets.indices) waveOffsets[i] += (0.70f + i * 0.24f) * k.toFloat()
                val lightFactor = (if (targetLight < lightAlpha) 0.055f else 0.075f) * k.toFloat()
                lightAlpha += (targetLight - lightAlpha) * lightFactor.coerceAtMost(1f)
                if (lightAlpha < 0.004f) lightAlpha = 0f
                if (hs == LhState.ON) {
                    if (t >= nextColorShift) {
                        bulbColorTarget = if (bulbColorTarget < 0.5f) 0.9f else 0f
                        nextColorShift = t + 5000 + (Math.random() * 8000).toLong()
                    }
                    bulbColorT += (bulbColorTarget - bulbColorT) * 0.012f * k.toFloat()
                } else {
                    bulbColorT += (0f - bulbColorT) * 0.08f * k.toFloat()
                }
            }
        }
    }

    // статичные слои (небо+луна, маяк, скала) не меняются между кадрами —
    // рендерим их один раз в bitmap, каждый кадр гоняем только динамику
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.66f
            val cx = w / 2f

            val towerH = h * 0.48f
            val baseY = h * 0.83f
            val plinthH = towerH * 0.07f
            val plinth2H = plinthH * 0.55f
            val tbY = baseY - plinthH - plinth2H
            val towerTopY = baseY - towerH + towerH * 0.20f
            val galleryH = towerH * 0.045f
            val galleryY = towerTopY - galleryH
            val lantH = towerH * 0.16f
            val lantTopY = galleryY - lantH
            val bulbCy = lantTopY + lantH / 2f

            val skyLayer = staticLayer {
                drawLhSky(w, h)
                drawLhMoon(w, horizonY)
            }
            val towerLayer = staticLayer {
                drawLhLighthouse(cx, baseY, towerH, towerTopY, tbY, galleryY, galleryH, lantTopY, lantH, plinthH, plinth2H)
            }
            val cliffLayer = staticLayer {
                drawLhCliff(w, h)
            }

            onDrawBehind {
                drawImage(skyLayer)
                drawLhStars(stars, w, horizonY, frameTime)
                if (lightAlpha > 0.01f) {
                    drawLhBeam(
                        cx,
                        bulbCy,
                        w,
                        horizonY,
                        sweepPhase.toFloat(),
                        heroState,
                        bulbColorT,
                        frameTime,
                        lightAlpha
                    )
                }
                drawLhSea(w, h, horizonY, waveOffsets)
                drawImage(towerLayer)
                drawLhBulbGlow(cx, bulbCy, heroState, bulbColorT, frameTime, lightAlpha)
                drawImage(cliffLayer)
            }
        }
    )
}

private fun CacheDrawScope.staticLayer(block: DrawScope.() -> Unit): ImageBitmap {
    val wPx = size.width.toInt().coerceAtLeast(1)
    val hPx = size.height.toInt().coerceAtLeast(1)
    val img = ImageBitmap(wPx, hPx)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(img), size, block)
    return img
}

private fun DrawScope.drawLhSky(w: Float, h: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0F1535), Color(0xFF141C46), Color(0xFF243060)),
            startY = 0f, endY = h
        ),
        size = Size(w, h)
    )
}

private fun DrawScope.drawLhMoon(w: Float, horizonY: Float) {
    val mx = w * 0.76f
    val my = horizonY * 0.20f
    val r = 11f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x1CD2DCFF), Color(0x00D2DCFF)),
            center = Offset(mx, my), radius = r * 5.5f
        ),
        center = Offset(mx, my), radius = r * 5.5f
    )
    drawCircle(color = Color(0xFFF2F6FF), center = Offset(mx, my), radius = r)
    drawCircle(color = Color(0xFF12183E), center = Offset(mx + r * 0.44f, my), radius = r * 0.94f)
}

private fun DrawScope.drawLhStars(stars: List<LhStar>, w: Float, horizonY: Float, time: Long) {
    for (s in stars) {
        val x = s.nx * w
        val y = s.ny * horizonY * 0.95f
        val tw1 = (sin((time + s.phase) / s.p1.toDouble()) * 0.5 + 0.5).toFloat()
        val tw2 = (sin((time + s.phase * 2L) / s.p2.toDouble()) * 0.5 + 0.5).toFloat()
        val alpha = (s.baseAlpha * (0.25f + (tw1 * 0.6f + tw2 * 0.4f) * 0.85f)).coerceIn(0f, 1f)
        drawCircle(color = Color(0.96f, 0.98f, 1f, alpha), center = Offset(x, y), radius = s.size)
    }
}

private fun DrawScope.drawLhBeam(
    cx: Float, cy: Float, w: Float, horizonY: Float,
    sweepPhase: Float, state: LhState, bulbColorT: Float, time: Long, lightAlpha: Float
) {
    val pulse = if (state == LhState.CONNECTING) {
        (0.72f + sin(time / 130.0).toFloat() * 0.28f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val visibility = lightAlpha * pulse
    if (visibility <= 0.01f) return

    val warmth = if (state == LhState.CONNECTING) 1f else bulbColorT
    val glowR = lerpC(120, 255, warmth)
    val glowG = lerpC(200, 195, warmth)
    val glowB = lerpC(255, 90, warmth)

    val targetX = cx + sin(sweepPhase.toDouble()).toFloat() * w * 0.34f
    val targetY = horizonY
    val spread = w * 0.17f

    fun beamPath(s: Float) = Path().apply {
        moveTo(cx, cy); lineTo(targetX - spread * s, targetY); lineTo(targetX + spread * s, targetY); close()
    }

    drawPath(beamPath(1f), color = Color(glowR, glowG, glowB, 0.08f * visibility))
    drawPath(beamPath(0.6f), color = Color(glowR, glowG, glowB, 0.18f * visibility))
    drawPath(beamPath(0.3f), color = Color(glowR, glowG, glowB, 0.32f * visibility))
}

private fun DrawScope.drawLhSea(w: Float, h: Float, horizonY: Float, waveOffsets: FloatArray) {
    val fadeTop = horizonY - 40f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0x00122846), Color(0xFF040818)),
            startY = fadeTop, endY = h
        ),
        topLeft = Offset(0f, fadeTop), size = Size(w, h - fadeTop)
    )
    for (i in waveOffsets.indices) {
        val y = horizonY + 4f + i * (h - horizonY) * 0.075f
        val amp = 2f + i * 2.5f
        val period = 28f + i * 22f
        val alpha = (0.22f - i * 0.045f).coerceAtLeast(0.06f)
        val t = i / waveOffsets.size.toFloat()
        val wavePath = Path()
        var x = -10f
        wavePath.moveTo(x, y)
        while (x <= w + 10f) {
            wavePath.lineTo(x, y + sin((x + waveOffsets[i]) / period * PI * 2).toFloat() * amp)
            x += 6f
        }
        drawPath(
            path = wavePath,
            color = Color(0.67f - t * 0.24f, 0.82f - t * 0.20f, 1f, alpha),
            style = Stroke(width = 1.1f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawLhLighthouse(
    cx: Float, baseY: Float, towerH: Float, towerTopY: Float, tbY: Float,
    galleryY: Float, galleryH: Float, lantTopY: Float, lantH: Float,
    plinthH: Float, plinth2H: Float
) {
    val topW = towerH * 0.20f
    val bottomW = towerH * 0.34f

    val pierW = bottomW * 2f
    val pierH = towerH * 0.10f
    val pierY = baseY - 2f

    val islandPath = Path().apply {
        moveTo(cx - pierW / 2f, pierY + 4f)
        cubicTo(cx - pierW * 0.45f, pierY - pierH * 0.4f, cx - pierW * 0.25f, pierY - pierH * 0.9f, cx, pierY - pierH)
        cubicTo(cx + pierW * 0.25f, pierY - pierH * 0.9f, cx + pierW * 0.45f, pierY - pierH * 0.4f, cx + pierW / 2f, pierY + 4f)
        close()
    }
    drawPath(
        path = islandPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF384470), Color(0xFF141C38)),
            startY = pierY - pierH, endY = pierY
        )
    )

    val plinthW = bottomW * 1.35f
    val plinthY = baseY - plinthH
    drawRoundRect(Color(0xFF283460), topLeft = Offset(cx - plinthW / 2f, plinthY), size = Size(plinthW, plinthH), cornerRadius = CornerRadius(8f))

    val plinth2W = plinthW * 0.85f
    val plinth2Y = plinthY - plinth2H
    drawRoundRect(Color(0xFF303E6C), topLeft = Offset(cx - plinth2W / 2f, plinth2Y), size = Size(plinth2W, plinth2H), cornerRadius = CornerRadius(6f))

    val towerPath = Path().apply {
        moveTo(cx - topW / 2f, towerTopY); lineTo(cx + topW / 2f, towerTopY)
        lineTo(cx + bottomW / 2f, tbY); lineTo(cx - bottomW / 2f, tbY); close()
    }
    drawPath(
        path = towerPath,
        color = Color(0xFFF6F8FC)
    )
    drawPath(towerPath, color = Color(0x5AB9C6E1), style = Stroke(1f))

    val bandColors = listOf(Color(0xFF3C6EE6), Color(0xFFDC4650), Color(0xFF3C6EE6))
    for (i in bandColors.indices) {
        val t = (i + 1f) / (bandColors.size + 1f)
        val bcy = towerTopY + (tbY - towerTopY) * t
        val bw = topW + (bottomW - topW) * t
        val bh = towerH * 0.045f
        drawRoundRect(bandColors[i], topLeft = Offset(cx - bw / 2f + 3f, bcy - bh / 2f), size = Size(bw - 6f, bh), cornerRadius = CornerRadius(5f))
    }

    val galleryW = topW * 1.7f
    drawRoundRect(Color(0xFF283460), topLeft = Offset(cx - galleryW / 2f, galleryY), size = Size(galleryW, galleryH), cornerRadius = CornerRadius(4f))
    drawRect(Color(0xFFB4C3E6), topLeft = Offset(cx - galleryW / 2f + 2f, galleryY - 1f), size = Size(galleryW - 4f, 2f))

    val lantW = topW * 1.45f
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF141E46), Color(0xFF0C1432)),
            start = Offset(cx - lantW / 2f, lantTopY), end = Offset(cx + lantW / 2f, lantTopY + lantH)
        ),
        topLeft = Offset(cx - lantW / 2f, lantTopY), size = Size(lantW, lantH), cornerRadius = CornerRadius(6f)
    )
    drawRoundRect(
        color = Color(0x96788CB4), topLeft = Offset(cx - lantW / 2f, lantTopY),
        size = Size(lantW, lantH), cornerRadius = CornerRadius(6f), style = Stroke(1f)
    )
    listOf(-0.25f, 0f, 0.25f).forEach { offset ->
        val x = (cx + lantW * offset).let { kotlin.math.round(it) }
        drawLine(
            color = Color(0xB8A7B9DC),
            start = Offset(x, lantTopY + 2f),
            end = Offset(x, lantTopY + lantH - 2f),
            strokeWidth = 1f
        )
    }

    val roofW = lantW * 1.18f
    val roofH = lantH * 1.05f
    val roofPath = Path().apply {
        moveTo(cx, lantTopY - roofH)
        lineTo(cx - roofW / 2f - 2f, lantTopY + 2f)
        quadraticTo(cx, lantTopY + 5f, cx + roofW / 2f + 2f, lantTopY + 2f)
        close()
    }
    drawPath(
        path = roofPath,
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFBE3239), Color(0xFF8C1E28)),
            startX = cx - roofW / 2f, endX = cx + roofW / 2f
        )
    )
    val highlightPath = Path().apply {
        moveTo(cx - roofW * 0.35f, lantTopY - 2f)
        quadraticTo(cx - roofW * 0.18f, lantTopY - roofH * 0.7f, cx - 1f, lantTopY - roofH + 4f)
    }
    drawPath(highlightPath, color = Color(1f, 1f, 1f, 0.16f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

    val ballR = (towerH * 0.018f).coerceAtLeast(3f)
    val ballY = lantTopY - roofH - ballR * 2f - 2f
    drawCircle(Color(0xFFF0F5FF), radius = ballR, center = Offset(cx, ballY))
    drawLine(Color(0xFFB4C3E6), start = Offset(cx, ballY + ballR * 2f), end = Offset(cx, lantTopY - roofH), strokeWidth = 1.2f)
}

private fun DrawScope.drawLhBulbGlow(
    cx: Float,
    cy: Float,
    state: LhState,
    bulbColorT: Float,
    time: Long,
    lightAlpha: Float
) {
    val pulseAlpha = if (state == LhState.CONNECTING) {
        (0.72f + sin(time / 180.0).toFloat() * 0.28f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val onAlpha = (0.08f + lightAlpha * 0.92f) * pulseAlpha
    val pulse = when (state) {
        LhState.ON -> 1f + sin(time / 900.0).toFloat() * 0.07f
        LhState.CONNECTING -> 1f + sin(time / 180.0).toFloat() * 0.11f
        LhState.OFF, LhState.DISCONNECTING -> 1f
    }
    val bulbR = (7f * pulse).coerceAtLeast(4f)
    val t = bulbColorT
    val haloColor = Color(lerpC(120, 255, t), lerpC(200, 185, t), lerpC(255, 60, t))
    val coreColor = Color(lerpC(200, 255, t), lerpC(240, 210, t), lerpC(255, 120, t), onAlpha)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(haloColor.copy(alpha = 0.78f * onAlpha), haloColor.copy(alpha = 0f)),
            center = Offset(cx, cy), radius = bulbR * 4f
        ),
        center = Offset(cx, cy), radius = bulbR * 4f
    )
    val lensSize = Size(bulbR * 3.2f, bulbR * 1.7f)
    val lensTopLeft = Offset(cx - lensSize.width / 2f, cy - lensSize.height / 2f)
    drawOval(
        brush = Brush.horizontalGradient(
            colors = listOf(
                haloColor.copy(alpha = 0.12f * onAlpha),
                coreColor.copy(alpha = 0.7f * onAlpha),
                haloColor.copy(alpha = 0.12f * onAlpha)
            ),
            startX = lensTopLeft.x,
            endX = lensTopLeft.x + lensSize.width
        ),
        topLeft = lensTopLeft,
        size = lensSize
    )
    drawOval(
        color = Color(0xFFDCEEFF).copy(alpha = 0.68f),
        topLeft = lensTopLeft,
        size = lensSize,
        style = Stroke(1f)
    )
    for (step in -1..1) {
        val y = cy + step * lensSize.height / 4.5f
        val halfWidth = lensSize.width * (0.43f - kotlin.math.abs(step) * 0.04f)
        drawLine(
            color = Color.White.copy(alpha = 0.28f * onAlpha),
            start = Offset(cx - halfWidth, y),
            end = Offset(cx + halfWidth, y),
            strokeWidth = 0.7f,
            cap = StrokeCap.Round
        )
    }
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                coreColor.copy(alpha = 0.5f * onAlpha),
                coreColor.copy(alpha = 0f)
            ),
            center = Offset(cx, cy),
            radius = lensSize.width * 0.42f
        ),
        topLeft = lensTopLeft,
        size = lensSize
    )
}

private fun DrawScope.drawLhCliff(w: Float, h: Float) {
    val pts = arrayOf(
        0.00f to 0.76f, 0.05f to 0.71f, 0.10f to 0.74f, 0.16f to 0.69f,
        0.22f to 0.75f, 0.29f to 0.82f, 0.37f to 0.88f, 0.46f to 0.935f,
        0.54f to 0.935f, 0.63f to 0.88f, 0.71f to 0.82f, 0.78f to 0.75f,
        0.84f to 0.70f, 0.90f to 0.74f, 0.95f to 0.71f, 1.00f to 0.76f
    )
    val cliffPath = Path().apply {
        moveTo(0f, h); lineTo(0f, pts[0].second * h)
        for (p in pts) lineTo(p.first * w, p.second * h)
        lineTo(w, h); close()
    }
    drawPath(
        path = cliffPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF101529), Color(0xFF03040C)),
            startY = h * 0.70f, endY = h
        )
    )
    val rimPath = Path().apply {
        moveTo(0f, pts[0].second * h)
        for (p in pts) lineTo(p.first * w, p.second * h)
    }
    drawPath(rimPath, color = Color(0x738CAFD8), style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
