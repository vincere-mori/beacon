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
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
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
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import app.beacon.desktop.BeaconTheme as T

private var lockFile: File? = null
private var lockChannel: FileChannel? = null
private var lock: FileLock? = null

fun checkSingleInstance(): Boolean {
    return try {
        val path = DesktopPaths.appDir.resolve("beacon.lock")
        lockFile = path.toFile()
        val raf = RandomAccessFile(lockFile, "rw")
        val channel = raf.channel
        val fLock = channel.tryLock()
        if (fLock == null) {
            false
        } else {
            lockChannel = channel
            lock = fLock
            lockFile?.deleteOnExit()
            true
        }
    } catch (e: Exception) {
        true
    }
}

fun main() {
    val store = DesktopProfileStore()
    val state = store.load()
    L.lang = state.language

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

    if (!checkSingleInstance()) {
        SwingUtilities.invokeAndWait { showAlreadyRunningDialog() }
        exitProcess(0)
    }

    if (Platform.isWindows) refreshShellIconCacheOnce()

    SwingUtilities.invokeLater {
        BeaconDesktop().show()
    }
}

private fun showAlreadyRunningDialog() {
    val dlg = JDialog(null as java.awt.Frame?, "Beacon", true).apply {
        defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        isResizable = false
        runCatching {
            ImageIcon(BeaconDesktop::class.java.getResource("/icon.png")).image
        }.getOrNull()?.let { setIconImage(it) }
    }

    val content = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.paint = GradientPaint(0f, 0f, T.BG_TOP, 0f, height.toFloat(), T.BG_BOT)
            g2.fillRect(0, 0, width, height)
        }
    }.apply {
        isOpaque = true
        border = EmptyBorder(22, 24, 22, 24)
    }

    val card = T.card(18f).apply {
        layout = BorderLayout(18, 0)
        border = EmptyBorder(18, 18, 18, 18)
    }
    val icon = JLabel(ImageIcon(
        ImageIcon(BeaconDesktop::class.java.getResource("/icon.png")).image
            .getScaledInstance(44, 44, Image.SCALE_SMOOTH)
    ))
    card.add(icon, BorderLayout.WEST)

    val text = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel(L.t("Beacon уже запущен", "Beacon is already running")).apply {
            foreground = T.TEXT
            font = font.deriveFont(Font.BOLD, 17f)
            alignmentX = 0f
        })
        add(Box.createVerticalStrut(6))
        add(JLabel(L.t("Откройте уже запущенное окно или значок в трее.", "Open the existing window or tray icon.")).apply {
            foreground = T.TEXT_DIM
            font = font.deriveFont(Font.PLAIN, 12f)
            alignmentX = 0f
        })
    }
    card.add(text, BorderLayout.CENTER)
    content.add(card, BorderLayout.CENTER)

    val buttonRow = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = EmptyBorder(16, 0, 0, 0)
        add(Box.createHorizontalGlue())
        add(T.accentButton(L.t("Понятно", "OK")).apply {
            preferredSize = Dimension(118, 36)
            addActionListener { dlg.dispose() }
        })
    }
    content.add(buttonRow, BorderLayout.SOUTH)

    dlg.contentPane = content
    dlg.size = Dimension(430, 210)
    dlg.setLocationRelativeTo(null)
    dlg.isVisible = true
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
    private var registeringWarp = false

    private lateinit var frame: JFrame
    private val appIcon = ImageIcon(BeaconDesktop::class.java.getResource("/icon.png")).image
    private val hero = LighthouseHero()
    private var trayIcon: TrayIcon? = null
    private var trayToggleItem: MenuItem? = null
    private var trayOpenItem: MenuItem? = null
    private var trayExitItem: MenuItem? = null

    private val btnSubscriptions = VectorIconButton(IconType.GLOBE, "").apply {
        addActionListener { openSubscriptions() }
    }
    private val btnKeys = VectorIconButton(IconType.KEY, "").apply {
        addActionListener { openKeys() }
    }
    private val btnSettings = VectorIconButton(IconType.GEAR, "").apply {
        addActionListener { openSettings() }
    }

    private lateinit var pingCard: JPanel
    private lateinit var downCard: JPanel
    private lateinit var upCard: JPanel

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
        preferredSize = Dimension(62, 22)
        maximumSize = Dimension(62, 22)
        minimumSize = Dimension(62, 22)
        font = font.deriveFont(Font.BOLD, 10f)
        border = BorderFactory.createEmptyBorder(4, 9, 4, 9)
        addActionListener { runPing(showProgress = true) }
    }

    private val proxyModeBtn = JToggleButton("Proxy")
    private val tunModeBtn = JToggleButton("TUN")
    private val warpModeBtn = JToggleButton("WARP")

    private val latencyProbe = LatencyProbe()
    private var trafficMonitor: TrafficMonitor? = null
    private val pingTimer = Timer(5000) { runPing() }
    private var pinging = false

    fun show() {
        val windowSize = initialWindowSize()
        frame = JFrame("Beacon").apply {
            defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            minimumSize = Dimension(
                minOf(740, windowSize.width),
                minOf(540, windowSize.height)
            )
            preferredSize = windowSize
            iconImage = appIcon
            // Theme the titlebar via FlatLaf client properties (3.x)
            rootPane.putClientProperty("JRootPane.titleBarBackground", Color(15, 21, 53))
            rootPane.putClientProperty("JRootPane.titleBarForeground", Color(220, 230, 250))
            rootPane.putClientProperty("JRootPane.titleBarBorderColor", Color(28, 38, 80))
            contentPane = buildRoot()
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    if (!hideToTray()) shutdown()
                }
                override fun windowIconified(e: WindowEvent) {
                    hero.pauseAnimation()
                }
                override fun windowDeiconified(e: WindowEvent) = hero.resumeAnimation()
            })
            pack()
            setLocationRelativeTo(null)
        }
        refresh()
        frame.isVisible = true
        syncTrayIcon()
        pingTimer.initialDelay = 1000
    }

    private fun initialWindowSize(): Dimension {
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val maxW = (bounds.width - 32).coerceAtLeast(640)
        val maxH = (bounds.height - 32).coerceAtLeast(520)
        val width = 960.coerceAtMost(maxW).coerceAtLeast(720.coerceAtMost(maxW))
        val height = 720.coerceAtMost(maxH).coerceAtLeast(540.coerceAtMost(maxH))
        return Dimension(width, height)
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
        }
    }

    private fun topBar(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = EmptyBorder(8, 18, 0, 14)

        val right = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(btnSubscriptions)
            add(Box.createHorizontalStrut(4))
            add(btnKeys)
            add(Box.createHorizontalStrut(4))
            add(btnSettings)
        }
        add(right, BorderLayout.EAST)
    }

    private fun centerStack(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(0, 18, 0, 18)

        add(Box.createVerticalStrut(2))

        hero.alignmentX = Component.CENTER_ALIGNMENT
        add(hero)

        add(Box.createVerticalStrut(4))
        add(statusRow())
        add(Box.createVerticalStrut(10))
        add(modeControls().also { it.alignmentX = Component.CENTER_ALIGNMENT })
        add(Box.createVerticalStrut(10))
        add(mainBtn.also { it.alignmentX = Component.CENTER_ALIGNMENT })
        add(Box.createVerticalStrut(14))
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

    private fun statsRow(): JPanel = object : JPanel(GridLayout(1, 3, 0, 0)) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color(10, 16, 40, 150)
            g2.fillRoundRect(0, 0, width, height, 18, 18)
            g2.color = T.BORDER_SOFT
            g2.stroke = BasicStroke(1f)
            g2.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
            g2.color = Color(255, 255, 255, 16)
            val one = width / 3
            g2.drawLine(one, 10, one, height - 10)
            g2.drawLine(one * 2, 10, one * 2, height - 10)
        }
    }.apply {
        isOpaque = false
        layout = GridLayout(1, 3, 10, 0)
        alignmentX = Component.CENTER_ALIGNMENT
        border = EmptyBorder(4, 4, 4, 4)
        maximumSize = Dimension(560, 68)
        preferredSize = Dimension(560, 68)

        pingCard = statCard(pingValue, pingLabel, T.ACCENT_LIGHT, "", pingTestBtn)
        downCard = statCard(downValue, downLabel, T.SUCCESS, "")
        upCard = statCard(upValue, upLabel, T.WARN, "")

        add(pingCard)
        add(downCard)
        add(upCard)
    }

    private fun statCard(value: JLabel, label: JLabel, accent: Color, tip: String, action: JButton? = null): JPanel =
        JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(6, 10, 6, 10)
        toolTipText = tip
        value.apply {
            foreground = accent
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }
        label.apply {
            foreground = T.MUTED
            font = font.deriveFont(Font.BOLD, 10f)
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }
        add(Box.createVerticalGlue())
        add(value.fullWidth())
        add(Box.createVerticalStrut(1))
        add(label.fullWidth())
        action?.let {
            add(Box.createVerticalStrut(3))
            add(it.also { btn -> btn.alignmentX = Component.CENTER_ALIGNMENT })
        }
        add(Box.createVerticalGlue())
    }

    private fun modeControls(): JPanel {
        proxyModeBtn.toolTipText =
            L.t("<html><b>Proxy</b> — VPN только для браузера и приложений с поддержкой прокси.<br>" +
                "Работает без админских прав. Через 127.0.0.1:$PROXY_PORT.</html>",
                "<html><b>Proxy</b> — VPN only for browser and proxy-aware apps.<br>" +
                "Works without admin privileges. Via 127.0.0.1:$PROXY_PORT.</html>")
        tunModeBtn.toolTipText =
            L.t("<html><b>TUN</b> — VPN для всей системы, включая игры и любые приложения.<br>" +
                "Требует запуск от администратора.</html>",
                "<html><b>TUN</b> — VPN for the entire system, including games and all apps.<br>" +
                "Requires admin privileges.</html>")
        warpModeBtn.toolTipText =
            L.t("<html><b>WARP</b> — отдельный маршрут для Google / Gemini.<br>" +
                "Если Gemini не открывается через основной сервер, включи этот режим.</html>",
                "<html><b>WARP</b> — separate routing for Google / Gemini.<br>" +
                "Enable this if Gemini does not open via the main server.</html>")
        warpModeBtn.addActionListener {
            if (refreshing) return@addActionListener
            handleWarpClick()
        }
        val modeSegment = ModeSegment(proxyModeBtn, tunModeBtn) { mode ->
            if (refreshing) return@ModeSegment
            val wasConnected = connected
            state = state.copy(inboundMode = mode); persist()
            refresh()
            if (wasConnected) reconnectAfterModeChange()
        }
        val warpPill = WarpToggleFrame(warpModeBtn)
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            maximumSize = Dimension(370, 38)
            preferredSize = Dimension(370, 38)
            add(modeSegment)
            add(Box.createHorizontalStrut(10))
            add(warpPill)
        }
    }

    private fun primaryConnectButton(): JButton {
        return object : JButton(L.t("Подключить", "Connect")) {
            init {
                isOpaque = false
                isContentAreaFilled = false
                isBorderPainted = false
                isFocusPainted = false
                font = font.deriveFont(Font.BOLD, 16f)
                foreground = Color.WHITE
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                preferredSize = Dimension(300, 54)
                maximumSize = Dimension(300, 54)
                minimumSize = Dimension(300, 54)
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
            isOpaque = false
        }
        fun item(text: String, selected: Boolean = false, muted: Boolean = false, action: () -> Unit): JMenuItem {
            return AnimatedMenuItem(text, selected, muted).apply {
                addActionListener { action() }
            }
        }
        val all = state.allProfiles
        if (all.isEmpty()) {
            menu.add(item(L.t("Добавить ключ…", "Add key...")) { openKeys() })
        } else {
            all.forEach { p ->
                val active = p.id == state.activeProfileId
                menu.add(item("${if (active) "✓ " else "   "}${p.name}   ${p.host}:${p.port}", active) {
                    state = state.copy(activeProfileId = p.id); persist(); refresh()
                })
            }
            menu.addSeparator()
            menu.add(item(L.t("Управление ключами…", "Manage keys..."), muted = true) { openKeys() })
            menu.add(item(L.t("Подписки…", "Subscriptions..."), muted = true) { openSubscriptions() })
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

    private fun handleWarpClick() {
        val enabled = warpModeBtn.isSelected
        val wasConnected = connected
        state = state.copy(warpEnabled = enabled)
        persist()
        refresh()

        if (enabled && !hasUsableWarpCredentials()) {
            registerWarp(reconnectOnSuccess = wasConnected)
            return
        }

        if (wasConnected) reconnectAfterModeChange()
    }

    private fun registerWarp(afterSuccess: (() -> Unit)? = null, reconnectOnSuccess: Boolean = true) {
        if (registeringWarp) return
        registeringWarp = true
        refresh()
        Thread {
            val result = runCatching { WarpManager.register() }
            SwingUtilities.invokeLater {
                var successCallback: (() -> Unit)? = null
                result.fold(
                    onSuccess = { creds ->
                        val wasConnected = connected
                        state = state.copy(warpCredentials = creds, warpEnabled = true)
                        persist()
                        registeringWarp = false
                        refresh()
                        if (wasConnected && reconnectOnSuccess) reconnectAfterModeChange()
                        successCallback = afterSuccess
                    },
                    onFailure = { err ->
                        state = state.copy(warpEnabled = false)
                        persist()
                        registeringWarp = false
                        refresh()
                        showError(L.t("WARP не зарегистрировался: ", "WARP registration failed: ") + (err.message ?: L.t("ошибка", "error")))
                    }
                )
                successCallback?.invoke()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun openSettings() {
        val dlg = JDialog(frame, L.t("Настройки", "Settings"), true)
        val content = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                g.color = T.BG_BOT; g.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque = true
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(20, 22, 20, 22)
        }

        content.add(JLabel(L.t("Настройки", "Settings")).apply {
            foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 20f); alignmentX = 0f
        })
        content.add(Box.createVerticalStrut(14))

        val dnsBox = JComboBox(DnsMode.entries.toTypedArray()).apply {
            background = T.BG_INPUT; foreground = T.TEXT
            selectedItem = state.dnsMode
            preferredSize = Dimension(130, 26)
            maximumSize = Dimension(130, 26)
            addActionListener {
                state = state.copy(dnsMode = selectedItem as DnsMode); persist()
            }
        }
        val ipv6Box = JCheckBox().apply {
            isOpaque = false
            isSelected = state.ipv6Enabled
            addActionListener {
                state = state.copy(ipv6Enabled = isSelected); persist()
            }
        }
        val trayBox = JCheckBox().apply {
            isOpaque = false
            isSelected = state.trayEnabled
            isEnabled = isTraySupported()
            toolTipText = if (isEnabled) {
                L.t("При закрытии окно скроется, а анимация остановится.", "On close, the window will hide, and animation will pause.")
            } else {
                L.t("Системный трей недоступен.", "System tray is unavailable.")
            }
            addActionListener {
                state = state.copy(trayEnabled = isSelected)
                persist()
                syncTrayIcon()
            }
        }
        val langBox = JComboBox(AppLanguage.entries.toTypedArray()).apply {
            background = T.BG_INPUT; foreground = T.TEXT
            selectedItem = state.language
            preferredSize = Dimension(130, 26)
            maximumSize = Dimension(130, 26)
            addActionListener {
                val newLang = selectedItem as AppLanguage
                if (newLang != state.language) {
                    state = state.copy(language = newLang)
                    L.lang = newLang
                    persist()
                    dlg.dispose()
                    refresh()
                    openSettings()
                }
            }
        }

        fun settingRow(titleText: String, descText: String, control: JComponent): JPanel = JPanel(BorderLayout(14, 0)).apply {
            isOpaque = false
            border = EmptyBorder(6, 4, 6, 4)
            val info = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JLabel(titleText).apply {
                    foreground = T.TEXT
                    font = font.deriveFont(Font.BOLD, 12f)
                    alignmentX = 0f
                })
                add(Box.createVerticalStrut(1))
                add(JLabel(descText).apply {
                    foreground = T.MUTED
                    font = font.deriveFont(Font.PLAIN, 10f)
                    alignmentX = 0f
                })
            }
            add(info, BorderLayout.CENTER)
            val rightWrap = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = EmptyBorder(4, 0, 4, 0)
                add(control, BorderLayout.CENTER)
            }
            add(rightWrap, BorderLayout.EAST)
        }

        val card = T.card().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = 0f
            border = EmptyBorder(12, 14, 12, 14)
            add(settingRow(
                L.t("DNS сервер", "DNS Server"),
                L.t("DNS-сервер для VPN (Cloudflare/Google)", "DNS server for VPN (Cloudflare/Google)"),
                dnsBox
            ))
            add(Box.createVerticalStrut(4))
            add(settingRow(
                L.t("Протокол IPv6", "IPv6 Support"),
                L.t("Включить поддержку IPv6 адресов", "Enable IPv6 address support"),
                ipv6Box
            ))
            add(Box.createVerticalStrut(4))
            add(settingRow(
                L.t("Сворачивать в трей", "Minimize to Tray"),
                L.t("Закрывать окно в системный трей", "Hide window in system tray on close"),
                trayBox
            ))
            add(Box.createVerticalStrut(4))
            add(settingRow(
                L.t("Язык интерфейса", "Interface Language"),
                L.t("Выберите язык интерфейса приложения", "Select interface language for application"),
                langBox
            ))
        }
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
            add(JLabel(L.t("Восстановить системный прокси", "Restore system proxy")).apply {
                foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 12f); alignmentX = 0f
            })
            add(JLabel(L.t("если интернет пропал после отключения VPN", "if internet connection is lost after disconnecting VPN")).apply {
                foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 11f); alignmentX = 0f
            })
        }
        restoreCard.add(restoreText)
        restoreCard.add(Box.createHorizontalGlue())
        restoreCard.add(T.ghostButton(L.t("Вернуть proxy", "Restore proxy")).apply {
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
            add(JLabel(L.t("Журнал sing-box", "sing-box Log")).apply {
                foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 12f); alignmentX = 0f
            })
            add(JLabel(L.t("технический лог для диагностики", "technical log for diagnostic purposes")).apply {
                foreground = T.MUTED; font = font.deriveFont(Font.PLAIN, 11f); alignmentX = 0f
            })
        }
        logCard.add(logText)
        logCard.add(Box.createHorizontalGlue())
        logCard.add(T.ghostButton(L.t("Открыть", "Open")).apply {
            addActionListener { openLog() }
            preferredSize = Dimension(150, 36)
            maximumSize = Dimension(150, 36)
        })
        content.add(logCard)

        content.add(Box.createVerticalGlue())
        content.add(Box.createVerticalStrut(18))
        val closeRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 40)
            add(Box.createHorizontalGlue())
            add(T.accentButton(L.t("Готово", "Done")).apply {
                preferredSize = Dimension(140, 36)
                addActionListener { dlg.dispose() }
            })
        }
        content.add(closeRow)

        dlg.contentPane = content
        dlg.size = Dimension(520, 660)
        dlg.setLocationRelativeTo(frame)
        dlg.isVisible = true
    }

    private fun openLog() = runCatching {
        DesktopPaths.logFile.parent?.let { Files.createDirectories(it) }
        if (!DesktopPaths.logFile.exists()) DesktopPaths.logFile.createFile()
        Desktop.getDesktop().open(DesktopPaths.logFile.toFile())
    }.onFailure { showError(L.t("не удалось открыть лог: ", "failed to open log: ") + DesktopPaths.logFile) }

    private fun connect() {
        val profile = state.activeProfile ?: run {
            showError(L.t("сначала добавь и выбери ключ", "add and select a key first")); openKeys(); return
        }
        if (state.inboundMode == InboundMode.Tun && !isElevated()) {
            val res = JOptionPane.showConfirmDialog(
                frame,
                L.t("TUN перехватывает весь трафик и требует запуск от администратора.\nПерезапустить Beacon с правами администратора?",
                    "TUN intercepts all traffic and requires administrator privileges.\nRelaunch Beacon as administrator?"),
                "Beacon", JOptionPane.YES_NO_OPTION
            )
            if (res == JOptionPane.YES_OPTION && relaunchElevated()) { shutdown(); return }
            showError(L.t("Запусти Beacon от администратора или выбери режим Proxy", "Run Beacon as administrator or select Proxy mode")); return
        }

        val warp = state.warpCredentials
        if (state.warpEnabled && !hasUsableWarpCredentials()) {
            registerWarp(afterSuccess = { connect() }, reconnectOnSuccess = false)
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
                error?.let { showError(it.message ?: L.t("не удалось подключиться", "failed to connect")) }
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
        removeTrayIcon()
        runCatching { singBox.stop(); systemProxy.restore() }
        frame.dispose(); exitProcess(0)
    }

    private fun persist() = store.save(state)

    private fun refresh() {
        refreshing = true
        try {
            // Dynamic translation updates
            btnSubscriptions.toolTipText = L.t("Подписки", "Subscriptions")
            btnKeys.toolTipText = L.t("Управление ключами", "Key Management")
            btnSettings.toolTipText = L.t("Настройки", "Settings")

            pingLabel.text = L.t("пинг", "ping")
            downLabel.text = L.t("↓ вход", "↓ in")
            upLabel.text = L.t("↑ исх", "↑ out")

            pingTestBtn.text = L.t("Тест", "Test")
            pingTestBtn.toolTipText = L.t("Проверить задержку до активного сервера", "Check latency to the active server")

            if (::pingCard.isInitialized) {
                pingCard.toolTipText = L.t(
                    "<html>Задержка до сервера в миллисекундах.<br>Чем меньше — тем отзывчивее соединение.</html>",
                    "<html>Latency to the server in milliseconds.<br>Lower is more responsive.</html>"
                )
            }
            if (::downCard.isInitialized) {
                downCard.toolTipText = L.t(
                    "<html>Скорость загрузки прямо сейчас (входящий трафик).<br>Обновляется раз в секунду.</html>",
                    "<html>Current download speed (incoming traffic).<br>Updates every second.</html>"
                )
            }
            if (::upCard.isInitialized) {
                upCard.toolTipText = L.t(
                    "<html>Скорость отдачи прямо сейчас (исходящий трафик).<br>Обновляется раз в секунду.</html>",
                    "<html>Current upload speed (outgoing traffic).<br>Updates every second.</html>"
                )
            }

            proxyModeBtn.toolTipText = L.t(
                "<html><b>Proxy</b> — VPN только для браузера и приложений с поддержкой прокси.<br>" +
                "Работает без админских прав. Через 127.0.0.1:$PROXY_PORT.</html>",
                "<html><b>Proxy</b> — VPN only for browser and proxy-aware apps.<br>" +
                "Works without admin privileges. Via 127.0.0.1:$PROXY_PORT.</html>"
            )
            tunModeBtn.toolTipText = L.t(
                "<html><b>TUN</b> — VPN для всей системы, включая игры и любые приложения.<br>" +
                "Требует запуск от администратора.</html>",
                "<html><b>TUN</b> — VPN for the entire system, including games and all apps.<br>" +
                "Requires admin privileges.</html>"
            )
            warpModeBtn.toolTipText = L.t(
                "<html><b>WARP</b> — отдельный маршрут для Google / Gemini.<br>" +
                "Если Gemini не открывается через основной сервер, включи этот режим.</html>",
                "<html><b>WARP</b> — separate routing for Google / Gemini.<br>" +
                "Enable this if Gemini does not open via the main server.</html>"
            )

            when {
                registeringWarp -> {
                    hero.heroState = LighthouseHero.HeroState.CONNECTING
                    statusDot.foreground = T.WARN; statusText.foreground = T.WARN
                    statusText.text = L.t("Регистрация WARP…", "Registering WARP...")
                    mainBtn.text = L.t("Подождите", "Please wait")
                }
                connecting -> {
                    hero.heroState = LighthouseHero.HeroState.CONNECTING
                    statusDot.foreground = T.WARN; statusText.foreground = T.WARN
                    statusText.text = L.t("Подключение…", "Connecting...")
                    mainBtn.text = L.t("Отмена", "Cancel")
                }
                connected -> {
                    hero.heroState = LighthouseHero.HeroState.ON
                    statusDot.foreground = T.SUCCESS; statusText.foreground = T.SUCCESS
                    statusText.text = L.t("Подключено", "Connected")
                    mainBtn.text = L.t("Отключить", "Disconnect")
                }
                else -> {
                    hero.heroState = LighthouseHero.HeroState.OFF
                    statusDot.foreground = T.MUTED; statusText.foreground = T.TEXT_DIM
                    statusText.text = L.t("Отключено", "Disconnected")
                    mainBtn.text = L.t("Подключить", "Connect")
                }
            }
            mainBtn.isEnabled = !registeringWarp
            mainBtn.repaint()

            val active = state.activeProfile
            keyChip.text = if (active != null) {
                "  ${active.name}  ▾"
            } else L.t("  выбери ключ  ▾", "  select key  ▾")

            when (state.inboundMode) {
                InboundMode.Mixed -> proxyModeBtn.isSelected = true
                InboundMode.Tun -> tunModeBtn.isSelected = true
            }
            proxyModeBtn.repaint(); tunModeBtn.repaint()
            warpModeBtn.isSelected = state.warpEnabled
            warpModeBtn.text = if (registeringWarp) "..." else "WARP"
            warpModeBtn.isEnabled = !registeringWarp
            warpModeBtn.background = if (warpModeBtn.isSelected) T.ACCENT else T.BG_INPUT
            warpModeBtn.foreground = if (warpModeBtn.isSelected) Color.WHITE else T.MUTED
            pingTestBtn.isEnabled = active != null && !pinging
            warpModeBtn.repaint()
        } finally {
            refreshing = false
        }
        frame.contentPane.revalidate()
        frame.contentPane.repaint()
        syncTrayIcon()
    }

    private fun isTraySupported(): Boolean =
        runCatching { SystemTray.isSupported() }.getOrDefault(false)

    private fun hideToTray(): Boolean {
        if (!state.trayEnabled || !isTraySupported()) return false
        syncTrayIcon()
        if (trayIcon == null) return false
        frame.isVisible = false
        hero.pauseAnimation()
        if (!state.trayNoticeShown) {
            state = state.copy(trayNoticeShown = true)
            persist()
            trayIcon?.displayMessage(
                L.t("Скрыто в трее", "Hidden in Tray"),
                L.t("Beacon скрыт в трее. VPN продолжает работать. Настроить это поведение можно в настройках.",
                    "Beacon is hidden in the system tray. VPN continues running. This behavior can be configured in settings."),
                TrayIcon.MessageType.INFO
            )
        }
        return true
    }

    private fun restoreWindow() {
        frame.isVisible = true
        frame.extendedState = frame.extendedState and JFrame.ICONIFIED.inv()
        frame.toFront()
        frame.requestFocus()
        hero.resumeAnimation()
    }

    private fun syncTrayIcon() {
        if (!state.trayEnabled || !isTraySupported()) {
            removeTrayIcon()
            return
        }

        if (trayIcon == null) {
            val popup = PopupMenu()
            val openItem = MenuItem(L.t("Открыть", "Open")).apply {
                addActionListener { SwingUtilities.invokeLater { restoreWindow() } }
            }
            trayOpenItem = openItem
            trayToggleItem = MenuItem(trayToggleLabel()).apply {
                addActionListener { SwingUtilities.invokeLater { toggleConnect() } }
            }
            val exitItem = MenuItem(L.t("Выход", "Exit")).apply {
                addActionListener { SwingUtilities.invokeLater { shutdown() } }
            }
            trayExitItem = exitItem
            popup.add(openItem)
            popup.add(trayToggleItem)
            popup.addSeparator()
            popup.add(exitItem)

            val image = appIcon.getScaledInstance(16, 16, Image.SCALE_SMOOTH)
            val icon = TrayIcon(image, trayTooltip(), popup).apply {
                isImageAutoSize = true
                addActionListener { SwingUtilities.invokeLater { restoreWindow() } }
            }
            val added = runCatching { SystemTray.getSystemTray().add(icon) }.isSuccess
            if (added) trayIcon = icon
        }

        trayIcon?.toolTip = trayTooltip()
        trayToggleItem?.label = trayToggleLabel()
        trayToggleItem?.isEnabled = !registeringWarp
        trayOpenItem?.label = L.t("Открыть", "Open")
        trayExitItem?.label = L.t("Выход", "Exit")
    }

    private fun removeTrayIcon() {
        trayIcon?.let { icon ->
            runCatching { SystemTray.getSystemTray().remove(icon) }
        }
        trayIcon = null
        trayToggleItem = null
        trayOpenItem = null
        trayExitItem = null
    }

    private fun trayTooltip(): String {
        val status = when {
            registeringWarp -> L.t("регистрация WARP", "registering WARP")
            connecting -> L.t("подключение", "connecting")
            connected -> L.t("подключено", "connected")
            else -> L.t("отключено", "disconnected")
        }
        return "Beacon: $status"
    }

    private fun trayToggleLabel(): String =
        if (connected || connecting) L.t("Отключить", "Disconnect") else L.t("Подключить", "Connect")

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
        private var hoverProgress = 0f
        private val animator = HoverAnimator(this) { hoverProgress = it }

        init {
            isOpaque = false
            layout = BorderLayout()
            add(btn, BorderLayout.CENTER)
            maximumSize = Dimension(280, 32)
            preferredSize = Dimension(220, 32)
            btn.addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { animator.setTarget(1f) }
                override fun mouseExited(e: MouseEvent) { animator.setTarget(0f) }
            })
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Background color interpolation
            val bgStart = T.BG_INPUT
            val bgEnd = T.BG_INPUT_HOVER
            val r = bgStart.red + ((bgEnd.red - bgStart.red) * hoverProgress).toInt()
            val gDec = bgStart.green + ((bgEnd.green - bgStart.green) * hoverProgress).toInt()
            val b = bgStart.blue + ((bgEnd.blue - bgStart.blue) * hoverProgress).toInt()
            g2.color = Color(r, gDec, b)
            g2.fillRoundRect(0, 0, width, height, 18, 18)

            // Border color interpolation
            val borderStart = T.BORDER
            val borderEnd = T.ACCENT
            val br = borderStart.red + ((borderEnd.red - borderStart.red) * hoverProgress).toInt()
            val bgBorder = borderStart.green + ((borderEnd.green - borderStart.green) * hoverProgress).toInt()
            val bb = borderStart.blue + ((borderEnd.blue - borderStart.blue) * hoverProgress).toInt()
            g2.color = Color(br, bgBorder, bb)
            g2.stroke = BasicStroke(1f)
            g2.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
        }
    }

    private class AnimatedMenuItem(
        text: String,
        private val active: Boolean,
        private val muted: Boolean
    ) : JMenuItem(text) {
        private var hoverProgress = 0f
        private val animator = HoverAnimator(this, 130) { hoverProgress = it }

        init {
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            border = EmptyBorder(8, 14, 8, 18)
            foreground = when {
                active -> T.ACCENT_LIGHT
                muted -> T.MUTED
                else -> T.TEXT
            }
            background = T.CARD_SOLID
            font = font.deriveFont(if (active) Font.BOLD else Font.PLAIN, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { animator.setTarget(1f) }
                override fun mouseExited(e: MouseEvent) { animator.setTarget(0f) }
            })
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val bgAlpha = ((if (active) 28 else 0) + hoverProgress * 34).toInt().coerceIn(0, 255)
            if (bgAlpha > 0) {
                g2.color = Color(255, 255, 255, bgAlpha)
                g2.fillRoundRect(6, 4, width - 12, height - 8, 12, 12)
            }
            if (active) {
                g2.color = T.ACCENT_LIGHT
                g2.fillRoundRect(10, 10, 3, height - 20, 3, 3)
            }
            val base = when {
                active -> T.ACCENT_LIGHT
                muted -> T.MUTED
                else -> T.TEXT
            }
            val target = if (muted) T.TEXT_DIM else Color.WHITE
            val r = base.red + ((target.red - base.red) * hoverProgress).toInt()
            val gDec = base.green + ((target.green - base.green) * hoverProgress).toInt()
            val b = base.blue + ((target.blue - base.blue) * hoverProgress).toInt()
            foreground = Color(r, gDec, b)
            super.paintComponent(g)
        }
    }

    private inner class ModeSegment(
        private val a: JToggleButton,
        private val b: JToggleButton,
        onModeChange: (InboundMode) -> Unit
    ) : JPanel() {
        private var selectionX = 0f
        private var selectionTarget = 0f
        private val selectionAnimator = HoverAnimator(this, 180) { p ->
            val target = if (b.isSelected) 1f else 0f
            selectionX += (target - selectionX) * (0.22f + p * 0.18f)
        }
        private val progress = floatArrayOf(0f, 0f)
        private val animators = Array(2) { idx ->
            val btn = if (idx == 0) a else b
            HoverAnimator(this) { p ->
                progress[idx] = p
                val startColor = if (btn.isSelected) Color.WHITE else T.MUTED
                val endColor = if (btn.isSelected) Color.WHITE else T.TEXT
                val r = startColor.red + ((endColor.red - startColor.red) * p).toInt()
                val gDec = startColor.green + ((endColor.green - startColor.green) * p).toInt()
                val bDec = startColor.blue + ((endColor.blue - startColor.blue) * p).toInt()
                btn.foreground = Color(r, gDec, bDec)
            }
        }

        init {
            isOpaque = false
            layout = GridLayout(1, 2, 0, 0)
            preferredSize = Dimension(252, 38)
            maximumSize = Dimension(252, 38)
            minimumSize = Dimension(252, 38)
            ButtonGroup().apply { add(a); add(b) }
            styleToggle(a, 0)
            styleToggle(b, 1)
            add(a); add(b)
            a.addActionListener { if (a.isSelected) { refreshAll(); onModeChange(InboundMode.Mixed) } }
            b.addActionListener { if (b.isSelected) { refreshAll(); onModeChange(InboundMode.Tun) } }
            selectionX = if (b.isSelected) 1f else 0f
        }
        private fun styleToggle(t: JToggleButton, idx: Int) {
            t.isOpaque = false
            t.isContentAreaFilled = false
            t.isBorderPainted = false
            t.isFocusPainted = false
            t.foreground = T.MUTED
            t.font = t.font.deriveFont(Font.BOLD, 13f)
            t.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            t.addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { animators[idx].setTarget(1f) }
                override fun mouseExited(e: MouseEvent) { animators[idx].setTarget(0f) }
            })

            t.addChangeListener {
                val isSelected = t.isSelected
                val isHovered = animators[idx].progress > 0f
                if (!isHovered) {
                    t.foreground = if (isSelected) Color.WHITE else T.MUTED
                }
                val newTarget = if (b.isSelected) 1f else 0f
                if (selectionTarget != newTarget) {
                    selectionTarget = newTarget
                    selectionAnimator.setTarget(if (selectionTarget > 0f) 1f else 0f)
                }
            }
        }
        private fun refreshAll() { a.repaint(); b.repaint(); repaint() }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = T.BG_INPUT
            g2.fillRoundRect(0, 0, width, height, 20, 20)
            val w2 = width / 2

            for (i in 0..1) {
                val isSelected = if (i == 0) a.isSelected else b.isSelected
                if (!isSelected && progress[i] > 0f) {
                    val hx = i * w2
                    val alpha = (16 * progress[i]).toInt().coerceIn(0, 255)
                    g2.color = Color(255, 255, 255, alpha)
                    g2.fillRoundRect(hx + 3, 3, w2 - 6, height - 6, 14, 14)
                }
            }

            val modeX = (selectionX * w2).toInt()
            val pill = g2.create(modeX + 3, 3, w2 - 6, height - 6) as Graphics2D
            try { T.softFill(pill, w2 - 6, height - 6, T.ACCENT_HOVER, T.ACCENT, 14) }
            finally { pill.dispose() }

            g2.color = T.BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, 20, 20)
        }
    }

    private class WarpToggleFrame(private val btn: JToggleButton) : JPanel(BorderLayout()) {
        private var hoverProgress = 0f
        private val animator = HoverAnimator(this, 150) { hoverProgress = it }

        init {
            isOpaque = false
            preferredSize = Dimension(108, 38)
            maximumSize = Dimension(108, 38)
            minimumSize = Dimension(108, 38)
            btn.isOpaque = false
            btn.isContentAreaFilled = false
            btn.isBorderPainted = false
            btn.isFocusPainted = false
            btn.border = EmptyBorder(0, 12, 0, 12)
            btn.font = btn.font.deriveFont(Font.BOLD, 13f)
            btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(btn, BorderLayout.CENTER)
            btn.addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { animator.setTarget(1f) }
                override fun mouseExited(e: MouseEvent) { animator.setTarget(0f) }
            })
            btn.addChangeListener {
                btn.foreground = if (btn.isSelected) Color.WHITE else T.TEXT_DIM
                repaint()
            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val top = when {
                btn.isSelected -> T.ACCENT_HOVER
                hoverProgress > 0f -> T.BG_INPUT_HOVER
                else -> T.BG_INPUT
            }
            val bottom = if (btn.isSelected) T.ACCENT else T.BG_INPUT.darker()
            T.softFill(g2, width, height, top, bottom, 20)
            g2.color = if (btn.isSelected) T.ACCENT_LIGHT else Color(
                T.BORDER.red + ((T.ACCENT.red - T.BORDER.red) * hoverProgress).toInt(),
                T.BORDER.green + ((T.ACCENT.green - T.BORDER.green) * hoverProgress).toInt(),
                T.BORDER.blue + ((T.ACCENT.blue - T.BORDER.blue) * hoverProgress).toInt()
            )
            g2.stroke = BasicStroke(1f)
            g2.drawRoundRect(0, 0, width - 1, height - 1, 20, 20)
        }
    }

    companion object {
        const val PROXY_PORT = 2080
        const val CLASH_PORT = 9095
    }
}
