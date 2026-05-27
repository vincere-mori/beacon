package app.beacon.desktop

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.sin

class LighthouseHero : JPanel() {

    enum class HeroState { OFF, CONNECTING, ON }

    var heroState: HeroState = HeroState.OFF
        set(value) {
            if (field == value) return
            field = value
            stateChangedAt = System.currentTimeMillis()
        }

    private var stateChangedAt = System.currentTimeMillis()

    private var sweepPhase = 0.0
    private var sweepSpeed = 0.0

    private val stars = generateStars(90)
    private val waveOffsets = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    private val shootingStars = mutableListOf<ShootingStar>()
    private var nextShootingAt = System.currentTimeMillis() + 4000

    private var fogAlpha = 0f

    // Bulb warm-color drift: 0 = cool blue-white, 1 = warm yellow
    private var bulbColorT      = 0f
    private var bulbColorTarget = 0f
    private var nextColorShiftAt = System.currentTimeMillis() + 5000L

    // Pre-allocated paths — reused every frame via reset() to avoid GC pressure
    private val wavePaths     = Array(4) { GeneralPath() }
    private val beamPath      = GeneralPath()
    private val islandPath    = GeneralPath()
    private val foamPath      = GeneralPath()
    private val towerPath     = GeneralPath()
    private val roofPath      = GeneralPath()
    private val highlightPath = GeneralPath()
    private val reflPath      = GeneralPath()
    private val rockPath      = GeneralPath()
    private val cliffPath     = GeneralPath()

    private val timer: Timer = Timer(16) {
        val now = System.currentTimeMillis()
        val targetSpeed = when (heroState) {
            HeroState.ON -> 0.012
            HeroState.CONNECTING -> 0.045
            HeroState.OFF -> 0.0
        }
        sweepSpeed += (targetSpeed - sweepSpeed) * 0.06
        sweepPhase += sweepSpeed

        for (i in waveOffsets.indices) waveOffsets[i] += 0.35 + i * 0.12

        if (now >= nextShootingAt) {
            shootingStars += ShootingStar.spawn(width, height)
            nextShootingAt = now + (3500 + Math.random() * 7000).toLong()
        }
        shootingStars.removeAll { it.tick() }

        val fogTarget = when (heroState) {
            HeroState.OFF -> 0f
            HeroState.CONNECTING -> 0.50f
            HeroState.ON -> 1.0f
        }
        fogAlpha += (fogTarget - fogAlpha) * 0.028f

        // Bulb color: slowly drifts to warm yellow and back when ON
        if (heroState == HeroState.ON) {
            if (now >= nextColorShiftAt) {
                bulbColorTarget = if (bulbColorTarget < 0.5f) 0.9f else 0f
                nextColorShiftAt = now + (4000 + (Math.random() * 9000)).toLong()
            }
            bulbColorT += (bulbColorTarget - bulbColorT) * 0.006f
        } else {
            bulbColorT += (0f - bulbColorT) * 0.04f
        }

        // Adaptive frame rate: 60fps when active, ~30fps while fading, ~20fps when idle
        val targetDelay = when {
            heroState != HeroState.OFF -> 16
            fogAlpha > 0.01f || shootingStars.isNotEmpty() -> 33
            else -> 50
        }
        if (timer.delay != targetDelay) timer.delay = targetDelay

        repaint()
    }

    init {
        isOpaque = false
        preferredSize = Dimension(420, 260)
        minimumSize = Dimension(280, 170)
        maximumSize = Dimension(520, 320)
        timer.start()
    }

