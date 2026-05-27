package app.beacon.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

object BeaconTheme {

    val BG_TOP = Color(15, 21, 53)
    val BG_BOT = Color(8, 12, 34)
    val CARD = Color(22, 32, 70, 235)
    val CARD_SOLID = Color(22, 32, 70)
    val BG_INPUT = Color(15, 22, 54)
    val BG_INPUT_HOVER = Color(23, 34, 76)
    val BORDER = Color(42, 58, 110)
    val BORDER_SOFT = Color(35, 48, 92)
    val TEXT = Color(225, 232, 250)
    val TEXT_DIM = Color(180, 195, 230)
    val MUTED = Color(120, 140, 190)
    val ACCENT = Color(60, 110, 230)
    val ACCENT_HOVER = Color(80, 130, 245)
    val ACCENT_LIGHT = Color(130, 180, 255)
    val SUCCESS = Color(52, 211, 153)
    val WARN = Color(251, 191, 36)
    val DANGER = Color(248, 113, 113)

    fun accentButton(text: String) = object : JButton(text) {
        var hoverProgress = 0f
        val animator = HoverAnimator(this) { hoverProgress = it }
        init {
            isOpaque = false
            isContentAreaFilled = false
            isFocusPainted = false
            foreground = Color.WHITE
            font = font.deriveFont(Font.BOLD, 13f)
            border = BorderFactory.createEmptyBorder(8, 20, 8, 20)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { animator.setTarget(1f) }
                override fun mouseExited(e: MouseEvent) { animator.setTarget(0f) }
            })
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val bgStart = ACCENT
            val bgEnd = ACCENT_HOVER
            val r = bgStart.red + ((bgEnd.red - bgStart.red) * hoverProgress).toInt()
            val gDec = bgStart.green + ((bgEnd.green - bgStart.green) * hoverProgress).toInt()
            val b = bgStart.blue + ((bgEnd.blue - bgStart.blue) * hoverProgress).toInt()
            g2.color = Color(r, gDec, b)
            val arc = 18
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            super.paintComponent(g)
        }
    }

    fun ghostButton(text: String) = object : JButton(text) {
        var hoverProgress = 0f
        val animator = HoverAnimator(this) { hoverProgress = it }
        init {
            isOpaque = false
            isContentAreaFilled = false
            isFocusPainted = false
            foreground = TEXT_DIM
            font = font.deriveFont(Font.BOLD, 12f)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT, 1, true),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)
            )
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { animator.setTarget(1f) }
                override fun mouseExited(e: MouseEvent) { animator.setTarget(0f) }
            })
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Interpolate background color
            val bgStart = BG_INPUT
            val bgEnd = BG_INPUT_HOVER
            val r = bgStart.red + ((bgEnd.red - bgStart.red) * hoverProgress).toInt()
            val gDec = bgStart.green + ((bgEnd.green - bgStart.green) * hoverProgress).toInt()
            val b = bgStart.blue + ((bgEnd.blue - bgStart.blue) * hoverProgress).toInt()
            g2.color = Color(r, gDec, b)
            val arc = 16
            g2.fillRoundRect(0, 0, width, height, arc, arc)

            // Interpolate border color
            val borderStart = BORDER_SOFT
            val borderEnd = BORDER
            val br = borderStart.red + ((borderEnd.red - borderStart.red) * hoverProgress).toInt()
            val bgBorder = borderStart.green + ((borderEnd.green - borderStart.green) * hoverProgress).toInt()
            val bb = borderStart.blue + ((borderEnd.blue - borderStart.blue) * hoverProgress).toInt()
            g2.color = Color(br, bgBorder, bb)
            g2.stroke = BasicStroke(1f)
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

            // Interpolate text color
            val textStart = TEXT_DIM
            val textEnd = TEXT
            val tr = textStart.red + ((textEnd.red - textStart.red) * hoverProgress).toInt()
            val tg = textStart.green + ((textEnd.green - textStart.green) * hoverProgress).toInt()
            val tb = textStart.blue + ((textEnd.blue - textStart.blue) * hoverProgress).toInt()
            foreground = Color(tr, tg, tb)

            super.paintComponent(g)
        }
    }

    fun iconButton(glyph: String, tip: String) = JButton(glyph).apply {
        isOpaque = false
        isContentAreaFilled = false
        foreground = TEXT_DIM
        font = font.deriveFont(Font.PLAIN, 18f)
        isFocusPainted = false
        border = EmptyBorder(8, 10, 8, 10)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = tip
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { foreground = Color.WHITE; repaint() }
            override fun mouseExited(e: MouseEvent) { foreground = TEXT_DIM; repaint() }
        })
    }

    fun softFill(g2: Graphics2D, w: Int, h: Int, top: Color, bottom: Color, radius: Int) {
        g2.paint = GradientPaint(0f, 0f, top, 0f, h.toFloat(), bottom)
        g2.fillRoundRect(0, 0, w, h, radius, radius)
    }

    fun card(radius: Float = 14f): JPanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = CARD
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius, radius))
            g2.color = BORDER_SOFT
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, radius, radius))
        }
    }.apply {
        isOpaque = false
        border = EmptyBorder(14, 16, 14, 16)
    }

    fun pillTag(text: String, color: Color) = JLabel(text).apply {
        isOpaque = false
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = color
        horizontalAlignment = SwingConstants.CENTER
        border = EmptyBorder(4, 10, 4, 10)
    }

    fun JComponent.preferred(w: Int, h: Int) = apply {
        preferredSize = Dimension(w, h)
        minimumSize = Dimension(w, h)
        maximumSize = Dimension(w, h)
    }
}
enum class IconType { GLOBE, KEY, GEAR }

