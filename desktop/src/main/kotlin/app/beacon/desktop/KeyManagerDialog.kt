package app.beacon.desktop

import app.beacon.core.model.ProxyProfile
import app.beacon.core.parser.ProfileInputParser
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import app.beacon.desktop.BeaconTheme as T

class KeyManagerDialog(
    owner: JFrame,
    private val parser: ProfileInputParser,
    private var profiles: List<ProxyProfile>,
    private var activeId: String?,
    private val onChange: (List<ProxyProfile>, String?) -> Unit
) : JDialog(owner, L.t("Ключи", "Keys"), true) {

    private val model = DefaultListModel<ProxyProfile>()
    private val list = JList(model)
    private val keyInput = JTextArea()
    private val emptyHint = JLabel(
        L.t(
            "<html><div style='text-align:center;color:#7B8EAD'>" +
            "Здесь будут сохранённые ключи.<br>Вставь vless://... ссылку выше и нажми «Сохранить».</div></html>",
            "<html><div style='text-align:center;color:#7B8EAD'>" +
            "Saved keys will appear here.<br>Paste vless://... link above and click \"Save\".</div></html>"
        ),
        SwingConstants.CENTER
    )

    init {
        contentPane = buildContent()
        size = Dimension(580, 580)
        minimumSize = Dimension(520, 500)
        setLocationRelativeTo(owner)
        rebuildList()
    }

    private fun buildContent(): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                g.color = T.BG_BOT
                g.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque = true
            border = EmptyBorder(22, 24, 22, 24)
            add(header(), BorderLayout.NORTH)
            add(center(), BorderLayout.CENTER)
            add(footer(), BorderLayout.SOUTH)
        }
    }

    private fun header(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel(L.t("Ключи", "Keys")).apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 22f); alignmentX = 0f })
        add(Box.createVerticalStrut(4))
        add(JLabel(L.t("вставь vless:// Reality ссылку — она сразу станет активной", "paste vless:// Reality link — it will immediately become active")).apply {
            foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 12f); alignmentX = 0f
        })
        add(Box.createVerticalStrut(16))
    }

    private fun center(): JPanel = JPanel(BorderLayout(0, 14)).apply {
        isOpaque = false
        add(inputCard(), BorderLayout.NORTH)
        add(listCard(), BorderLayout.CENTER)
    }

    private fun inputCard(): JPanel = card().apply {
        layout = BorderLayout(0, 12)
        keyInput.apply {
            lineWrap = true; wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            background = T.BG_INPUT; foreground = T.TEXT; caretColor = T.ACCENT_LIGHT
            border = EmptyBorder(10, 12, 10, 12)
        }
        val scroll = JScrollPane(keyInput).apply {
            preferredSize = Dimension(0, 96)
            border = BorderFactory.createLineBorder(T.BORDER)
            viewport.background = T.BG_INPUT
        }
        keyInput.addFocusListener(object : java.awt.event.FocusListener {
            override fun focusGained(e: java.awt.event.FocusEvent) {
                scroll.border = BorderFactory.createLineBorder(T.ACCENT, 1)
            }
            override fun focusLost(e: java.awt.event.FocusEvent) {
                scroll.border = BorderFactory.createLineBorder(T.BORDER)
            }
        })
        add(scroll, BorderLayout.CENTER)

        val btnRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(T.accentButton(L.t("Сохранить", "Save")).apply {
                addActionListener { save() }
                preferredSize = Dimension(148, 38)
                maximumSize  = Dimension(148, 38)
                minimumSize  = Dimension(148, 38)
                font = font.deriveFont(Font.BOLD, 13f)
            })
        }
        add(btnRow, BorderLayout.SOUTH)
    }

    private fun listCard(): JPanel = card().apply {
        layout = BorderLayout(0, 10)

        list.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = 66
            background = T.CARD_SOLID
            border = EmptyBorder(4, 4, 4, 4)
            cellRenderer = javax.swing.ListCellRenderer<ProxyProfile> { l, value, _, isSelected, _ ->
                val r = Row(value, isSelected, value.id == activeId)
                r.preferredSize = Dimension(l.width, 66)
                r as Component
            }
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val idx = locationToIndex(e.point)
                    if (idx < 0 || idx >= model.size) return
                    val p: ProxyProfile = model.getElementAt(idx)

                    // hit-test delete cross (right side ~30px from edge)
                    val cellBounds = getCellBounds(idx, idx) ?: return
                    val xInCell = e.x - cellBounds.x
                    if (xInCell > cellBounds.width - 44) {
                        confirmDelete(p)
                        return
                    }
                    // otherwise: make active
                    if (p.id != activeId) {
                        activeId = p.id
                        rebuildList()
                        onChange(profiles, activeId)
                    }
                }
            })
        }
        add(JScrollPane(list).apply {
            border = BorderFactory.createEmptyBorder()
            viewport.background = T.CARD_SOLID
            isOpaque = false
            viewport.isOpaque = false
        }, BorderLayout.CENTER)

        emptyHint.isVisible = false
        add(emptyHint, BorderLayout.SOUTH)
    }

    private fun footer(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = EmptyBorder(14, 0, 0, 0)
        add(Box.createHorizontalGlue())
        add(T.accentButton(L.t("Готово", "Done")).apply {
            preferredSize = Dimension(160, 40)
            maximumSize = Dimension(160, 40)
            addActionListener { dispose() }
        })
    }

    private fun save() {
        val profile = runCatching { parser.parse(keyInput.text) }
            .getOrElse {
                JOptionPane.showMessageDialog(this, it.message ?: L.t("ключ не сохранён", "key not saved"),
                    "Beacon", JOptionPane.ERROR_MESSAGE)
                return
            }
        profiles = profiles.filterNot { it.id == profile.id } + profile
        activeId = profile.id
        keyInput.text = ""
        rebuildList()
        onChange(profiles, activeId)
    }

    private fun confirmDelete(p: ProxyProfile) {
        val res = JOptionPane.showConfirmDialog(this, L.t("Удалить ключ «${p.name}»?", "Delete key \"${p.name}\"?"),
            "Beacon", JOptionPane.YES_NO_OPTION)
        if (res != JOptionPane.YES_OPTION) return
        profiles = profiles.filterNot { it.id == p.id }
        if (activeId == p.id) activeId = profiles.firstOrNull()?.id
        rebuildList()
        onChange(profiles, activeId)
    }

    private fun rebuildList() {
        model.clear()
        profiles.forEach { model.addElement(it) }
        list.repaint()
        // toggle empty hint visibility
        emptyHint.isVisible = profiles.isEmpty()
    }

    private fun card(): JPanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = T.CARD
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 14f, 14f))
            g2.color = T.BORDER_SOFT
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 14f, 14f))
        }
    }.apply {
        isOpaque = false
        border = EmptyBorder(16, 18, 16, 18)
    }

    /** Single row in the keys list. */
    private class Row(profile: ProxyProfile, val isSelected: Boolean, val isActive: Boolean) : JPanel() {
        init {
            isOpaque = false
            layout = BorderLayout()
            border = EmptyBorder(10, 18, 10, 18)

            val left = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                val dot = JLabel("●").apply {
                    foreground = if (isActive) T.SUCCESS else T.MUTED
                    font = font.deriveFont(13f)
                }
                add(dot)
                add(Box.createHorizontalStrut(14))
                val text = JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JLabel(profile.name).apply {
                        foreground = if (isActive) T.ACCENT_LIGHT else T.TEXT
                        font = font.deriveFont(if (isActive) Font.BOLD else Font.PLAIN, 14f)
                        alignmentX = 0f
                    })
                    add(Box.createVerticalStrut(2))
                    add(JLabel("${profile.host}:${profile.port}").apply {
                        foreground = T.MUTED
                        font = font.deriveFont(Font.PLAIN, 11f)
                        alignmentX = 0f
                    })
                }
                add(text)
            }
            add(left, BorderLayout.CENTER)

            val right = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                if (isActive) {
                    add(JLabel(L.t("активный", "active")).apply {
                        foreground = T.SUCCESS
                        font = font.deriveFont(Font.BOLD, 10f)
                        border = EmptyBorder(0, 8, 0, 12)
                    })
                }
                add(Box.createHorizontalStrut(4))
                add(JLabel("✕").apply {
                    foreground = T.DANGER
                    font = font.deriveFont(Font.BOLD, 14f)
                    border = EmptyBorder(2, 6, 2, 6)
                })
            }
            add(right, BorderLayout.EAST)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = when {
                isActive -> Color(40, 56, 120, 220)
                isSelected -> Color(35, 48, 92, 180)
                else -> T.BG_INPUT
            }
            g2.fillRoundRect(4, 4, width - 8, height - 8, 12, 12)
            g2.color = if (isActive) T.ACCENT_LIGHT else T.BORDER_SOFT
            g2.stroke = BasicStroke(1f)
            g2.drawRoundRect(4, 4, width - 9, height - 9, 12, 12)
        }
    }
}