    fun pauseAnimation() { timer.stop() }
    fun resumeAnimation() { if (!timer.isRunning) timer.start() }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT)

        val w = width
        val h = height

        val clip = java.awt.geom.RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), 18f, 18f)
        g2.clip(clip)
        val baseX = w / 2
        val horizonY = (h * 0.66).toInt()

        val towerH    = (h * 0.48).toInt()
        val baseY     = (h * 0.83).toInt()
        val towerTopY = baseY - towerH + (towerH * 0.20).toInt()
        val galleryH  = (towerH * 0.045).toInt()
        val galleryY  = towerTopY - galleryH
        val lantH     = (towerH * 0.16).toInt()
        val lantTopY  = galleryY - lantH
        val bulbCy    = lantTopY + lantH / 2

        drawSky(g2, w, h, horizonY)
        drawMoon(g2, w, horizonY)
        drawStars(g2, w, horizonY)
        drawShootingStars(g2)
        drawAtmosphereGlow(g2, baseX, bulbCy, w, h)
        drawBeam(g2, baseX, bulbCy, w, h, horizonY)
        drawSea(g2, w, h, horizonY, baseX)
        drawStonePier(g2, baseX, baseY, towerH)
        drawIslandWaterBlend(g2, baseX, baseY, towerH)
        drawLighthouseReflection(g2, baseX, horizonY, towerH)
        drawLighthouse(g2, baseX, baseY, towerH)
        drawBulbGlow(g2, baseX, bulbCy)
        drawForegroundCliff(g2, w, h)
        drawCloak(g2, w, h, horizonY)
        drawEdgeFade(g2, w, h)
    }

    private fun drawSky(g2: Graphics2D, w: Int, h: Int, horizonY: Int) {
        val sky1 = Color(15, 21, 53)
        val sky2 = Color(20, 28, 70)
        val sky3 = Color(36, 48, 96)
        g2.paint = GradientPaint(0f, 0f, sky1, 0f, h * 0.35f, sky2)
        g2.fillRect(0, 0, w, (h * 0.35).toInt())
        // Continue the sky gradient all the way to the bottom of the panel so
        // the sea layer (alpha-fade from the horizon) always has a matching
        // background underneath. No seam is geometrically possible.
        g2.paint = GradientPaint(0f, h * 0.35f, sky2, 0f, h.toFloat(), sky3)
        g2.fillRect(0, (h * 0.35).toInt(), w, h - (h * 0.35).toInt())
    }

    /** Crescent moon — subtle, top-right of the sky. */
    private fun drawMoon(g2: Graphics2D, w: Int, horizonY: Int) {
        val mx = (w * 0.76).toFloat()
        val my = (horizonY * 0.20).toFloat()
        val r  = 11f
        val old = g2.paint

        g2.paint = RadialGradientPaint(mx, my, r * 5.5f,
            floatArrayOf(0f, 1f),
            arrayOf(Color(210, 220, 255, 28), Color(210, 220, 255, 0)))
        g2.fillOval((mx - r * 5.5f).toInt(), (my - r * 5.5f).toInt(), (r * 11).toInt(), (r * 11).toInt())

        g2.paint = RadialGradientPaint(mx, my, r * 2.2f,
            floatArrayOf(0f, 1f),
            arrayOf(Color(235, 240, 255, 55), Color(235, 240, 255, 0)))
        g2.fillOval((mx - r * 2.2f).toInt(), (my - r * 2.2f).toInt(), (r * 4.4f).toInt(), (r * 4.4f).toInt())

        g2.paint = old
        g2.color = Color(242, 246, 255)
        g2.fillOval((mx - r).toInt(), (my - r).toInt(), (r * 2).toInt(), (r * 2).toInt())

        g2.color = Color(18, 24, 62)
        val biteOffset = r * 0.44f
        val biteR = r * 0.94f
        g2.fillOval(
            (mx - biteR + biteOffset).toInt(), (my - biteR - 1).toInt(),
            (biteR * 2).toInt(), (biteR * 2 + 1).toInt()
        )
    }

    /**
     * The cloak — darkness rises from the viewer's side when connected.
     * Wisp data comes from pre-computed companion arrays; zero per-frame allocation.
     */
    private fun drawCloak(g2: Graphics2D, w: Int, h: Int, horizonY: Int) {
        if (fogAlpha < 0.01f) return
        val old = g2.composite
        val t   = System.currentTimeMillis() / 5500.0

        val cloakH = h * 0.52f
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (fogAlpha * 0.92f).coerceAtMost(1f))
        g2.paint = GradientPaint(
            0f, h - cloakH, Color(1, 3, 12, 0),
            0f, h.toFloat(),  Color(1, 3, 14, 255)
        )
        g2.fillRect(0, (h - cloakH).toInt(), w, cloakH.toInt())

        val bandTop = horizonY + (h - horizonY) * 0.18f
        val bandH   = (h - horizonY) * 0.34f
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (fogAlpha * 0.60f).coerceAtMost(1f))
        g2.paint = GradientPaint(
            0f, bandTop,         Color(3, 7, 24, 0),
            0f, bandTop + bandH, Color(2, 5, 18, 200)
        )
        g2.fillRect(0, bandTop.toInt(), w, bandH.toInt())

        for (i in 0 until WISP_COUNT) {
            val driftX = sin(t * 0.38 + i * 1.71) * 0.036
            val wx = ((WISP_XF[i] + driftX) * w).toFloat()
            val wy = (WISP_YF[i] * h).toFloat()
            val rx = (WISP_RF[i] * w).toFloat()
            val ry = rx * 0.24f
            val a  = (fogAlpha * WISP_ST[i] *
                     (0.78 + sin(t * 0.55 + i * 1.13) * 0.22)).toFloat().coerceIn(0f, 1f)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a)
            g2.color = WISP_COLOR
            g2.fillOval((wx - rx).toInt(), (wy - ry).toInt(), (rx * 2).toInt(), (ry * 2).toInt())
        }

        g2.composite = old
    }

    private fun drawStars(g2: Graphics2D, w: Int, horizonY: Int) {
        val time = System.currentTimeMillis()
        val savedComposite = g2.composite
        val savedStroke    = g2.stroke
        g2.stroke = STAR_CROSS_STROKE

        for (s in stars) {
            val xi = (s.x * w).toInt()
            val yi = (s.y * horizonY * 0.95).toInt()

            val tw1 = sin((time + s.phase) / s.twinklePeriod1) * 0.5 + 0.5
            val tw2 = sin((time + s.phase * 2) / s.twinklePeriod2) * 0.5 + 0.5
            val intensity = tw1 * 0.6 + tw2 * 0.4
            val alpha = (s.baseAlpha * (0.25 + intensity * 0.85)).coerceIn(0.0, 1.0)

            if (s.size >= 2) {
                val haloFraction = ((intensity - 0.35) / 0.65).coerceIn(0.0, 1.0)
                val haloAlpha = (alpha * 0.28 * haloFraction).toFloat()
                if (haloAlpha > 0.01f) {
                    val hr = s.size + 2
                    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, haloAlpha)
                    g2.color = STAR_HALO_COLOR
                    g2.fillOval(xi - hr, yi - hr, hr * 2, hr * 2)
                }
            }

            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.toFloat())
            g2.color = STAR_COLOR
            val r = s.size
            g2.fillOval(xi - r, yi - r, r * 2, r * 2)

            if (s.size >= 2 && intensity > 0.82) {
                val crossFraction = ((intensity - 0.82) / 0.18).coerceIn(0.0, 1.0)
                val crossAlpha = (alpha * 0.7 * crossFraction).toFloat()
                if (crossAlpha > 0.01f) {
                    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, crossAlpha)
                    g2.color = Color.WHITE
                    val cl = 3
                    g2.drawLine(xi - cl, yi, xi + cl, yi)
                    g2.drawLine(xi, yi - cl, xi, yi + cl)
                }
            }
        }

        g2.composite = savedComposite
        g2.stroke = savedStroke
    }

    private fun drawShootingStars(g2: Graphics2D) {
        val old = g2.composite
        g2.stroke = SHOOTING_STROKE
        for (s in shootingStars) {
            val headX = s.x.toFloat()
            val headY = s.y.toFloat()
            val tailX = (s.x - s.tailDx).toFloat()
            val tailY = (s.y - s.tailDy).toFloat()
            val alpha = s.alpha()
            g2.paint = GradientPaint(
                headX, headY, Color(255, 255, 255, (230 * alpha).toInt().coerceIn(0, 255)),
                tailX, tailY, Color(180, 200, 255, 0)
            )
            g2.drawLine(headX.toInt(), headY.toInt(), tailX.toInt(), tailY.toInt())
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.toFloat())
            g2.color = Color.WHITE
            g2.fillOval(headX.toInt() - 2, headY.toInt() - 2, 4, 4)
        }
        g2.composite = old
    }

    /**
     * Gaseous cloud-like glow around the lantern.
     * Blob layout comes from pre-computed companion arrays; no per-frame list/object allocation.
     */
    private fun drawAtmosphereGlow(g2: Graphics2D, cx: Int, cy: Int, w: Int, h: Int) {
        val intensity = when (heroState) {
            HeroState.ON -> 1.0f
            HeroState.CONNECTING -> (0.55 + sin((System.currentTimeMillis() - stateChangedAt) / 240.0) * 0.25).toFloat()
            HeroState.OFF -> 0.0f
        }
        if (intensity <= 0.01f) return

        val oldComposite = g2.composite
        val warm = heroState == HeroState.CONNECTING
        val haloColor = if (warm) Color(255, 210, 100) else Color(120, 200, 255)
        val hR = haloColor.red; val hG = haloColor.green; val hB = haloColor.blue
        val t = System.currentTimeMillis() / 5000.0

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
        for (i in 0 until BLOB_COUNT) {
            val driftX = sin(t * 0.8 + i * 1.37) * 0.018
            val driftY = cos(t * 0.6 + i * 1.05) * 0.014
            val blobX  = cx + (BLOB_BX[i] + driftX) * w
            val blobY  = cy + (BLOB_BY[i] + driftY) * w
            val blobR  = (BLOB_R[i] * w).toFloat()
            val pulse  = (0.82f + sin(t * 1.1 + i * 0.9).toFloat() * 0.18f).coerceIn(0.6f, 1f)
            val peak   = (BLOB_PEAK[i] * intensity * pulse).toInt().coerceIn(0, 255)
            val mid    = (peak * 0.30).toInt().coerceIn(0, 255)

            g2.paint = RadialGradientPaint(
                blobX.toFloat(), blobY.toFloat(), blobR,
                floatArrayOf(0.0f, 0.45f, 1f),
                arrayOf(Color(hR, hG, hB, peak), Color(hR, hG, hB, mid), Color(hR, hG, hB, 0))
            )
            g2.fillOval((blobX - blobR).toInt(), (blobY - blobR).toInt(),
                (blobR * 2).toInt(), (blobR * 2).toInt())
        }

        g2.composite = oldComposite
    }

    private fun drawBeam(g2: Graphics2D, originX: Int, originY: Int, w: Int, h: Int, horizonY: Int) {
        val visibility = when (heroState) {
            HeroState.ON -> 1.0f
            HeroState.CONNECTING -> (0.45 + sin((System.currentTimeMillis() - stateChangedAt) / 130.0) * 0.35).toFloat()
            HeroState.OFF -> 0.0f
        }
        if (visibility <= 0.01f) return

        val warmth = if (heroState == HeroState.CONNECTING) 1f else bulbColorT
        val core = Color(lerpInt(190, 255, warmth), lerpInt(235, 225, warmth), lerpInt(255, 140, warmth))
        val glow = Color(lerpInt(120, 255, warmth), lerpInt(200, 195, warmth), lerpInt(255, 90, warmth))
        val old  = g2.composite

        val targetX    = originX + sin(sweepPhase) * w * 0.34
        val targetY    = horizonY - h * 0.035
        val mainSpread = w * 0.17

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f * visibility)
        g2.color = glow
        g2.fill(buildBeamPath(originX, originY, w * 0.12, targetY, w * 0.28))
        g2.fill(buildBeamPath(originX, originY, w * 0.88, targetY, w * 0.28))

        for (li in BEAM_LAYER_SPREADS.indices) {
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BEAM_LAYER_ALPHAS[li] * visibility)
            g2.color = if (BEAM_LAYER_SPREADS[li] > 1.5) glow else core
            g2.fill(buildBeamPath(originX, originY, targetX, targetY, mainSpread * BEAM_LAYER_SPREADS[li]))
        }

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (0.34f * visibility).coerceAtMost(0.65f))
        g2.color = core
        g2.fill(buildBeamPath(originX, originY, targetX, targetY, mainSpread * 0.55))

        g2.composite = old
    }

    /** Builds the beam shape into the pre-allocated [beamPath] — no allocation. */
    private fun buildBeamPath(originX: Int, originY: Int, targetX: Double, targetY: Double, spread: Double): Path2D {
        beamPath.reset()
        val leftX  = targetX - spread
        val rightX = targetX + spread
        beamPath.moveTo(originX.toDouble(), originY.toDouble())
        beamPath.curveTo(
            originX + (leftX - originX) * 0.28, originY + (targetY - originY) * 0.12,
            originX + (leftX - originX) * 0.70, targetY - 18,
            leftX, targetY
        )
        beamPath.quadTo(targetX, targetY + 14, rightX, targetY)
        beamPath.curveTo(
            originX + (rightX - originX) * 0.70, targetY - 18,
            originX + (rightX - originX) * 0.28, originY + (targetY - originY) * 0.12,
            originX.toDouble(), originY.toDouble()
        )
        beamPath.closePath()
        return beamPath
    }

    private fun drawSea(g2: Graphics2D, w: Int, h: Int, horizonY: Int, lhX: Int) {
        // Single alpha-fade gradient — transparent above the horizon, fully
        // solid deep blue at the bottom. Sky already fills the full panel
        // height underneath, so the transition is mathematically seamless.
        val fadeTop = horizonY - 40
        g2.paint = GradientPaint(
            0f, fadeTop.toFloat(), Color(18, 28, 70, 0),
            0f, h.toFloat(),       Color(4, 8, 24, 255)
        )
        g2.fillRect(0, fadeTop, w, h - fadeTop)

        for (band in 0..5) {
            val y = horizonY + band * (h - horizonY) / 6
            g2.color = Color(40, 60, 110, 10)
            g2.fillRect(0, y, w, 2)
        }

        val old = g2.composite
        g2.stroke = WAVE_STROKE

        for ((i, offset) in waveOffsets.withIndex()) {
            val t      = i.toDouble() / waveOffsets.size
            val y      = horizonY + (4 + i * (h - horizonY) * 0.075).toInt()
            val amp    = 2.0 + i * 2.5
            val period = 28.0 + i * 22.0
            val alpha  = (0.22f - i * 0.045f).coerceAtLeast(0.06f)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            g2.color = Color(170 - (t * 60).toInt(), 210 - (t * 50).toInt(), 255)

            val path = wavePaths[i]
            path.reset()
            var x = -10.0
            path.moveTo(x, y.toDouble())
            while (x <= w + 10) {
                val py = y + sin((x + offset) / period * Math.PI * 2) * amp +
                        sin((x * 0.4 + offset * 1.3) / period * Math.PI * 2) * amp * 0.4
                path.lineTo(x, py)
                x += 3.0
            }
            g2.draw(path)

            if (i <= 2) {
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 1.3f)
                g2.color = Color(220, 240, 255)
                var fx = 0.0
                while (fx < w) {
                    val phase = sin((fx + offset) / period * Math.PI * 2)
                    if (phase > 0.85) {
                        val fy = y + phase * amp - 1
                        g2.fillOval(fx.toInt() - 1, fy.toInt(), 2, 2)
                    }
                    fx += 4.0
                }
            }
        }

        g2.composite = old
    }

    /**
     * Stone pier and rocks at the island's waterline — the visual anchor between
     * the lighthouse and the sea. Draws two flanking stone jetties with scattered
     * boulders, then foam where the water breaks against them.
     */
    private fun drawStonePier(g2: Graphics2D, baseX: Int, baseY: Int, towerH: Int) {
        val pw   = (towerH * 0.34).toFloat()   // half-width of island
        val py   = (baseY - 3).toFloat()        // waterline Y
        val old  = g2.composite

        val stoneDeep  = Color(22, 30, 58)
        val stoneMid   = Color(36, 48, 82)
        val stoneLight = Color(54, 70, 108)
        val foamCol    = Color(190, 215, 245, 160)

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)

        // ── Left jetty ──────────────────────────────────────────────────
        // Main stone slab extending left
        rockPath.reset()
        rockPath.moveTo(baseX - pw * 0.55f, py + 4)
        rockPath.lineTo(baseX - pw * 0.62f, py - 5)
        rockPath.lineTo(baseX - pw * 1.10f, py - 3)
        rockPath.lineTo(baseX - pw * 1.28f, py + 1)
        rockPath.lineTo(baseX - pw * 1.18f, py + 6)
        rockPath.lineTo(baseX - pw * 0.60f, py + 7)
        rockPath.closePath()
        g2.paint = GradientPaint(
            baseX - pw * 0.6f, py - 5f, stoneMid,
            baseX - pw * 0.6f, py + 7f, stoneDeep
        )
        g2.fill(rockPath)
        g2.color = stoneLight; g2.stroke = BasicStroke(0.8f); g2.draw(rockPath)

        // Boulder left-outer
        rockPath.reset()
        rockPath.moveTo(baseX - pw * 1.22f, py + 2)
        rockPath.lineTo(baseX - pw * 1.30f, py - 7)
        rockPath.lineTo(baseX - pw * 1.48f, py - 4)
        rockPath.lineTo(baseX - pw * 1.52f, py + 3)
        rockPath.closePath()
        g2.color = stoneDeep; g2.fill(rockPath)
        g2.color = stoneMid;  g2.draw(rockPath)

        // Small boulder far left
        rockPath.reset()
        rockPath.moveTo(baseX - pw * 1.55f, py + 1)
        rockPath.lineTo(baseX - pw * 1.60f, py - 5)
        rockPath.lineTo(baseX - pw * 1.72f, py - 2)
        rockPath.lineTo(baseX - pw * 1.68f, py + 4)
        rockPath.closePath()
        g2.color = Color(18, 25, 50); g2.fill(rockPath)

        // ── Right jetty ─────────────────────────────────────────────────
        rockPath.reset()
        rockPath.moveTo(baseX + pw * 0.55f, py + 4)
        rockPath.lineTo(baseX + pw * 0.62f, py - 5)
        rockPath.lineTo(baseX + pw * 1.10f, py - 3)
        rockPath.lineTo(baseX + pw * 1.28f, py + 1)
        rockPath.lineTo(baseX + pw * 1.18f, py + 6)
        rockPath.lineTo(baseX + pw * 0.60f, py + 7)
        rockPath.closePath()
        g2.paint = GradientPaint(
            baseX + pw * 0.6f, py - 5f, stoneMid,
            baseX + pw * 0.6f, py + 7f, stoneDeep
        )
        g2.fill(rockPath)
        g2.color = stoneLight; g2.stroke = BasicStroke(0.8f); g2.draw(rockPath)

        // Boulder right-outer
        rockPath.reset()
        rockPath.moveTo(baseX + pw * 1.22f, py + 2)
        rockPath.lineTo(baseX + pw * 1.30f, py - 7)
        rockPath.lineTo(baseX + pw * 1.48f, py - 4)
        rockPath.lineTo(baseX + pw * 1.52f, py + 3)
        rockPath.closePath()
        g2.color = stoneDeep; g2.fill(rockPath)
        g2.color = stoneMid;  g2.draw(rockPath)

        // Small boulder far right
        rockPath.reset()
        rockPath.moveTo(baseX + pw * 1.55f, py + 1)
        rockPath.lineTo(baseX + pw * 1.60f, py - 5)
        rockPath.lineTo(baseX + pw * 1.72f, py - 2)
        rockPath.lineTo(baseX + pw * 1.68f, py + 4)
        rockPath.closePath()
        g2.color = Color(18, 25, 50); g2.fill(rockPath)

        // ── Foam / surf line ────────────────────────────────────────────
        // Animated: slight offset per wave so it feels alive (waveOffsets[0] used)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f)
        g2.color = foamCol
        g2.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        // Left foam arcs along jetty edge
        g2.drawArc((baseX - pw * 1.32f).toInt(), (py - 4).toInt(),
            (pw * 0.80f).toInt(), 8, 15, 130)
        g2.drawArc((baseX - pw * 1.75f).toInt(), (py - 2).toInt(),
            (pw * 0.30f).toInt(), 6, 20, 120)
        // Right foam arcs
        g2.drawArc((baseX + pw * 0.52f).toInt(), (py - 4).toInt(),
            (pw * 0.80f).toInt(), 8, 35, -130)
        g2.drawArc((baseX + pw * 1.45f).toInt(), (py - 2).toInt(),
            (pw * 0.30f).toInt(), 6, 10, -120)

        // Thin surf-line connecting the whole structure
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f)
        g2.color = Color(210, 230, 255)
        g2.stroke = BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawLine((baseX - pw * 1.72f).toInt(), (py + 1).toInt(),
                    (baseX + pw * 1.72f).toInt(), (py + 1).toInt())

        g2.composite = old
    }

    /**
     * Softens the edge where the lighthouse island sits in the water.
     */
    private fun drawIslandWaterBlend(g2: Graphics2D, baseX: Int, baseY: Int, towerH: Int) {
        val pierW  = (towerH * 0.34 * 2.0).toInt()
        val pierY  = baseY - 2
        val old    = g2.composite

        val shadowW = (pierW * 1.3).toFloat()
        val shadowH = 26f
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.60f)
        g2.paint = RadialGradientPaint(
            baseX.toFloat(), (pierY + 10).toFloat(), shadowW / 2,
            floatArrayOf(0f, 0.55f, 1f),
            arrayOf(Color(2, 5, 18, 210), Color(4, 8, 26, 90), Color(2, 5, 18, 0))
        )
        g2.fillOval((baseX - shadowW / 2).toInt(), pierY - 2, shadowW.toInt(), shadowH.toInt())

        val ambientAlpha = when (heroState) {
            HeroState.ON -> 0.13f
            HeroState.CONNECTING -> 0.09f
            HeroState.OFF -> 0.05f
        }
        val ambientColor = if (heroState == HeroState.CONNECTING) Color(255, 210, 100) else Color(90, 160, 230)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ambientAlpha)
        g2.paint = RadialGradientPaint(
            baseX.toFloat(), (pierY + 4).toFloat(), (pierW * 0.65f),
            floatArrayOf(0f, 1f),
            arrayOf(ambientColor, Color(ambientColor.red, ambientColor.green, ambientColor.blue, 0))
        )
        g2.fillOval((baseX - pierW * 0.65).toInt(), (pierY - 4).toInt(), (pierW * 1.3).toInt(), 24)

        for (i in 0..2) {
            val rf = pierW * (0.53 + i * 0.11)
            val ra = (0.20f - i * 0.06f).coerceAtLeast(0.06f)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ra)
            g2.color = Color(130, 180, 230)
            g2.stroke = RIPPLE_STROKE
            g2.drawArc((baseX - rf).toInt(), pierY - 3, (rf * 2).toInt(), 12, 0, 180)
        }

        g2.composite = old
    }

    private fun drawLighthouse(g2: Graphics2D, baseX: Int, baseY: Int, towerH: Int) {
        val topW      = (towerH * 0.20).toInt()
        val bottomW   = (towerH * 0.34).toInt()
        val towerTopY = baseY - towerH + (towerH * 0.20).toInt()

        val pierW   = (bottomW * 2.0).toInt()
        val pierH   = (towerH * 0.10).toInt()
        val pierY   = baseY - 2
        val plinth2H = ((towerH * 0.07).toInt() * 0.55).toInt()
        val plinth2Y = baseY - (towerH * 0.07).toInt() - plinth2H
        val tbY     = plinth2Y.toDouble()

        islandPath.reset()
        islandPath.moveTo((baseX - pierW / 2.0), (pierY + 4).toDouble())
        islandPath.curveTo(
            (baseX - pierW * 0.45).toDouble(), (pierY - pierH * 0.4).toDouble(),
            (baseX - pierW * 0.25).toDouble(), (pierY - pierH * 0.9).toDouble(),
            baseX.toDouble(), (pierY - pierH).toDouble()
        )
        islandPath.curveTo(
            (baseX + pierW * 0.25).toDouble(), (pierY - pierH * 0.9).toDouble(),
            (baseX + pierW * 0.45).toDouble(), (pierY - pierH * 0.4).toDouble(),
            (baseX + pierW / 2.0), (pierY + 4).toDouble()
        )
        islandPath.closePath()
        g2.paint = GradientPaint(
            baseX.toFloat(), pierY - pierH.toFloat(), Color(56, 68, 110),
            baseX.toFloat(), pierY.toFloat(), Color(20, 28, 56)
        )
        g2.fill(islandPath)
        g2.color = Color(80, 100, 150, 80); g2.stroke = BasicStroke(1f); g2.draw(islandPath)
        g2.color = Color(180, 220, 255, 95)
        g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        foamPath.reset()
        foamPath.moveTo((baseX - pierW * 0.48).toDouble(), (pierY + 2).toDouble())
        foamPath.curveTo(
            (baseX - pierW * 0.24).toDouble(), (pierY - 6).toDouble(),
            (baseX + pierW * 0.24).toDouble(), (pierY - 6).toDouble(),
            (baseX + pierW * 0.48).toDouble(), (pierY + 2).toDouble()
        )
        g2.draw(foamPath)

        val plinthW = (bottomW * 1.35).toInt()
        val plinthH = (towerH * 0.07).toInt()
        val plinthY = baseY - plinthH
        g2.color = Color(40, 52, 96)
        g2.fillRoundRect(baseX - plinthW / 2, plinthY, plinthW, plinthH, 8, 8)
        g2.color = Color(70, 90, 140, 180)
        g2.drawRoundRect(baseX - plinthW / 2, plinthY, plinthW - 1, plinthH - 1, 8, 8)
        val plinth2W = (plinthW * 0.85).toInt()
        g2.color = Color(48, 62, 108)
        g2.fillRoundRect(baseX - plinth2W / 2, plinth2Y, plinth2W, plinth2H, 6, 6)

        val ttX = (baseX - topW / 2).toDouble()
        val ttY = towerTopY.toDouble()
        towerPath.reset()
        towerPath.moveTo(ttX, ttY)
        towerPath.lineTo((baseX + topW / 2).toDouble(), ttY)
        towerPath.lineTo((baseX + bottomW / 2).toDouble(), tbY)
        towerPath.lineTo((baseX - bottomW / 2).toDouble(), tbY)
        towerPath.closePath()
        g2.paint = GradientPaint(
            (baseX - bottomW / 2f), 0f, Color(248, 250, 255),
            (baseX + bottomW / 2f), 0f, Color(200, 212, 240)
        )
        g2.fill(towerPath)
        g2.color = Color(80, 100, 160, 80); g2.stroke = BasicStroke(1f); g2.draw(towerPath)

        val bands = 3
        for (i in 0 until bands) {
            val t = (i + 1f) / (bands + 1)
            val y = (ttY + (tbY - ttY) * t).toInt()
            val widthAtY = lerpInt(topW, bottomW, t)
            val pad = 3
            val l = baseX - widthAtY / 2 + pad
            val r = baseX + widthAtY / 2 - pad
            val bh = (towerH * 0.045).toInt()
            val color = if (i == 1) Color(220, 70, 80) else Color(60, 110, 230)
            g2.paint = GradientPaint(l.toFloat(), (y - bh / 2).toFloat(), color,
                l.toFloat(), (y + bh / 2).toFloat(), color.darker())
            g2.fillRoundRect(l, y - bh / 2, r - l, bh, 5, 5)
        }

        val doorW = (bottomW * 0.16).toInt()
        val doorH = (plinth2H * 0.9).toInt()
        g2.color = Color(28, 38, 80)
        g2.fillRoundRect(baseX - doorW / 2, plinth2Y - doorH + (plinth2H * 0.4).toInt(), doorW, doorH, 4, 4)

        val galleryW = (topW * 1.7).toInt()
        val galleryH = (towerH * 0.045).toInt()
        val galleryY = towerTopY - galleryH
        g2.color = Color(40, 52, 96)
        g2.fillRoundRect(baseX - galleryW / 2, galleryY, galleryW, galleryH, 4, 4)
        g2.color = Color(180, 195, 230)
        g2.fillRect(baseX - galleryW / 2 + 2, galleryY - 1, galleryW - 4, 2)
        for (j in 0..4) {
            val px = baseX - galleryW / 2 + 6 + j * (galleryW - 12) / 4
            g2.color = Color(60, 80, 130)
            g2.fillRect(px, galleryY - 4, 1, 4)
        }

        val lantW   = (topW * 1.45).toInt()
        val lantH   = (towerH * 0.16).toInt()
        val lantTopY = galleryY - lantH
        g2.paint = GradientPaint(
            (baseX - lantW / 2f), lantTopY.toFloat(), Color(20, 30, 70),
            (baseX + lantW / 2f), lantTopY.toFloat() + lantH, Color(12, 20, 50)
        )
        g2.fillRoundRect(baseX - lantW / 2, lantTopY, lantW, lantH, 6, 6)
        g2.color = Color(120, 140, 190, 150)
        g2.drawRoundRect(baseX - lantW / 2, lantTopY, lantW - 1, lantH - 1, 6, 6)
        for (k in 1 until 4) {
            val x = baseX - lantW / 2 + (lantW * k / 4)
            g2.color = Color(90, 110, 170, 110)
            g2.drawLine(x, lantTopY + 2, x, lantTopY + lantH - 2)
        }

        val roofW  = (lantW * 1.18).toInt()
        val roofH  = (lantH * 1.05).toInt()
        val apexX  = baseX.toDouble(); val apexY = (lantTopY - roofH).toDouble()
        val eaveL  = baseX - roofW / 2.0 - 2.0; val eaveR = baseX + roofW / 2.0 + 2.0
        val eaveY  = lantTopY + 2.0
        roofPath.reset()
        roofPath.moveTo(apexX, apexY)
        roofPath.lineTo(eaveL, eaveY)
        roofPath.quadTo(baseX.toDouble(), eaveY + 3.0, eaveR, eaveY)
        roofPath.closePath()
        g2.paint = GradientPaint((baseX - roofW / 2f), lantTopY.toFloat(), Color(190, 50, 60),
            (baseX + roofW / 2f), lantTopY.toFloat(), Color(140, 30, 40))
        g2.fill(roofPath)
        g2.color = Color(255, 255, 255, 40)
        highlightPath.reset()
        highlightPath.moveTo((baseX - roofW * 0.35), (lantTopY - 2).toDouble())
        highlightPath.quadTo((baseX - roofW * 0.18), (lantTopY - roofH * 0.7).toDouble(),
            (baseX - 1).toDouble(), (lantTopY - roofH + 4).toDouble())
        g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.draw(highlightPath)

        val ballR = (towerH * 0.018).toInt().coerceAtLeast(3)
        val ballY = lantTopY - roofH - ballR * 2 - 2
        g2.color = Color(240, 245, 255)
        g2.fillOval(baseX - ballR, ballY, ballR * 2, ballR * 2)
        g2.color = Color(180, 195, 230)
        g2.stroke = BasicStroke(1.2f)
        g2.drawLine(baseX, ballY + ballR * 2, baseX, lantTopY - roofH)
    }

    private fun drawBulbGlow(g2: Graphics2D, cx: Int, cy: Int) {
        val now  = System.currentTimeMillis()
        val warm = heroState == HeroState.CONNECTING

        val onAlpha = when (heroState) {
            HeroState.ON         -> 1f
            HeroState.CONNECTING -> (0.65f + sin((now - stateChangedAt) / 180.0).toFloat() * 0.3f).coerceIn(0f, 1f)
            HeroState.OFF        -> (0.10f + sin(now / 2400.0).toFloat() * 0.06f).coerceIn(0.04f, 0.18f)
        }

        // Float pulse — no toInt() so radius is smooth, no discrete pixel jumps
        val pulse = when (heroState) {
            HeroState.ON         -> 1.0f + sin(now / 900.0).toFloat() * 0.07f
            HeroState.CONNECTING -> 1.0f + sin((now - stateChangedAt) / 180.0).toFloat() * 0.11f
            HeroState.OFF        -> 1.0f + sin(now / 2400.0).toFloat() * 0.04f
        }
        val bulbR  = (7f * pulse).coerceAtLeast(4f)   // Float — drawn via Ellipse2D.Float
        val haloR  = bulbR * 4f

        // Core/halo color: CONNECTING = always warm; ON = cool with occasional warm drift (bulbColorT)
        val core: Color
        val halo: Color
        if (warm) {
            core = Color(255, 230, 150)
            halo = Color(255, 200, 80)
        } else {
            val t = bulbColorT
            core = Color(lerpInt(200, 255, t), lerpInt(240, 210, t), lerpInt(255, 120, t))
            halo = Color(lerpInt(120, 255, t), lerpInt(200, 185, t), lerpInt(255,  60, t))
        }

        val old = g2.paint
        g2.paint = RadialGradientPaint(
            cx.toFloat(), cy.toFloat(), haloR,
            floatArrayOf(0f, 1f),
            arrayOf(
                Color(halo.red, halo.green, halo.blue, (200 * onAlpha).toInt().coerceIn(0, 255)),
                Color(halo.red, halo.green, halo.blue, 0)
            )
        )
        g2.fillOval((cx - haloR).toInt(), (cy - haloR).toInt(), (haloR * 2).toInt(), (haloR * 2).toInt())
        g2.paint = old

        g2.color = core
        g2.fill(Ellipse2D.Float(cx - bulbR, cy - bulbR, bulbR * 2f, bulbR * 2f))
        g2.color = Color(255, 255, 255, 220)
        val specR = (bulbR * 0.35f).toInt().coerceAtLeast(1)
        g2.fillOval(cx - specR - 1, cy - specR - 1, 3, 3)
    }

    /**
     * Faint vertical reflection of the lighthouse tower in the water.
     */
    private fun drawLighthouseReflection(g2: Graphics2D, baseX: Int, horizonY: Int, towerH: Int) {
        val reflAlpha = when (heroState) {
            HeroState.ON -> 0.18f
            HeroState.CONNECTING -> 0.12f
            HeroState.OFF -> 0.08f
        }
        val old = g2.composite
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, reflAlpha)

        val reflH    = (towerH * 0.40).toInt()
        val topW     = (towerH * 0.20).toInt()
        val bottomW  = (towerH * 0.34).toInt()
        val reflTopY = horizonY + 4
        val reflBotY = reflTopY + reflH

        reflPath.reset()
        reflPath.moveTo((baseX - topW / 2.0), reflTopY.toDouble())
        reflPath.lineTo((baseX + topW / 2.0), reflTopY.toDouble())
        reflPath.lineTo((baseX + bottomW / 2.0), reflBotY.toDouble())
        reflPath.lineTo((baseX - bottomW / 2.0), reflBotY.toDouble())
        reflPath.closePath()
        g2.paint = GradientPaint(
            baseX.toFloat(), reflTopY.toFloat(), Color(220, 230, 255),
            baseX.toFloat(), reflBotY.toFloat(), Color(220, 230, 255, 0)
        )
        g2.fill(reflPath)

        if (heroState != HeroState.OFF) {
            val halo = if (heroState == HeroState.CONNECTING) Color(255, 210, 100) else Color(120, 200, 255)
            val gr = (towerH * 0.06).toInt()
            g2.paint = RadialGradientPaint(
                baseX.toFloat(), (reflTopY + 2).toFloat(), gr.toFloat(),
                floatArrayOf(0f, 1f),
                arrayOf(Color(halo.red, halo.green, halo.blue, 120), Color(halo.red, halo.green, halo.blue, 0))
            )
            g2.fillOval(baseX - gr, reflTopY - gr / 2, gr * 2, gr * 2)
        }

        g2.composite = old
    }

    /**
     * Dark foreground cliff — viewer stands on a rocky outcrop, sea and lighthouse
     * are in the midground beyond. Sides rise high, center dips low so the lighthouse
     * stays fully visible. Gives the scene depth and correct perspective framing.
     */
    private fun drawForegroundCliff(g2: Graphics2D, w: Int, h: Int) {
        // Top-edge silhouette control points: (xFraction, yFraction of h)
        // Sides tall (~0.70–0.76h), dip to ~0.93h in the centre.
        val pts = arrayOf(
            0.00f to 0.76f,
            0.05f to 0.71f,
            0.10f to 0.74f,
            0.16f to 0.69f,
            0.22f to 0.75f,
            0.29f to 0.82f,
            0.37f to 0.88f,
            0.46f to 0.935f,
            0.54f to 0.935f,
            0.63f to 0.88f,
            0.71f to 0.82f,
            0.78f to 0.75f,
            0.84f to 0.70f,
            0.90f to 0.74f,
            0.95f to 0.71f,
            1.00f to 0.76f
        )

        cliffPath.reset()
        cliffPath.moveTo(0f, h.toFloat())
        cliffPath.lineTo(0f, pts[0].second * h)
        for (p in pts) cliffPath.lineTo(p.first * w, p.second * h)
        cliffPath.lineTo(w.toFloat(), h.toFloat())
        cliffPath.closePath()

        // Main fill — near-black with a cool blue undertone, darker at the bottom
        g2.paint = GradientPaint(
            0f, h * 0.70f, Color(16, 21, 46),
            0f, h.toFloat(),  Color(3, 4, 12)
        )
        g2.fill(cliffPath)

        // Subtle side rim — the far edges catch faint skylight
        val sideGlowW = w * 0.22f
        val old = g2.composite
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f)
        g2.paint = GradientPaint(0f, 0f, Color(80, 110, 170), sideGlowW, 0f, Color(80, 110, 170, 0))
        g2.fill(cliffPath)
        g2.paint = GradientPaint(w.toFloat(), 0f, Color(80, 110, 170), w - sideGlowW, 0f, Color(80, 110, 170, 0))
        g2.fill(cliffPath)
        g2.composite = old

        // Rim-light stroke along the top edge — mimics moonlight catching the ridge
        val rimStroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val rimPath = GeneralPath()
        rimPath.moveTo(0f, pts[0].second * h)
        for (p in pts) rimPath.lineTo(p.first * w, p.second * h)

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
        g2.color = Color(140, 170, 220)
        g2.stroke = rimStroke
        g2.draw(rimPath)

        // Very faint haze just above the cliff edge — beds it into the scene
        val hazeH = (h * 0.035f).toInt().coerceAtLeast(6)
        val hazeY = (h * 0.665f).toInt()
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f)
        g2.paint = GradientPaint(
            0f, hazeY.toFloat(),              Color(10, 15, 40, 0),
            0f, (hazeY + hazeH).toFloat(),    Color(10, 15, 40, 180)
        )
        g2.fillRect(0, hazeY, w, hazeH + (h * 0.05f).toInt())

        g2.composite = old
    }

    /**
     * Gradient fade at top and bottom edges so the panel blends into the surrounding background.
     */
    private fun drawEdgeFade(g2: Graphics2D, w: Int, h: Int) {
        val bgTop = Color(15, 21, 53)
        val bgBot = Color(8, 12, 34)
        val fadeH = 32
        g2.paint = GradientPaint(0f, 0f, bgTop, 0f, fadeH.toFloat(), Color(bgTop.red, bgTop.green, bgTop.blue, 0))
        g2.fillRect(0, 0, w, fadeH)
        g2.paint = GradientPaint(0f, (h - fadeH).toFloat(), Color(bgBot.red, bgBot.green, bgBot.blue, 0),
            0f, h.toFloat(), bgBot)
        g2.fillRect(0, h - fadeH, w, fadeH)
    }

    private fun lerpInt(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    private data class Star(
        val x: Double, val y: Double, val size: Int,
        val baseAlpha: Double, val phase: Long,
        val twinklePeriod1: Double, val twinklePeriod2: Double
    )

    private fun generateStars(n: Int): List<Star> {
        val r = java.util.Random(42)
        return (0 until n).map {
            val brightness = r.nextDouble()
            Star(
                x = r.nextDouble(),
                y = r.nextDouble(),
                size = if (brightness > 0.92) 3 else if (brightness > 0.7) 2 else 1,
                baseAlpha = 0.35 + brightness * 0.65,
                phase = (r.nextDouble() * 3000).toLong(),
                twinklePeriod1 = 500.0 + r.nextDouble() * 1500.0,
                twinklePeriod2 = 800.0 + r.nextDouble() * 2200.0
            )
        }
    }

    private class ShootingStar(
        var x: Double, var y: Double,
        val dx: Double, val dy: Double,
        var life: Int,
        val maxLife: Int
    ) {
        val tailDx: Double get() = dx * 20
        val tailDy: Double get() = dy * 20

        fun tick(): Boolean { x += dx; y += dy; life++; return life >= maxLife }

        fun alpha(): Double {
            val t = life.toDouble() / maxLife
            return if (t < 0.15) t / 0.15 else (1.0 - (t - 0.15) / 0.85).coerceIn(0.0, 1.0)
        }

        companion object {
            fun spawn(w: Int, h: Int): ShootingStar {
                val r = java.util.Random()
                val goingRight = r.nextBoolean()
                val startX = if (goingRight)
                    r.nextDouble() * w * 0.35
                else
                    w * 0.65 + r.nextDouble() * w * 0.35
                val startY = r.nextDouble() * h * 0.32
                val angle  = Math.toRadians(15.0 + r.nextDouble() * 28)
                val speed  = 5.5 + r.nextDouble() * 4.5
                val dx = cos(angle) * speed * if (goingRight) 1.0 else -1.0
                val dy = sin(angle) * speed
                return ShootingStar(x = startX, y = startY, dx = dx, dy = dy, life = 0, maxLife = 38 + r.nextInt(22))
            }
        }
    }

    companion object {
        private const val BLOB_COUNT = 8
        private const val WISP_COUNT = 8

        // Atmosphere blob layout — pre-computed, never allocated at runtime
        private val BLOB_BX   = doubleArrayOf( 0.000, -0.070,  0.085,  0.000, -0.125,  0.115, -0.045,  0.060)
        private val BLOB_BY   = doubleArrayOf( 0.000, -0.065, -0.050, -0.130,  0.010,  0.020,  0.080,  0.095)
        private val BLOB_R    = floatArrayOf(  0.26f,  0.19f,  0.18f,  0.14f,  0.13f,  0.13f,  0.15f,  0.14f)
        private val BLOB_PEAK = intArrayOf(      72,     52,     50,     40,     35,     34,     42,     38)

        // Cloak wisp layout — pre-computed, never allocated at runtime
        private val WISP_XF = doubleArrayOf(0.08, 0.36, 0.64, 0.90, 0.22, 0.74, 0.50, 0.50)
        private val WISP_YF = doubleArrayOf(0.80, 0.87, 0.83, 0.79, 0.92, 0.89, 0.96, 0.75)
        private val WISP_RF = doubleArrayOf(0.28, 0.33, 0.30, 0.24, 0.38, 0.34, 0.42, 0.26)
        private val WISP_ST = doubleArrayOf(0.42, 0.50, 0.44, 0.38, 0.55, 0.48, 0.58, 0.30)

        // Beam layers — replaces per-frame listOf(... to ...) allocation
        private val BEAM_LAYER_SPREADS = doubleArrayOf(1.9, 1.45, 1.1)
        private val BEAM_LAYER_ALPHAS  = floatArrayOf(0.10f, 0.09f, 0.08f)

        // Cached Color constants — eliminates per-frame Color() allocation in drawStars / drawCloak
        private val STAR_COLOR      = Color(245, 250, 255)
        private val STAR_HALO_COLOR = Color(220, 230, 255)
        private val WISP_COLOR      = Color(5, 10, 32)

        // Cached strokes — BasicStroke is immutable, safe to share across frames
        private val WAVE_STROKE      = BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        private val SHOOTING_STROKE  = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        private val STAR_CROSS_STROKE = BasicStroke(0.9f)
        private val RIPPLE_STROKE    = BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    }
}
