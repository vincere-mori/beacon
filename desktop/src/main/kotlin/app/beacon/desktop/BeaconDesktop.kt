package app.beacon.desktop

import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProxyProfile
import app.beacon.core.net.LatencyProbe
import app.beacon.core.parser.ProfileInputParser
import app.beacon.core.singbox.InboundMode
import app.beacon.core.singbox.SingBoxConfigBuilder
import app.beacon.core.singbox.SingBoxConfigSettings
import com.formdev.flatlaf.FlatDarkLaf
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.ToolTipManager
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.system.exitProcess
import app.beacon.desktop.BeaconTheme as T

fun main() {
    if (Platform.isWindows) refreshShellIconCacheOnce()

    if (Platform.isWindows) {
        // Let FlatLaf draw the titlebar instead of Windows DWM so our brand
        // colours apply to the title strip and buttons. On Linux the native WM
        // handles decorations — forcing FlatLaf ones there breaks tiling WMs.
        System.setProperty("flatlaf.useWindowDecorations", "true")
        System.setProperty("flatlaf.menuBarEmbedded", "true")
        JFrame.setDefaultLookAndFeelDecorated(true)
        JDialog.setDefaultLookAndFeelDecorated(true)
    }

    FlatDarkLaf.setup()

    if (Platform.isWindows) {
        // Brand colours for the FlatLaf-drawn titlebar (Windows only)
        UIManager.put("TitlePane.background",          Color(15, 21, 53))
        UIManager.put("TitlePane.inactiveBackground",  Color(10, 15, 38))
        UIManager.put("TitlePane.foreground",          Color(220, 230, 250))
        UIManager.put("TitlePane.inactiveForeground",  Color(120, 140, 190))
        UIManager.put("TitlePane.borderColor",         Color(28, 38, 80))
        UIManager.put("TitlePane.unifiedBackground",   true)
        UIManager.put("TitlePane.buttonHoverBackground",   Color(40, 55, 120))
        UIManager.put("TitlePane.buttonPressedBackground", Color(28, 40, 95))
        UIManager.put("TitlePane.closeHoverBackground",    Color(185, 38, 48))
        UIManager.put("TitlePane.closeHoverForeground",    Color.WHITE)
        UIManager.put("TitlePane.closePressedBackground",  Color(220, 20, 30))
        UIManager.put("TitlePane.closePressedForeground",  Color.WHITE)
        UIManager.put("TitlePane.menuBarEmbedded", true)
    }

    UIManager.put("RootPane.background", Color(15, 21, 53))

    UIManager.put("Component.arc", 14)
    UIManager.put("Button.arc", 18)
    UIManager.put("ScrollBar.width", 8)
    UIManager.put("ToolTip.background", Color(28, 38, 80))
    UIManager.put("ToolTip.foreground", Color(220, 230, 255))
    UIManager.put("ToolTip.border", BorderFactory.createLineBorder(Color(70, 100, 200), 1))
    ToolTipManager.sharedInstance().initialDelay = 250
    ToolTipManager.sharedInstance().reshowDelay = 80
    ToolTipManager.sharedInstance().dismissDelay = 8000

    SwingUtilities.invokeLater {
        BeaconDesktop().show()
    }
}

/**
 * After an install or upgrade, Windows Start-menu search often caches a generic
 * icon for the new shortcut. ie4uinit refreshes the shell icon cache so the
 * real Beacon icon appears next time the user types "Beacon". We tie this to
 * the running .exe's path + last-modified time, so it fires exactly once per
 * installed build — not on every launch.
 */
private fun refreshShellIconCacheOnce() {
    runCatching {
        val exePath = ProcessHandle.current().info().command().orElse(null) ?: return@runCatching
        val exe = java.nio.file.Path.of(exePath)
        if (!java.nio.file.Files.exists(exe)) return@runCatching
        val mtime = java.nio.file.Files.getLastModifiedTime(exe).toMillis()
        val marker = DesktopPaths.appDir.resolve("icon-cache.marker")
        val signature = "$exePath|$mtime"
        if (java.nio.file.Files.exists(marker) &&
            java.nio.file.Files.readString(marker).trim() == signature) return@runCatching

        ProcessBuilder("ie4uinit.exe", "-ClearIconCache").start()
            .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        ProcessBuilder("ie4uinit.exe", "-show").start()
            .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        java.nio.file.Files.writeString(marker, signature)
    }
}

