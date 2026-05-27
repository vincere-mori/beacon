package app.beacon.desktop

import app.beacon.core.geo.CountryDetector
import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import app.beacon.core.net.LatencyProbe
import app.beacon.core.net.SubscriptionFetcher
import app.beacon.core.parser.SubscriptionParser
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
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import app.beacon.desktop.BeaconTheme as T

class SubscriptionDialog(
    owner: JFrame,
    private var subscriptions: List<Subscription>,
    private var activeId: String?,
    private val onChange: (List<Subscription>, String?) -> Unit
) : JDialog(owner, L.t("Подписки", "Subscriptions"), true) {

    private val fetcher = SubscriptionFetcher()
    private val subParser = SubscriptionParser()
    private val probe = LatencyProbe()
    private val pingPool = Executors.newFixedThreadPool(8) { r -> Thread(r).apply { isDaemon = true } }

    /** profile id -> measured latency (null = unreachable). */
    private val pingResults = HashMap<String, Long?>()
    private val pingingIds = HashSet<String>()

    private val urlInput = JTextField()
    private val listPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val emptyHint = JLabel(
        L.t(
            "<html><div style='text-align:center;color:#7B8EAD'>" +
            "Пока нет подписок.<br>Вставь ссылку подписки (http/https) выше и нажми «Добавить».</div></html>",
            "<html><div style='text-align:center;color:#7B8EAD'>" +
            "No subscriptions yet.<br>Paste a subscription link (http/https) above and click \"Add\".</div></html>"
        ),
        SwingConstants.CENTER
    )
    private var busy = false

    init {
        contentPane = buildContent()
        size = Dimension(620, 660)
        minimumSize = Dimension(560, 560)
        setLocationRelativeTo(owner)
        rebuild()
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) { pingPool.shutdownNow() }
        })
    }

    private fun buildContent(): JPanel = object : JPanel(BorderLayout()) {
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

    private fun header(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel(L.t("Подписки", "Subscriptions")).apply {
            foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 22f); alignmentX = 0f
        })
        add(Box.createVerticalStrut(4))
        add(JLabel(L.t("ссылка подписки разворачивается в список серверов — выбери нужный", "subscription link expands to server list — select the desired one")).apply {
            foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 12f); alignmentX = 0f
        })
        add(Box.createVerticalStrut(16))
    }

    private fun center(): JPanel = JPanel(BorderLayout(0, 14)).apply {
        isOpaque = false
        add(inputCard(), BorderLayout.NORTH)
        add(listCard(), BorderLayout.CENTER)
    }
    private fun inputCard(): JPanel = T.card().apply {
        layout = BorderLayout(10, 0)
        maximumSize = Dimension(Int.MAX_VALUE, 70)
        urlInput.apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            background = T.BG_INPUT; foreground = T.TEXT; caretColor = T.ACCENT_LIGHT
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(T.BORDER), EmptyBorder(8, 10, 8, 10)
            )
            addFocusListener(object : java.awt.event.FocusListener {
                override fun focusGained(e: java.awt.event.FocusEvent) {
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(T.ACCENT), EmptyBorder(8, 10, 8, 10)
                    )
                }
                override fun focusLost(e: java.awt.event.FocusEvent) {
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(T.BORDER), EmptyBorder(8, 10, 8, 10)
                    )
                }
            })
            addActionListener { addSubscription() }
        }
        add(urlInput, BorderLayout.CENTER)
        add(T.accentButton(L.t("Добавить", "Add")).apply {
            preferredSize = Dimension(130, 38)
            addActionListener { addSubscription() }
        }, BorderLayout.EAST)
    }

    private fun listCard(): JPanel = T.card().apply {
        layout = BorderLayout(0, 0)
        add(JScrollPane(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(listPanel, BorderLayout.NORTH)
            }
        ).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)
    }

    private fun footer(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = EmptyBorder(14, 0, 0, 0)
        add(Box.createHorizontalGlue())
        add(T.accentButton(L.t("Готово", "Done")).apply {
            preferredSize = Dimension(160, 40)
            addActionListener { dispose() }
        })
    }

    // ── data actions ─────────────────────────────────────────────────────────

    private fun addSubscription() {
        val url = urlInput.text.trim()
        if (url.isBlank() || busy) return
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            error(L.t("ссылка подписки должна начинаться с http:// или https://", "subscription link must start with http:// or https://"))
            return
        }
        setBusy(true)
        Thread {
            val result = runCatching {
                val servers = subParser.parse(fetcher.fetch(url))
                if (servers.isEmpty()) throw IllegalStateException(L.t("в подписке нет VLESS Reality серверов", "no VLESS Reality servers found in subscription"))
                servers
            }
            SwingUtilities.invokeLater {
                setBusy(false)
                result.fold(
                    onSuccess = { servers ->
                        val sub = Subscription(
                            id = UUID.randomUUID().toString().take(12),
                            name = subscriptionName(url),
                            url = url,
                            profiles = servers,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        subscriptions = subscriptions + sub
                        urlInput.text = ""
                        commit()
                    },
                    onFailure = { error(L.t("не удалось загрузить подписку: ", "failed to load subscription: ") + (it.message ?: L.t("ошибка", "error"))) }
                )
            }
        }.apply { isDaemon = true; start() }
    }

    private fun refreshSubscription(sub: Subscription) {
        if (busy) return
        setBusy(true)
        Thread {
            val result = runCatching {
                val servers = subParser.parse(fetcher.fetch(sub.url))
                if (servers.isEmpty()) throw IllegalStateException(L.t("в подписке нет серверов", "no servers in subscription"))
                servers
            }
            SwingUtilities.invokeLater {
                setBusy(false)
                result.fold(
                    onSuccess = { servers ->
                        subscriptions = subscriptions.map {
                            if (it.id == sub.id)
                                it.copy(profiles = servers, updatedAtMillis = System.currentTimeMillis())
                            else it
                        }
                        commit()
                    },
                    onFailure = { error(L.t("не удалось обновить подписку: ", "failed to update subscription: ") + (it.message ?: L.t("ошибка", "error"))) }
                )
            }
        }.apply { isDaemon = true; start() }
    }

    private fun deleteSubscription(sub: Subscription) {
        val res = JOptionPane.showConfirmDialog(this, L.t("Удалить подписку «${sub.name}»?", "Delete subscription \"${sub.name}\"?"),
            "Beacon", JOptionPane.YES_NO_OPTION)
        if (res != JOptionPane.YES_OPTION) return
        subscriptions = subscriptions.filterNot { it.id == sub.id }
        if (sub.profiles.any { it.id == activeId }) activeId = null
        commit()
    }

    private fun pingAll(sub: Subscription) {
        sub.profiles.forEach { pingServer(it) }
    }

    private fun pingServer(profile: ProxyProfile) {
        if (profile.id in pingingIds) return
        pingingIds += profile.id
        rebuild()
        pingPool.submit {
            val ms = probe.tcpLatencyMs(profile.host, profile.port)
            SwingUtilities.invokeLater {
                pingingIds -= profile.id
                pingResults[profile.id] = ms
                rebuild()
            }
        }
    }

    private fun pickBest(sub: Subscription) {
        val best = sub.profiles
            .mapNotNull { p -> pingResults[p.id]?.let { p to it } }
            .minByOrNull { it.second }
        if (best == null) {
            error(L.t("сначала измерь пинг серверов («Пинг всех»)", "measure server pings first (\"Ping all\")"))
            return
        }
        activeId = best.first.id
        commit()
    }

    private fun selectServer(profile: ProxyProfile) {
        activeId = profile.id
        commit()
    }

    private fun commit() {
        onChange(subscriptions, activeId)
        rebuild()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        urlInput.isEnabled = !value
        cursor = Cursor.getPredefinedCursor(if (value) Cursor.WAIT_CURSOR else Cursor.DEFAULT_CURSOR)
    }

    private fun error(msg: String) =
        JOptionPane.showMessageDialog(this, msg, "Beacon", JOptionPane.ERROR_MESSAGE)

    private fun subscriptionName(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
        return host ?: (L.t("Подписка ", "Subscription ") + (subscriptions.size + 1))
    }

    // ── rendering ────────────────────────────────────────────────────────────

    private fun rebuild() {
        listPanel.removeAll()
        if (subscriptions.isEmpty()) {
            listPanel.add(emptyHint)
        } else {
            subscriptions.forEach { sub ->
                listPanel.add(subscriptionCard(sub))
                listPanel.add(Box.createVerticalStrut(12))
            }
        }
        listPanel.revalidate()
        listPanel.repaint()
    }

    private fun subscriptionCard(sub: Subscription): JPanel {
        val card = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = T.CARD_SOLID
                g2.fillRoundRect(0, 0, width, height, 12, 12)
                g2.color = T.BORDER_SOFT
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
            }
        }.apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(12, 14, 12, 14)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        card.add(subscriptionHeader(sub))
        card.add(Box.createVerticalStrut(10))
        if (sub.profiles.isEmpty()) {
            card.add(JLabel(L.t("серверов нет — попробуй «Обновить»", "no servers — try \"Refresh\"")).apply {
                foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 11f); alignmentX = 0f
            })
        } else {
            sub.profiles.forEach { p ->
                card.add(serverRow(p))
                card.add(Box.createVerticalStrut(6))
            }
        }

        val rows = sub.profiles.size.coerceAtLeast(1)
        card.maximumSize = Dimension(Int.MAX_VALUE, 70 + rows * 44)
        return card
    }

    private fun subscriptionHeader(sub: Subscription): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 56)

        val title = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(sub.name).apply {
                foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 14f); alignmentX = 0f
            })
            add(Box.createVerticalStrut(2))
            add(JLabel(L.t("${sub.profiles.size} серверов", "${sub.profiles.size} servers")).apply {
                foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 11f); alignmentX = 0f
            })
        }
        title.alignmentX = Component.LEFT_ALIGNMENT
        add(title)
        add(Box.createHorizontalGlue())
        add(smallButton(L.t("Пинг всех", "Ping all")) { pingAll(sub) })
        add(Box.createHorizontalStrut(6))
        add(smallButton(L.t("Лучший", "Best")) { pickBest(sub) })
        add(Box.createHorizontalStrut(6))
        add(smallButton(L.t("Обновить", "Refresh")) { refreshSubscription(sub) })
        add(Box.createHorizontalStrut(6))
        add(smallButton(L.t("Удалить", "Delete")) { deleteSubscription(sub) })
    }

    private fun smallButton(text: String, onClick: () -> Unit): JComponent =
        T.ghostButton(text).apply {
            font = font.deriveFont(Font.BOLD, 10f)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(T.BORDER_SOFT, 1, true),
                EmptyBorder(5, 9, 5, 9)
            )
            isEnabled = !busy
            addActionListener { onClick() }
        }

    private fun serverRow(p: ProxyProfile): JPanel {
        val active = p.id == activeId
        val row = object : JPanel(BorderLayout(10, 0)) {
            var hoverProgress = 0f
            val animator = HoverAnimator(this) { hoverProgress = it }
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                // Background color interpolation
                val bgStart = if (active) Color(40, 60, 130) else T.BG_INPUT
                val bgEnd = if (active) Color(40, 60, 130) else T.BG_INPUT_HOVER
                val r = bgStart.red + ((bgEnd.red - bgStart.red) * hoverProgress).toInt()
                val gDec = bgStart.green + ((bgEnd.green - bgStart.green) * hoverProgress).toInt()
                val b = bgStart.blue + ((bgEnd.blue - bgStart.blue) * hoverProgress).toInt()
                g2.color = Color(r, gDec, b)
                g2.fillRoundRect(0, 0, width, height, 8, 8)

                if (active) {
                    g2.color = T.ACCENT_LIGHT
                    g2.stroke = BasicStroke(1f)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 8, 8)
                } else if (hoverProgress > 0f) {
                    val borderStart = Color(T.BORDER_SOFT.red, T.BORDER_SOFT.green, T.BORDER_SOFT.blue, 0)
                    val borderEnd = T.BORDER_SOFT
                    val br = borderStart.red + ((borderEnd.red - borderStart.red) * hoverProgress).toInt()
                    val bgBorder = borderStart.green + ((borderEnd.green - borderStart.green) * hoverProgress).toInt()
                    val bb = borderStart.blue + ((borderEnd.blue - borderStart.blue) * hoverProgress).toInt()
                    val alpha = (borderEnd.alpha * hoverProgress).toInt().coerceIn(0, 255)
                    g2.color = Color(br, bgBorder, bb, alpha)
                    g2.stroke = BasicStroke(1f)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 8, 8)
                }
            }
        }.apply {
            isOpaque = false
            border = EmptyBorder(7, 10, 7, 10)
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 40)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val left = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(FlagComponent(CountryDetector.detect(p.name)))
            add(Box.createHorizontalStrut(10))
            val text = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JLabel(p.name).apply {
                    foreground = if (active) T.ACCENT_LIGHT else T.TEXT
                    font = font.deriveFont(if (active) Font.BOLD else Font.PLAIN, 13f)
                    alignmentX = 0f
                })
                add(Box.createVerticalStrut(1))
                add(JLabel("${p.host}:${p.port}").apply {
                    foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 10f); alignmentX = 0f
                })
            }
            add(text)
        }
        row.add(left, BorderLayout.CENTER)
        row.add(pingLabel(p), BorderLayout.EAST)

        row.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { selectServer(p) }
            override fun mouseEntered(e: MouseEvent) { row.animator.setTarget(1f) }
            override fun mouseExited(e: MouseEvent) { row.animator.setTarget(0f) }
        })
        return row
    }

    private fun pingLabel(p: ProxyProfile): JComponent {
        val text: String
        val color: Color
        when {
            p.id in pingingIds -> { text = "…"; color = T.ACCENT_LIGHT }
            pingResults.containsKey(p.id) -> {
                val ms = pingResults[p.id]
                text = ms?.let { "$it ms" } ?: "—"
                color = when {
                    ms == null -> T.MUTED
                    ms < 80 -> T.SUCCESS
                    ms < 200 -> T.WARN
                    else -> T.DANGER
                }
            }
            else -> { text = L.t("пинг", "ping"); color = T.MUTED }
        }
        return JLabel(text).apply {
            foreground = color
            font = font.deriveFont(Font.BOLD, 11f)
            toolTipText = L.t("Нажми, чтобы измерить задержку", "Click to measure latency")
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = EmptyBorder(0, 8, 0, 4)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) { pingServer(p) }
            })
        }
    }

    /** Fixed-size component that paints a country flag. */
    private class FlagComponent(private val code: String?) : JComponent() {
        init {
            val size = Dimension(26, 18)
            preferredSize = size; minimumSize = size; maximumSize = size
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            val fw = 24; val fh = 16
            FlagIcon.paint(g2, code, (width - fw) / 2, (height - fh) / 2, fw, fh)
        }
    }
}