class VectorIconButton(val type: IconType, tip: String) : JButton() {
    private var hoverProgress = 0f
    private val animator = HoverAnimator(this) { hoverProgress = it }

    init {
        isOpaque = false
        isContentAreaFilled = false
        isFocusPainted = false
        border = EmptyBorder(6, 6, 6, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = tip
        preferredSize = Dimension(38, 38)
        minimumSize = Dimension(38, 38)
        maximumSize = Dimension(38, 38)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) { animator.setTarget(1f) }
            override fun mouseExited(e: java.awt.event.MouseEvent) { animator.setTarget(0f) }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        // Circle hover background
        if (hoverProgress > 0f) {
            val bgAlpha = (16 * hoverProgress).toInt().coerceIn(0, 255)
            val borderAlpha = (30 * hoverProgress).toInt().coerceIn(0, 255)
            g2.color = Color(255, 255, 255, bgAlpha)
            g2.fillOval(2, 2, width - 4, height - 4)
            g2.color = Color(255, 255, 255, borderAlpha)
            g2.stroke = BasicStroke(1f)
            g2.drawOval(2, 2, width - 5, height - 5)
        }

        // Draw icon
        val normalColor = BeaconTheme.TEXT_DIM
        val hoverColor = Color.WHITE
        val r = normalColor.red + ((hoverColor.red - normalColor.red) * hoverProgress).toInt()
        val gDec = normalColor.green + ((hoverColor.green - normalColor.green) * hoverProgress).toInt()
        val b = normalColor.blue + ((hoverColor.blue - normalColor.blue) * hoverProgress).toInt()
        g2.color = Color(r, gDec, b)
        g2.stroke = BasicStroke(1.8f)

        val cx = width / 2
        val cy = height / 2

        when (type) {
            IconType.GLOBE -> {
                // Outer circle
                g2.drawOval(cx - 9, cy - 9, 18, 18)
                // Equator
                g2.drawLine(cx - 9, cy, cx + 9, cy)
                // Meridians
                g2.drawOval(cx - 4, cy - 9, 8, 18)
            }
            IconType.KEY -> {
                // Head
                g2.drawOval(cx - 8, cy - 6, 9, 9)
                // Stem
                g2.drawLine(cx + 1, cy + 1, cx + 8, cy + 8)
                // Teeth
                g2.drawLine(cx + 5, cy + 5, cx + 3, cy + 7)
                g2.drawLine(cx + 8, cy + 8, cx + 6, cy + 10)
            }
            IconType.GEAR -> {
                // Center hole
                g2.drawOval(cx - 3, cy - 3, 6, 6)
                // Ring
                g2.drawOval(cx - 6, cy - 6, 12, 12)
                // Teeth
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4
                    val x1 = cx + (6 * Math.cos(angle))
                    val y1 = cy + (6 * Math.sin(angle))
                    val x2 = cx + (9 * Math.cos(angle))
                    val y2 = cy + (9 * Math.sin(angle))
                    g2.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
                }
            }
        }
    }
}

class HoverAnimator(
    private val component: javax.swing.JComponent,
    private val durationMs: Int = 150,
    private val onUpdate: (Float) -> Unit
) {
    private var timer: javax.swing.Timer? = null
    var progress = 0f
        private set
    private var target = 0f
    private var startTime = 0L

    fun setTarget(newTarget: Float) {
        if (target == newTarget) return
        target = newTarget
        startTime = System.currentTimeMillis() - ((if (target == 1f) progress else 1f - progress) * durationMs).toLong()
        if (timer == null) {
            timer = javax.swing.Timer(16) {
                val elapsed = System.currentTimeMillis() - startTime
                val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                progress = if (target == 1f) t else 1f - t
                onUpdate(progress)
                component.repaint()
                if (t >= 1f) {
                    timer?.stop()
                    timer = null
                }
            }
            timer?.start()
        }
    }
}