class BeaconDesktop(
    private val store: DesktopProfileStore = DesktopProfileStore(),
    private val parser: ProfileInputParser = ProfileInputParser(),
    private val configBuilder: SingBoxConfigBuilder = SingBoxConfigBuilder(),
    private val singBox: SingBoxProcess = SingBoxProcess(),
    private val systemProxy: SystemProxy = platformProxy()
) {
    private var state = store.load()
    private var connected = false
    private var connecting = false
    private var refreshing = false

    private lateinit var frame: JFrame
    private val hero = LighthouseHero()

    private val statusDot = JLabel("●")
    private val statusText = JLabel("Отключено")
    private val keyChip = JButton()

    private val mainBtn = primaryConnectButton()

    private val pingValue = JLabel("—")
    private val downValue = JLabel("—")
    private val upValue = JLabel("—")
    private val pingLabel = JLabel("ping")
    private val downLabel = JLabel("↓ down")
    private val upLabel = JLabel("↑ up")
    private val pingTestBtn = T.ghostButton("Тест").apply {
        toolTipText = "Проверить задержку до активного сервера"
        preferredSize = Dimension(78, 26)
        maximumSize = Dimension(78, 26)
        minimumSize = Dimension(78, 26)
        font = font.deriveFont(Font.BOLD, 11f)
        addActionListener { runPing(showProgress = true) }
    }

    private val proxyModeBtn = JToggleButton("Proxy")
    private val tunModeBtn = JToggleButton("TUN")
    private val warpModeBtn = PillToggleButton("WARP")

    private val latencyProbe = LatencyProbe()
    private var trafficMonitor: TrafficMonitor? = null
    private val pingTimer = Timer(5000) { runPing() }
    private var pinging = false

    fun show() {
        frame = JFrame("Beacon").apply {
            defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            minimumSize = Dimension(880, 720)
            preferredSize = Dimension(960, 760)
            iconImage = ImageIcon(BeaconDesktop::class.java.getResource("/icon.png")).image
            // Theme the titlebar via FlatLaf client properties (3.x)
            rootPane.putClientProperty("JRootPane.titleBarBackground", Color(15, 21, 53))
            rootPane.putClientProperty("JRootPane.titleBarForeground", Color(220, 230, 250))
            rootPane.putClientProperty("JRootPane.titleBarBorderColor", Color(28, 38, 80))
            contentPane = buildRoot()
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) = shutdown()
                override fun windowIconified(e: WindowEvent) = hero.pauseAnimation()
                override fun windowDeiconified(e: WindowEvent) = hero.resumeAnimation()
            })
            pack()
            setLocationRelativeTo(null)
        }
        refresh()
        frame.isVisible = true
        pingTimer.initialDelay = 1000
    }

    private fun buildRoot(): JPanel {
        return object : JPanel(BorderLayout(0, 0)) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.paint = GradientPaint(0f, 0f, T.BG_TOP, 0f, height.toFloat(), T.BG_BOT)
                g2.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque = true
            add(topBar(), BorderLayout.NORTH)
            add(centerStack(), BorderLayout.CENTER)
            add(bottomBar(), BorderLayout.SOUTH)
        }
    }

    private fun topBar(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = EmptyBorder(10, 18, 4, 14)

        val right = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(T.iconButton("🌐", "Подписки").apply {
                addActionListener { openSubscriptions() }
            })
            add(Box.createHorizontalStrut(2))
            add(T.iconButton("🔑", "Управление ключами").apply {
                addActionListener { openKeys() }
            })
            add(Box.createHorizontalStrut(2))
            add(T.iconButton("⚙", "Настройки").apply {
                addActionListener { openSettings() }
            })
        }
        add(right, BorderLayout.EAST)
    }

    private fun centerStack(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(0, 22, 0, 22)

        add(Box.createVerticalStrut(4))

        hero.alignmentX = Component.CENTER_ALIGNMENT
        add(hero)

        add(Box.createVerticalStrut(8))
        add(statusRow())
        add(Box.createVerticalStrut(14))
        add(mainBtn.also { it.alignmentX = Component.CENTER_ALIGNMENT })
        add(Box.createVerticalStrut(18))
        add(statsRow())
        add(Box.createVerticalGlue())
    }

    private fun statusRow(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.CENTER_ALIGNMENT

        add(Box.createHorizontalGlue())
        add(statusDot.apply { font = font.deriveFont(13f); foreground = T.MUTED })
        add(Box.createHorizontalStrut(8))
        add(statusText.apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 14f) })
        add(Box.createHorizontalStrut(14))

        keyChip.apply {
            isOpaque = false
            isContentAreaFilled = false
            isFocusPainted = false
            border = EmptyBorder(6, 12, 6, 12)
            foreground = T.ACCENT_LIGHT
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { showKeyPicker(this) }
        }
        add(KeyChipFrame(keyChip))
        add(Box.createHorizontalGlue())
    }

    private fun statsRow(): JPanel = JPanel().apply {
        isOpaque = false
        layout = GridLayout(1, 3, 14, 0)
        alignmentX = Component.CENTER_ALIGNMENT
        maximumSize = Dimension(720, 108)
        preferredSize = Dimension(720, 108)

        add(statCard(pingValue, pingLabel, T.ACCENT_LIGHT,
            "<html>Задержка до сервера в миллисекундах.<br>Чем меньше — тем отзывчивее соединение.</html>",
            pingTestBtn))
        add(statCard(downValue, downLabel, T.SUCCESS,
            "<html>Скорость загрузки прямо сейчас (входящий трафик).<br>Обновляется раз в секунду.</html>"))
        add(statCard(upValue, upLabel, T.WARN,
            "<html>Скорость отдачи прямо сейчас (исходящий трафик).<br>Обновляется раз в секунду.</html>"))
    }

    private fun statCard(value: JLabel, label: JLabel, accent: Color, tip: String, action: JButton? = null): JPanel =
        T.card(14f).apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        toolTipText = tip
        value.apply {
            foreground = accent
            font = font.deriveFont(Font.BOLD, 22f)
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }
        label.apply {
            foreground = T.MUTED
            font = font.deriveFont(Font.PLAIN, 11f)
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }
        add(Box.createVerticalGlue())
        add(value.fullWidth())
        add(Box.createVerticalStrut(2))
        add(label.fullWidth())
        action?.let {
            add(Box.createVerticalStrut(7))
            add(it.also { btn -> btn.alignmentX = Component.CENTER_ALIGNMENT })
        }
        add(Box.createVerticalGlue())
    }

    private fun bottomBar(): JPanel = JPanel().apply {
        isOpaque = false
        border = EmptyBorder(8, 22, 18, 22)
        layout = BoxLayout(this, BoxLayout.X_AXIS)

        val seg = ModeSegment(proxyModeBtn, tunModeBtn) { mode ->
            if (refreshing) return@ModeSegment
            val wasConnected = connected
            state = state.copy(inboundMode = mode); persist()
            refresh()
            if (wasConnected) reconnectAfterModeChange()
        }
        proxyModeBtn.toolTipText =
            "<html><b>Proxy</b> — VPN только для браузера и приложений с поддержкой прокси.<br>" +
            "Работает без админских прав. Через 127.0.0.1:$PROXY_PORT.</html>"
        tunModeBtn.toolTipText =
            "<html><b>TUN</b> — VPN для всей системы, включая игры и любые приложения.<br>" +
            "Требует запуск от администратора.</html>"
        warpModeBtn.toolTipText =
            "<html><b>WARP</b> — отдельный маршрут для Google / Gemini.<br>" +
            "Если Gemini не открывается через основной сервер, включи этот режим.</html>"
        styleWarpButton()
        warpModeBtn.addActionListener {
            if (refreshing) return@addActionListener
            handleWarpClick()
        }

        add(Box.createHorizontalGlue())
        add(seg)
        add(Box.createHorizontalStrut(12))
        add(warpModeBtn)
        add(Box.createHorizontalGlue())
    }

    private fun primaryConnectButton(): JButton {
        return object : JButton("Подключить") {
            init {
                isOpaque = false
                isContentAreaFilled = false
                isBorderPainted = false
                isFocusPainted = false
                font = font.deriveFont(Font.BOLD, 16f)
                foreground = Color.WHITE
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                preferredSize = Dimension(320, 58)
                maximumSize = Dimension(320, 58)
                minimumSize = Dimension(320, 58)
                addActionListener { toggleConnect() }
                addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
                    override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
                })
            }
            var hover = false
            var pressed = false
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val base = when {
                    connecting -> T.WARN
                    connected -> T.DANGER
                    else -> T.ACCENT
                }
                val color = if (hover) base.brighter() else base
                val y = if (pressed) 2 else 0
                g2.color = Color(0, 0, 0, 52)
                g2.fillRoundRect(2, 5, width - 4, height - 5, 22, 22)
                g2.paint = GradientPaint(0f, y.toFloat(), color, 0f, height.toFloat(), base.darker())
                g2.fillRoundRect(0, y, width, height - y - 1, 22, 22)
                val glowColor = Color(color.red, color.green, color.blue, 90)
                g2.color = glowColor
                g2.stroke = BasicStroke(2f)
                g2.drawRoundRect(1, y + 1, width - 3, height - y - 3, 22, 22)
                super.paintComponent(g)
            }
            override fun processMouseEvent(e: MouseEvent) {
                when (e.id) {
                    MouseEvent.MOUSE_PRESSED -> pressed = true
                    MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_EXITED -> pressed = false
                }
                super.processMouseEvent(e)
                repaint()
            }
        }
    }

    private fun toggleConnect() {
        if (connected || connecting) disconnect() else connect()
    }

    private fun showKeyPicker(anchor: Component) {
        val menu = JPopupMenu().apply {
            background = T.CARD_SOLID
            border = BorderFactory.createLineBorder(T.BORDER, 1)
        }
        val all = state.allProfiles
        if (all.isEmpty()) {
            menu.add(JMenuItem("Добавить ключ…").apply {
                foreground = T.TEXT; background = T.CARD_SOLID
                addActionListener { openKeys() }
            })
        } else {
            all.forEach { p ->
                val active = p.id == state.activeProfileId
                val item = JMenuItem("${if (active) "✓ " else "   "}${p.name}   ${p.host}:${p.port}")
                item.foreground = if (active) T.ACCENT_LIGHT else T.TEXT
                item.background = T.CARD_SOLID
                item.font = item.font.deriveFont(if (active) Font.BOLD else Font.PLAIN, 12f)
                item.addActionListener {
                    state = state.copy(activeProfileId = p.id); persist(); refresh()
                }
                menu.add(item)
            }
            menu.addSeparator()
            menu.add(JMenuItem("Управление ключами…").apply {
                foreground = T.MUTED; background = T.CARD_SOLID
                addActionListener { openKeys() }
            })
            menu.add(JMenuItem("Подписки…").apply {
                foreground = T.MUTED; background = T.CARD_SOLID
                addActionListener { openSubscriptions() }
            })
        }
        menu.show(anchor, 0, anchor.height + 4)
    }

    private fun openKeys() {
        KeyManagerDialog(frame, parser, state.profiles, state.activeProfileId) { profiles, activeId ->
            state = state.copy(profiles = profiles, activeProfileId = activeId)
            persist(); refresh()
        }.isVisible = true
    }

    private fun openSubscriptions() {
        SubscriptionDialog(frame, state.subscriptions, state.activeProfileId) { subscriptions, activeId ->
            state = state.copy(subscriptions = subscriptions, activeProfileId = activeId)
            persist(); refresh()
        }.isVisible = true
    }

    private fun styleWarpButton() {
        warpModeBtn.isOpaque = false
        warpModeBtn.isContentAreaFilled = false
        warpModeBtn.isBorderPainted = false
        warpModeBtn.isFocusPainted = false
        warpModeBtn.border = EmptyBorder(0, 14, 0, 14)
        warpModeBtn.preferredSize = Dimension(112, 42)
        warpModeBtn.maximumSize = Dimension(112, 42)
        warpModeBtn.minimumSize = Dimension(112, 42)
        warpModeBtn.font = warpModeBtn.font.deriveFont(Font.BOLD, 13f)
        warpModeBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun handleWarpClick() {
        if (!hasUsableWarpCredentials() && warpModeBtn.isSelected) {
            registerWarp()
            return
        }

        val wasConnected = connected
        state = state.copy(warpEnabled = warpModeBtn.isSelected)
        persist()
        refresh()
        if (wasConnected) reconnectAfterModeChange()
    }

    private fun registerWarp() {
        warpModeBtn.isEnabled = false
        warpModeBtn.text = "..."
        Thread {
            val result = runCatching { WarpManager.register() }
            SwingUtilities.invokeLater {
                result.fold(
                    onSuccess = { creds ->
                        val wasConnected = connected
                        state = state.copy(warpCredentials = creds, warpEnabled = true)
                        persist()
                        refresh()
                        if (wasConnected) reconnectAfterModeChange()
                    },
                    onFailure = { err ->
                        state = state.copy(warpEnabled = false)
                        persist()
                        refresh()
                        showError("WARP не зарегистрировался: ${err.message ?: "ошибка"}")
                    }
                )
                warpModeBtn.isEnabled = true
            }
        }.apply { isDaemon = true; start() }
    }

    private fun openSettings() {
        val dlg = JDialog(frame, "Настройки", true)
        val content = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                g.color = T.BG_BOT; g.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque = true
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(20, 22, 20, 22)
        }

        content.add(JLabel("Настройки").apply {
            foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 20f); alignmentX = 0f
        })
        content.add(Box.createVerticalStrut(14))

        val card = T.card().apply {
            layout = GridLayout(2, 2, 16, 12)
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 110)
        }
        card.add(JLabel("DNS").apply {
            foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 12f)
            toolTipText = "<html>DNS-сервер для разрешения доменов через VPN.<br>Cloudflare быстрее, Google надёжнее.</html>"
        })
        val dnsBox = JComboBox(DnsMode.entries.toTypedArray()).apply {
            background = T.CARD_SOLID; foreground = T.TEXT
            selectedItem = state.dnsMode
            addActionListener {
                state = state.copy(dnsMode = selectedItem as DnsMode); persist()
            }
        }
        card.add(dnsBox)
        card.add(JLabel("IPv6").apply {
            foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 12f)
            toolTipText = "Включить IPv6. Большинству сайтов не нужно."
        })
        val ipv6Box = JCheckBox().apply {
            isOpaque = false; isSelected = state.ipv6Enabled
            addActionListener {
                state = state.copy(ipv6Enabled = isSelected); persist()
            }
        }
        card.add(ipv6Box)
        content.add(card)

        content.add(Box.createVerticalStrut(14))

        val restoreCard = T.card().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 72)
        }
        val restoreText = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Восстановить системный прокси").apply {
                foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 12f); alignmentX = 0f
            })
            add(JLabel("если интернет пропал после отключения VPN").apply {
                foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 11f); alignmentX = 0f
            })
        }
        restoreCard.add(restoreText)
        restoreCard.add(Box.createHorizontalGlue())
        restoreCard.add(T.ghostButton("Вернуть proxy").apply {
            addActionListener { systemProxy.restore() }
            preferredSize = Dimension(150, 36)
            maximumSize = Dimension(150, 36)
        })
        content.add(restoreCard)

        content.add(Box.createVerticalStrut(10))

        val logCard = T.card().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 72)
        }
        val logText = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Журнал sing-box").apply {
                foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 12f); alignmentX = 0f
            })
            add(JLabel("технический лог для диагностики").apply {
                foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 11f); alignmentX = 0f
            })
        }
        logCard.add(logText)
        logCard.add(Box.createHorizontalGlue())
        logCard.add(T.ghostButton("Открыть").apply {
            addActionListener { openLog() }
            preferredSize = Dimension(150, 36)
            maximumSize = Dimension(150, 36)
        })
        content.add(logCard)

        content.add(Box.createVerticalGlue())
        val closeRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 40)
            add(Box.createHorizontalGlue())
            add(T.accentButton("Готово").apply {
                preferredSize = Dimension(140, 36)
                addActionListener { dlg.dispose() }
            })
        }
        content.add(closeRow)

        dlg.contentPane = content
        dlg.size = Dimension(520, 620)
        dlg.setLocationRelativeTo(frame)
        dlg.isVisible = true
    }

    private fun openLog() = runCatching {
        DesktopPaths.logFile.parent?.let { Files.createDirectories(it) }
        if (!DesktopPaths.logFile.exists()) DesktopPaths.logFile.createFile()
        Desktop.getDesktop().open(DesktopPaths.logFile.toFile())
    }.onFailure { showError("не удалось открыть лог: ${DesktopPaths.logFile}") }

    private fun connect() {
        val profile = state.activeProfile ?: run {
            showError("сначала добавь и выбери ключ"); openKeys(); return
        }
        if (state.inboundMode == InboundMode.Tun && !isElevated()) {
            val res = JOptionPane.showConfirmDialog(
                frame,
                "TUN перехватывает весь трафик и требует запуск от администратора.\nПерезапустить Beacon с правами администратора?",
                "Beacon", JOptionPane.YES_NO_OPTION
            )
            if (res == JOptionPane.YES_OPTION && relaunchElevated()) { shutdown(); return }
            showError("Запусти Beacon от администратора или выбери режим Proxy"); return
        }

        val warp = state.warpCredentials
        if (state.warpEnabled && !hasUsableWarpCredentials()) {
            showError("WARP нужно зарегистрировать заново")
            return
        }

        connecting = true; refresh()

        val config = configBuilder.build(
            profile = profile,
            settings = SingBoxConfigSettings(
                dnsMode = state.dnsMode, ipv6Enabled = state.ipv6Enabled,
                inboundMode = state.inboundMode, mixedListenPort = PROXY_PORT,
                clashApiPort = CLASH_PORT,
                warpEnabled = state.warpEnabled && hasUsableWarpCredentials(),
                warpPrivateKey = warp?.privateKey ?: "",
                warpLocalAddressV4 = warp?.localAddressV4 ?: "",
                warpLocalAddressV6 = warp?.localAddressV6 ?: "",
                warpPeerPublicKey = warp?.peerPublicKey ?: "",
                warpEndpoint = warp?.endpoint?.let { WarpManager.resolveEndpoint(it) } ?: "",
                warpReserved = warp?.reserved ?: listOf(0, 0, 0)
            )
        )

        Thread {
            val result = singBox.start(config, tunMode = state.inboundMode == InboundMode.Tun)
            val error = if (result.isSuccess) {
                if (state.inboundMode == InboundMode.Mixed)
                    runCatching { systemProxy.enable(PROXY_PORT) }
                        .onFailure { singBox.stop() }.exceptionOrNull()
                else null
            } else result.exceptionOrNull()

            SwingUtilities.invokeLater {
                connecting = false
                connected = error == null
                if (connected) startMonitoring()
                refresh()
                error?.let { showError(it.message ?: "не удалось подключиться") }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun disconnect() {
        connecting = false
        stopMonitoring()
        Thread {
            runCatching { singBox.stop(); systemProxy.restore() }
            SwingUtilities.invokeLater { connected = false; refresh() }
        }.apply { isDaemon = true; start() }
    }

    private fun reconnectAfterModeChange() {
        Thread {
            runCatching { singBox.stop(); systemProxy.restore() }
            SwingUtilities.invokeLater { connected = false; stopMonitoring(); refresh() }
            Thread.sleep(450)
            SwingUtilities.invokeLater { connect() }
        }.apply { isDaemon = true; start() }
    }

    private fun startMonitoring() {
        pingTimer.start()
        runPing()
        trafficMonitor = TrafficMonitor(CLASH_PORT).also { tm ->
            tm.start { sample ->
                SwingUtilities.invokeLater {
                    downValue.text = formatBytesPerSec(sample.down)
                    upValue.text = formatBytesPerSec(sample.up)
                }
            }
        }
    }

    private fun stopMonitoring() {
        pingTimer.stop()
        trafficMonitor?.stop()
        trafficMonitor = null
        downValue.text = "—"
        upValue.text = "—"
        pingValue.text = "—"
    }

    private fun runPing(showProgress: Boolean = false) {
        if (pinging) return
        val profile = state.activeProfile ?: return
        pinging = true
        if (showProgress) {
            pingValue.text = "..."
            pingValue.foreground = T.ACCENT_LIGHT
            pingTestBtn.isEnabled = false
        }
        Thread {
            val ms = latencyProbe.tcpLatencyMs(profile.host, profile.port)
            SwingUtilities.invokeLater {
                pinging = false
                pingTestBtn.isEnabled = state.activeProfile != null
                pingValue.text = ms?.let { "$it ms" } ?: "—"
                pingValue.foreground = when {
                    ms == null -> T.MUTED
                    ms < 80 -> T.SUCCESS
                    ms < 200 -> T.WARN
                    else -> T.DANGER
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun shutdown() {
        stopMonitoring()
        runCatching { singBox.stop(); systemProxy.restore() }
        frame.dispose(); exitProcess(0)
    }

    private fun persist() = store.save(state)

    private fun refresh() {
        refreshing = true
        try {
            when {
                connecting -> {
                    hero.heroState = LighthouseHero.HeroState.CONNECTING
                    statusDot.foreground = T.WARN; statusText.foreground = T.WARN
                    statusText.text = "Подключение…"
                    mainBtn.text = "Отмена"
                }
                connected -> {
                    hero.heroState = LighthouseHero.HeroState.ON
                    statusDot.foreground = T.SUCCESS; statusText.foreground = T.SUCCESS
                    statusText.text = "Подключено"
                    mainBtn.text = "Отключить"
                }
                else -> {
                    hero.heroState = LighthouseHero.HeroState.OFF
                    statusDot.foreground = T.MUTED; statusText.foreground = T.TEXT_DIM
                    statusText.text = "Отключено"
                    mainBtn.text = "Подключить"
                }
            }
            mainBtn.repaint()

            val active = state.activeProfile
            keyChip.text = if (active != null) {
                "  ${active.name}  ▾"
            } else "  выбери ключ  ▾"

            when (state.inboundMode) {
                InboundMode.Mixed -> proxyModeBtn.isSelected = true
                InboundMode.Tun -> tunModeBtn.isSelected = true
            }
            proxyModeBtn.repaint(); tunModeBtn.repaint()
            warpModeBtn.isSelected = state.warpEnabled && hasUsableWarpCredentials()
            warpModeBtn.text = if (hasUsableWarpCredentials()) "WARP" else "WARP +"
            warpModeBtn.background = if (warpModeBtn.isSelected) T.ACCENT else T.BG_INPUT
            warpModeBtn.foreground = if (warpModeBtn.isSelected) Color.WHITE else T.MUTED
            pingTestBtn.isEnabled = active != null && !pinging
            warpModeBtn.repaint()
        } finally {
            refreshing = false
        }
        frame.contentPane.revalidate()
        frame.contentPane.repaint()
    }

    private fun showError(msg: String) = SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(frame, msg, "Beacon", JOptionPane.ERROR_MESSAGE)
    }

    private fun hasUsableWarpCredentials(): Boolean {
        val creds = state.warpCredentials ?: return false
        return creds.privateKey.isNotBlank() && creds.localAddressV4.isNotBlank()
    }

    private fun isElevated() = runCatching {
        if (Platform.isWindows) {
            ProcessBuilder("cmd", "/c", "net session >nul 2>&1").start().waitFor() == 0
        } else {
            ProcessBuilder("id", "-u").start()
                .also { it.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
                .inputStream.bufferedReader().readText().trim() == "0"
        }
    }.getOrDefault(false)

    private fun relaunchElevated(): Boolean {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return false
        val args = ProcessHandle.current().info().arguments()
            .map { it.toList() }.orElse(emptyList())
        return if (Platform.isWindows) {
            val path = Path.of(command)
            if (!path.fileName.toString().equals("Beacon.exe", ignoreCase = true)) return false
            val q = "'" + path.toAbsolutePath().toString().replace("'", "''") + "'"
            runCatching {
                ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", "Start-Process -FilePath $q -Verb RunAs").start(); true
            }.getOrDefault(false)
        } else {
            runCatching {
                ProcessBuilder(listOf("pkexec") + listOf(command) + args).start(); true
            }.getOrDefault(false)
        }
    }

    private fun JLabel.fullWidth() = apply {
        alignmentX = Component.CENTER_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    // ── Inner UI components ──────────────────────────────────────────────────

    /** Visual frame around the key chip button. */
    private class KeyChipFrame(private val btn: JButton) : JPanel() {
        init {
            isOpaque = false
            layout = BorderLayout()
            add(btn, BorderLayout.CENTER)
            maximumSize = Dimension(280, 32)
            preferredSize = Dimension(220, 32)
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = T.BG_INPUT
            g2.fillRoundRect(0, 0, width, height, 18, 18)
            g2.color = T.BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
        }
    }

    private class PillToggleButton(text: String) : JToggleButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val active = isSelected
            val hover = model.isRollover
            val top = when {
                active -> T.ACCENT_HOVER
                hover -> T.BG_INPUT_HOVER
                else -> T.BG_INPUT
            }
            val bottom = when {
                active -> T.ACCENT
                hover -> T.BG_INPUT
                else -> T.BG_INPUT.darker()
            }
            T.softFill(g2, width, height, top, bottom, 18)
            g2.color = if (active) T.ACCENT_LIGHT else T.BORDER_SOFT
            g2.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
            super.paintComponent(g)
        }
    }

    /** Segmented control for Proxy/TUN. */
    private inner class ModeSegment(
        private val a: JToggleButton,
        private val b: JToggleButton,
        onChange: (InboundMode) -> Unit
    ) : JPanel() {
        init {
            isOpaque = false
            layout = GridLayout(1, 2, 0, 0)
            preferredSize = Dimension(320, 42)
            maximumSize = Dimension(320, 42)
            minimumSize = Dimension(320, 42)
            ButtonGroup().apply { add(a); add(b) }
            styleToggle(a); styleToggle(b)
            add(a); add(b)
            a.addActionListener { if (a.isSelected) { refreshChips(); onChange(InboundMode.Mixed) } }
            b.addActionListener { if (b.isSelected) { refreshChips(); onChange(InboundMode.Tun) } }
        }
        private fun styleToggle(t: JToggleButton) {
            t.isOpaque = false
            t.isContentAreaFilled = false
            t.isBorderPainted = false
            t.isFocusPainted = false
            t.foreground = T.MUTED
            t.font = t.font.deriveFont(Font.BOLD, 13f)
            t.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            t.addChangeListener { t.foreground = if (t.isSelected) Color.WHITE else T.MUTED; t.repaint() }
        }
        private fun refreshChips() { a.repaint(); b.repaint() }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = T.BG_INPUT
            g2.fillRoundRect(0, 0, width, height, 18, 18)
            // selected pill
            val w2 = width / 2
            val selX = if (a.isSelected) 0 else w2
            val pill = g2.create(selX + 2, 2, w2 - 4, height - 4) as Graphics2D
            try {
                T.softFill(pill, w2 - 4, height - 4, T.ACCENT_HOVER, T.ACCENT, 16)
            } finally {
                pill.dispose()
            }
            g2.color = T.BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
        }
    }

    companion object {
        const val PROXY_PORT = 2080
        const val CLASH_PORT = 9095
    }
}
