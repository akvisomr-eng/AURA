package com.aura.desktop

import com.aura.core.AuraGatewayConfig
import com.aura.core.AuraGatewayResult
import com.aura.core.AuraNineRouterAdapter
import com.aura.core.AuraRoutingMode
import com.aura.core.AuraRoutingRequest
import com.aura.core.AuraRuntime
import com.aura.core.AuraTaskType
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.Insets
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.PopupMenu
import java.awt.MenuItem
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.*

private class AuraDesktopWindow : JFrame("AURA — Work Companion") {
    private var trayIcon: TrayIcon? = null
    private var keepReady = true
    private val runtime = AuraRuntime()
    private val executor = Executors.newCachedThreadPool()
    private val transcript = JTextArea()
    private val input = JTextField()
    private val status = JLabel("AURA siap membantu pekerjaan Anda.")
    private val gatewayUrl = JTextField(System.getenv("AURA_GATEWAY_URL") ?: "")
    private val gatewayKey = JTextField(System.getenv("AURA_GATEWAY_KEY") ?: "")

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) { minimizeToTray() }
        })
        minimumSize = Dimension(900, 620)
        setSize(1100, 720)
        setLocationRelativeTo(null)
        transcript.isEditable = false
        transcript.lineWrap = true
        transcript.wrapStyleWord = true
        transcript.font = Font(Font.SANS_SERIF, Font.PLAIN, 15)

        val send = JButton("Kirim")
        send.addActionListener { sendMessage() }
        input.addActionListener { sendMessage() }

        val tools = JPanel(GridLayout(1, 3, 8, 8))
        val openFile = JButton("Buka Dokumen")
        val readFile = JButton("Baca File")
        val runtimeStatus = JButton("Status")
        val tray = JButton("Ke Tray")
        openFile.addActionListener { chooseFile(false) }
        readFile.addActionListener { chooseFile(true) }
        runtimeStatus.addActionListener { appendAura("Status: " + runtime.status()) }
        tray.addActionListener { minimizeToTray() }
        tools.add(openFile); tools.add(readFile); tools.add(runtimeStatus); tools.add(tray)

        val top = JPanel(BorderLayout(8, 8))
        top.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        top.add(JLabel("AURA Work Companion"), BorderLayout.WEST)
        top.add(status, BorderLayout.CENTER)
        top.add(tools, BorderLayout.EAST)

        val gateway = JPanel(GridLayout(2, 2, 6, 6))
        gateway.border = BorderFactory.createTitledBorder("AI Gateway (opsional)")
        gateway.add(JLabel("URL")); gateway.add(gatewayUrl)
        gateway.add(JLabel("API Key")); gateway.add(gatewayKey)

        val composer = JPanel(BorderLayout(8, 8))
        composer.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        input.margin = Insets(8, 8, 8, 8)
        composer.add(input, BorderLayout.CENTER); composer.add(send, BorderLayout.EAST)

        val bottom = JPanel(BorderLayout(8, 8))
        bottom.add(gateway, BorderLayout.CENTER); bottom.add(composer, BorderLayout.SOUTH)

        contentPane.layout = BorderLayout(8, 8)
        contentPane.add(top, BorderLayout.NORTH)
        contentPane.add(JScrollPane(transcript), BorderLayout.CENTER)
        contentPane.add(bottom, BorderLayout.SOUTH)

        appendAura("Halo. Saya AURA. Saya siap membantu pekerjaan Anda di Windows.")
        appendAura("Mode lokal aktif. Gateway dapat digunakan bila URL dikonfigurasi.")
        setupTray()
    }

    private fun setupTray() {
        if (!SystemTray.isSupported()) {
            appendAura("System tray tidak tersedia di lingkungan ini; AURA tetap dapat digunakan normal.")
            return
        }
        val menu = PopupMenu()
        val show = MenuItem("Buka AURA")
        val ready = MenuItem("Always-ready: AKTIF")
        val exit = MenuItem("Keluar AURA")
        show.addActionListener { restoreFromTray() }
        ready.addActionListener {
            keepReady = !keepReady
            ready.label = if (keepReady) "Always-ready: AKTIF" else "Always-ready: NONAKTIF"
        }
        exit.addActionListener {
            keepReady = false
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
            executor.shutdownNow()
            dispose()
            System.exit(0)
        }
        menu.add(show); menu.add(ready); menu.addSeparator(); menu.add(exit)
        val image = java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.drawString("A", 7, 23)
        g.dispose()
        trayIcon = TrayIcon(image, "AURA — Work Companion", menu).apply {
            isImageAutoSize = true
            addActionListener { restoreFromTray() }
        }
        runCatching { SystemTray.getSystemTray().add(trayIcon) }
    }

    private fun minimizeToTray() {
        if (keepReady && trayIcon != null) {
            isVisible = false
            trayIcon?.displayMessage("AURA", "AURA tetap aktif dan siap menerima pekerjaan.", TrayIcon.MessageType.INFO)
        } else {
            dispose()
        }
    }

    private fun restoreFromTray() {
        isVisible = true
        state = NORMAL
        toFront()
        requestFocus()
    }

    private fun sendMessage() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        input.text = ""
        appendUser(text)
        val plan = runtime.cognitivePlan(text)
        status.text = "Memproses context dan task..."
        executor.execute {
            val config = AuraGatewayConfig(gatewayUrl.text.trim(), gatewayKey.text.trim())
            if (!config.enabled) {
                val turn = runtime.respond(text, "")
                SwingUtilities.invokeLater { appendAura(turn.responseText); status.text = "AURA siap." }
                return@execute
            }
            val task = if (text.contains("kode", true) || text.contains("program", true)) AuraTaskType.CODING else AuraTaskType.CHAT
            val messages = mutableListOf<Pair<String, String>>()
            messages += "system" to "Anda adalah AURA, asisten kerja Windows berbahasa Indonesia yang natural, ringkas, akurat, dan aman."
            messages += "system" to "Context complexity=${plan.context.complexity}; privacySensitive=${plan.context.privacySensitive}."
            runtime.recentMemory(6).forEach { messages += it.role to it.text }
            messages += "user" to text

            AuraNineRouterAdapter(config).execute(
                AuraRoutingRequest(task = task, mode = AuraRoutingMode.BALANCED, estimatedInputTokens = text.length / 4, requiresIndonesian = true),
                messages
            ) { result ->
                SwingUtilities.invokeLater {
                    when (result) {
                        is AuraGatewayResult.Success -> {
                            runtime.rememberUser(text)
                            runtime.rememberAssistant(result.text)
                            appendAura(result.text)
                        }
                        is AuraGatewayResult.Failure -> appendAura("Gateway gagal: ${result.message}. Runtime lokal tetap tersedia.")
                    }
                    status.text = "AURA siap."
                }
            }
        }
    }

    private fun chooseFile(readContent: Boolean) {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile
        appendUser(if (readContent) "Baca file: ${file.name}" else "Pilih file: ${file.name}")
        if (!readContent) {
            appendAura("File ${file.name} siap diteruskan ke capability adapter.")
            return
        }
        val content = runCatching { Files.readString(file.toPath()).take(12000) }.getOrNull()
        if (content == null) appendAura("Format ${file.extension} belum memiliki document adapter aktif.")
        else appendAura("Isi awal ${file.name}:\n$content")
    }

    private fun appendUser(text: String) { transcript.append("\nAnda: $text\n") }
    private fun appendAura(text: String) { transcript.append("\nAURA: $text\n") }
}

fun main() { SwingUtilities.invokeLater { AuraDesktopWindow().isVisible = true } }
