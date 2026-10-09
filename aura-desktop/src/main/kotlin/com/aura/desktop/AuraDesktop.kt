package com.aura.desktop

import com.aura.core.AuraGatewayConfig
import com.aura.core.AuraGatewayResult
import com.aura.core.AuraNineRouterAdapter
import com.aura.core.AuraRoutingMode
import com.aura.core.AuraRoutingRequest
import com.aura.core.AuraRuntime
import com.aura.core.AuraTaskType
import com.aura.core.vision.AuraCameraDevice
import com.aura.core.vision.AuraCameraManagerStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.Insets
import java.awt.image.BufferedImage
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.PopupMenu
import java.awt.MenuItem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import javax.swing.*

private class AuraDesktopWindow : JFrame("AURA — Asisten Kerja") {
    private var trayIcon: TrayIcon? = null
    private var keepReady = true
    private val runtime = AuraRuntime()
    private val cameraService = DesktopCameraService()
    private var cameraDevices: List<AuraCameraDevice> = emptyList()
    private var cameraPreviewWindow: JFrame? = null
    private var cameraPreviewLabel: JLabel? = null
    private var cameraPreviewTimer: javax.swing.Timer? = null
    private val cameraFrameInFlight = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private val transcript = JTextArea()
    private val input = JTextField()
    private val status = JLabel("AURA siap membantu pekerjaan Anda.")
    private val gatewayUrl = JTextField(System.getenv("AURA_GATEWAY_URL") ?: "")
    private val gatewayKey = JTextField(System.getenv("AURA_GATEWAY_KEY") ?: "")
    private val desktop = DesktopCapabilities()
    private val screen = ScreenIntelligence()
    private val project = ProjectIntelligence()
    private val approvals = ApprovalEngine()
    private val workspaceState = WorkspaceState()
    private val avatarOverlay = AuraAvatarOverlay { handleAvatarClick() }
    private var workspace: Path? = null
    private val speechListener = ContinuousSpeechListener(
        apiKeyProvider = { SpeechCredentialStore.apiKey() },
        onText = { recognized ->
            SwingUtilities.invokeLater {
                input.text = recognized
                status.text = "Ucapan dikenali; meneruskan ke AURA..."
                sendMessage()
            }
        },
        onStatus = { message -> SwingUtilities.invokeLater { status.text = message } }
    )

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

        val tools = JPanel(GridLayout(0, 4, 8, 8))
        val openFile = JButton("Buka Dokumen")
        val readFile = JButton("Baca File")
        val runtimeStatus = JButton("Status")
        val tray = JButton("Ke Tray")
        val workspaceButton = JButton("Workspace")
        val gitButton = JButton("Git Status")
        val clipboardButton = JButton("Clipboard")
        val openWorkspace = JButton("Buka Workspace")
        val projectButton = JButton("Analisis Project")
        val screenButton = JButton("Tangkapan Layar")
        val listenButton = JButton("Mulai Dengarkan")
        val stopListenButton = JButton("Hentikan Dengarkan")
        val voiceSettingsButton = JButton("Atur Suara")
        listenButton.toolTipText = "Aktifkan mikrofon; segmen ucapan dikirim ke cloud jika AURA_SPEECH_API_KEY tersedia."
        listenButton.addActionListener { speechListener.start() }
        stopListenButton.addActionListener { speechListener.stop() }
        voiceSettingsButton.toolTipText = "Masukkan API key untuk mengaktifkan transkripsi kata pemicu dan fallback suara Indonesia."
        voiceSettingsButton.addActionListener { configureSpeech() }
        val approvalButton = JButton("Approval")
        val launchButton = JButton("Buka Aplikasi")
        val searchButton = JButton("Cari Workspace")
        val diffButton = JButton("Git Diff")
        val commandButton = JButton("Jalankan Task")
        val copyButton = JButton("Salin Ringkasan")
        val fileSearchButton = JButton("Cari File")
        val readDocButton = JButton("Baca Dokumen")
        val buildButton = JButton("Build/Test Project")
        val structureButton = JButton("Struktur Project")
        val scanCameraButton = JButton("Pindai Kamera")
        val startCameraButton = JButton("Mulai Kamera")
        val stopCameraButton = JButton("Hentikan Kamera")
        openFile.addActionListener { chooseFile(false) }
        readFile.addActionListener { chooseFile(true) }
        runtimeStatus.addActionListener {
            appendAura("Status: " + runtime.status())
            val camera = cameraService.availability()
            appendAura("AURA Vision: " + camera.message)
        }
        tray.addActionListener { minimizeToTray() }
        workspaceButton.addActionListener { chooseWorkspace() }
        gitButton.addActionListener { showGitStatus() }
        clipboardButton.addActionListener { showClipboard() }
        openWorkspace.addActionListener { workspace?.let { desktop.openPath(it) } ?: appendAura("Pilih workspace terlebih dahulu.") }
        projectButton.addActionListener { analyzeProject() }
        screenButton.addActionListener { captureScreen() }
        approvalButton.addActionListener { showApproval() }
        launchButton.addActionListener { launchApplication() }
        searchButton.addActionListener { searchWorkspace() }
        diffButton.addActionListener { showGitDiff() }
        commandButton.addActionListener { runTaskWithApproval() }
        copyButton.addActionListener { desktop.copyToClipboard(transcript.text); appendAura("Ringkasan percakapan disalin ke clipboard.") }
        fileSearchButton.addActionListener { searchFiles() }
        readDocButton.addActionListener { readDocument() }
        buildButton.addActionListener { runRecommendedBuild() }
        structureButton.addActionListener { showProjectStructure() }
        scanCameraButton.addActionListener { scanCameras() }
        startCameraButton.addActionListener { startCamera() }
        stopCameraButton.addActionListener { stopCamera() }
        tools.add(openFile); tools.add(readFile); tools.add(runtimeStatus); tools.add(tray)
        tools.add(workspaceButton); tools.add(gitButton); tools.add(clipboardButton); tools.add(openWorkspace)
        tools.add(projectButton); tools.add(screenButton); tools.add(approvalButton); tools.add(launchButton)
        tools.add(searchButton); tools.add(diffButton); tools.add(commandButton); tools.add(copyButton)
        tools.add(fileSearchButton); tools.add(readDocButton); tools.add(buildButton); tools.add(structureButton)
        tools.add(scanCameraButton); tools.add(startCameraButton); tools.add(stopCameraButton)
        tools.add(listenButton); tools.add(stopListenButton); tools.add(voiceSettingsButton)

        val top = JPanel(BorderLayout(8, 8))
        top.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        top.add(JLabel("AURA — Asisten Kerja"), BorderLayout.WEST)
        top.add(status, BorderLayout.CENTER)
        top.add(tools, BorderLayout.EAST)

        val gateway = JPanel(GridLayout(2, 2, 6, 6))
        gateway.border = BorderFactory.createTitledBorder("Gerbang AI (opsional)")
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
        restoreWorkspace()
        setupTray()
        avatarOverlay.show()
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
            speechListener.stop()
            executor.shutdownNow()
            dispose()
            System.exit(0)
        }
        menu.add(show); menu.add(ready); menu.addSeparator(); menu.add(exit)
        val image = java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        val cx = 16.0
        val cy = 16.0
        for (i in 0 until 16) {
            val angle = Math.PI * 2 * i / 16
            val inner = if (i % 2 == 0) 8.0 else 9.0
            val outer = if (i % 2 == 0) 15.0 else 12.5
            g.color = if (i % 2 == 0) java.awt.Color(255, 183, 45) else java.awt.Color(255, 224, 106)
            g.stroke = java.awt.BasicStroke(if (i % 2 == 0) 2.0f else 1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
            g.drawLine((cx + kotlin.math.cos(angle) * inner).toInt(), (cy + kotlin.math.sin(angle) * inner).toInt(),
                (cx + kotlin.math.cos(angle) * outer).toInt(), (cy + kotlin.math.sin(angle) * outer).toInt())
        }
        g.color = java.awt.Color(218, 94, 14)
        g.fillOval(8, 8, 16, 16)
        g.color = java.awt.Color(255, 245, 220)
        g.drawOval(8, 8, 16, 16)
        g.dispose()
        trayIcon = TrayIcon(image, "AURA — Asisten Kerja", menu).apply {
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

    private fun analyzeProject() {
        val root = workspace ?: run { appendAura("Pilih workspace terlebih dahulu."); return }
        executor.execute {
            val result = runCatching { project.profile(root) }
            SwingUtilities.invokeLater {
                result.onSuccess { p -> appendAura("Project Intelligence:\nJenis: ${p.kind}\nSource files: ${p.sourceFiles}\nTest files: ${p.testFiles}\nBuild: ${p.buildFiles.joinToString(", ").ifBlank { "tidak terdeteksi" }}") }
                    .onFailure { appendAura("Analisis project gagal: ${it.message}") }
            }
        }
    }

    private fun captureScreen() {
        executor.execute {
            val result = runCatching { screen.capturePrimary() }
            SwingUtilities.invokeLater {
                result.onSuccess { s -> appendAura("Screen Intelligence aktif: ${s.width}x${s.height}, ${s.monitorCount} monitor. Snapshot berhasil diambil untuk tahap computer vision berikutnya.") }
                    .onFailure { appendAura("Screen capture tidak tersedia: ${it.message}") }
            }
        }
    }

    private fun showApproval() {
        val request = approvals.classify("jalankan tindakan dari AURA")
        appendAura("Approval Engine: ${request.risk}\n${request.description}")
    }

    private fun launchApplication() {
        val command = JOptionPane.showInputDialog(this, "Nama executable/perintah aplikasi:", "Buka Aplikasi", JOptionPane.PLAIN_MESSAGE) ?: return
        if (command.isBlank()) return
        val request = approvals.classify("jalankan $command")
        if (request.risk != ApprovalRisk.READ_ONLY) {
            val approved = JOptionPane.showConfirmDialog(this, "AURA meminta izin menjalankan: $command", "Persetujuan", JOptionPane.YES_NO_OPTION)
            if (approved != JOptionPane.YES_OPTION) { appendAura("Tindakan dibatalkan."); return }
        }
        appendAura(if (desktop.openApplication(command)) "Aplikasi dijalankan: $command" else "Gagal menjalankan: $command")
    }

    private fun restoreWorkspace() {
        val saved = workspaceState.load()?.let { Path.of(it) }
        if (saved != null && Files.isDirectory(saved)) {
            workspace = saved
            appendAura("Workspace terakhir dipulihkan: $saved")
        } else if (saved != null) {
            workspaceState.clear()
        }
    }

    private fun chooseWorkspace() {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        workspace = chooser.selectedFile.toPath()
        workspaceState.save(workspace!!.toAbsolutePath().toString())
        val entries = runCatching { desktop.inspectFolder(workspace!!) }.getOrNull()
        if (entries == null) appendAura("Workspace tidak dapat dibaca.")
        else appendAura("Workspace aktif: ${workspace}\n${entries.joinToString("\n") { (if (it.directory) "[DIR] " else "[FILE] ") + it.name }}")
    }

    private fun showGitStatus() {
        val root = workspace ?: run { appendAura("Pilih workspace Git terlebih dahulu."); return }
        executor.execute {
            val result = runCatching { desktop.gitStatus(root) }
            SwingUtilities.invokeLater {
                result.onSuccess { s -> appendAura("Git workspace: ${s.root}\nBranch: ${s.branch}\nStatus:\n${s.status}\nCommit terakhir:\n${s.recentCommits.joinToString("\n")}") }
                    .onFailure { appendAura("Git belum tersedia di workspace: ${it.message}") }
            }
        }
    }

    private fun searchFiles() {
        val root = workspace ?: run { appendAura("Pilih workspace terlebih dahulu."); return }
        val query = JOptionPane.showInputDialog(this, "Nama file yang dicari:", "File Intelligence", JOptionPane.PLAIN_MESSAGE) ?: return
        if (query.isBlank()) return
        executor.execute {
            val result = runCatching { desktop.findFiles(root, query) }
            SwingUtilities.invokeLater {
                result.onSuccess { files ->
                    if (files.isEmpty()) appendAura("File tidak ditemukan: $query")
                    else appendAura("File ditemukan:\\n" + files.joinToString("\\n") { "${it.path} (${it.sizeBytes} B)" })
                }.onFailure { appendAura("Pencarian file gagal: ${it.message}") }
            }
        }
    }

    private fun readDocument() {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile.toPath()
        val result = runCatching { desktop.readDocument(file) }
        result.onSuccess { doc -> appendAura("Dokumen: ${doc.name}\n${doc.content}") }
            .onFailure { appendAura("Dokumen belum didukung: ${it.message}") }
    }

    private fun showProjectStructure() {
        val root = workspace ?: run { appendAura("Pilih workspace terlebih dahulu."); return }
        executor.execute {
            val result = runCatching { project.profile(root) }
            SwingUtilities.invokeLater {
                result.onSuccess { p -> appendAura("Struktur Project:\\nJenis: ${p.kind}\nFolder utama: ${p.keyDirectories.joinToString(", ").ifBlank { "tidak ada" }}") }
                    .onFailure { appendAura("Struktur project gagal: ${it.message}") }
            }
        }
    }

    private fun runRecommendedBuild() {
        val root = workspace ?: run { appendAura("Pilih workspace terlebih dahulu."); return }
        val build = runCatching { project.recommendedBuild(root) }.getOrElse {
            appendAura("Build Intelligence gagal: ${it.message}"); return
        }
        if (build.command.isEmpty()) {
            appendAura(build.rationale); return
        }
        val commandText = build.command.joinToString(" ")
        val request = approvals.classify("jalankan build test $commandText")
        val approved = JOptionPane.showConfirmDialog(this, "AURA mendeteksi:\n$commandText\n\nAlasan: ${build.rationale}\n\nJalankan?", "Build/Test Approval", JOptionPane.YES_NO_OPTION)
        if (approved != JOptionPane.YES_OPTION) { appendAura("Build/Test dibatalkan."); return }
        status.text = "Build/Test berjalan..."
        executor.execute {
            val result = runCatching { desktop.runApprovedCommand(root, *build.command.toTypedArray()) }
            SwingUtilities.invokeLater {
                result.onSuccess { r ->
                    val state = if (r.exitCode == 0) "BERHASIL" else "GAGAL"
                    appendAura("Build Intelligence — $state (exit ${r.exitCode})\\n${r.output}")
                    status.text = "AURA siap."
                }.onFailure { appendAura("Build/Test gagal dijalankan: ${it.message}"); status.text = "AURA siap." }
            }
        }
    }

    private fun searchWorkspace() {
        val root = workspace ?: run { appendAura("Pilih workspace terlebih dahulu."); return }
        val query = JOptionPane.showInputDialog(this, "Cari teks di workspace:", "Workspace Search", JOptionPane.PLAIN_MESSAGE) ?: return
        if (query.isBlank()) return
        executor.execute {
            val result = runCatching { desktop.searchWorkspace(root, query) }
            SwingUtilities.invokeLater {
                result.onSuccess { hits ->
                    if (hits.isEmpty()) appendAura("Tidak ditemukan: $query")
                    else appendAura("Hasil pencarian $query:\\n" + hits.joinToString("\\n") { "${it.path}:${it.line} — ${it.preview}" })
                }.onFailure { appendAura("Pencarian gagal: ${it.message}") }
            }
        }
    }

    private fun showGitDiff() {
        val root = workspace ?: run { appendAura("Pilih workspace Git terlebih dahulu."); return }
        executor.execute {
            val result = runCatching { desktop.gitDiff(root) }
            SwingUtilities.invokeLater {
                result.onSuccess { diff -> appendAura("Git Diff Summary:\\n$diff") }
                    .onFailure { appendAura("Git diff gagal: ${it.message}") }
            }
        }
    }

    private fun runTaskWithApproval() {
        val root = workspace ?: run { appendAura("Pilih workspace terlebih dahulu."); return }
        val command = JOptionPane.showInputDialog(this, "Task yang akan dijalankan (contoh: gradle test):", "Jalankan Task", JOptionPane.PLAIN_MESSAGE) ?: return
        if (command.isBlank()) return
        val request = approvals.classify("jalankan $command")
        if (request.risk != ApprovalRisk.READ_ONLY) {
            val approved = JOptionPane.showConfirmDialog(this, "AURA meminta izin menjalankan task:\\n$command", "Persetujuan Eksekusi", JOptionPane.YES_NO_OPTION)
            if (approved != JOptionPane.YES_OPTION) { appendAura("Task dibatalkan."); return }
        }
        val args = Regex("""[^\\s"']+|"[^"]*"|'[^']*'""")
            .findAll(command)
            .map { it.value.trim('"', '\'') }
            .toList()
        if (args.isEmpty()) return
        status.text = "Menjalankan task..."
        executor.execute {
            val result = runCatching { desktop.runApprovedCommand(root, *args.toTypedArray()) }
            SwingUtilities.invokeLater {
                result.onSuccess { r -> appendAura("Task selesai (exit ${r.exitCode}):\\n${r.output}"); status.text = "AURA siap." }
                    .onFailure { appendAura("Task gagal dijalankan: ${it.message}"); status.text = "AURA siap." }
            }
        }
    }

    private fun scanCameras() {
        val availability = cameraService.availability()
        if (!availability.available) {
            appendAura("AURA Vision: ${availability.message}")
            return
        }
        status.text = "Memindai kamera..."
        executor.execute {
            val result = runCatching { runBlocking { cameraService.refreshDevices() } }
            SwingUtilities.invokeLater {
                result.onSuccess { state ->
                    cameraDevices = state?.devices.orEmpty()
                    if (cameraDevices.isEmpty()) {
                        appendAura("AURA Vision: tidak ada kamera ditemukan. Pastikan webcam tersambung dan coba pindai lagi.")
                    } else {
                        appendAura("AURA Vision: ditemukan ${cameraDevices.size} kamera:\n" +
                            cameraDevices.joinToString("\n") { "• ${it.displayName} [${it.id}]" })
                    }
                }.onFailure {
                    appendAura("Pemindaian kamera gagal: ${it.message}")
                }
                status.text = "AURA siap."
            }
        }
    }

    private fun startCamera() {
        if (cameraDevices.isEmpty()) {
            appendAura("Pindai kamera terlebih dahulu sebelum memulai.")
            return
        }
        val options = cameraDevices.map { "${it.displayName} — ${it.id}" }.toTypedArray()
        val selected = JOptionPane.showInputDialog(
            this, "Pilih kamera yang ingin diaktifkan:", "AURA Vision",
            JOptionPane.PLAIN_MESSAGE, null, options, options.firstOrNull()
        ) as? String ?: return
        val device = cameraDevices[options.indexOf(selected)]
        status.text = "Mengaktifkan kamera..."
        executor.execute {
            val result = runCatching { runBlocking { cameraService.start(device.id, userInitiated = true) } }
            SwingUtilities.invokeLater {
                result.onSuccess { state ->
                    if (state?.status == AuraCameraManagerStatus.RUNNING) {
                        appendAura("Kamera aktif: ${device.displayName}. Indikator preview AURA Vision dibuka.")
                        showCameraPreview()
                    } else {
                        appendAura("Kamera tidak dapat dimulai: ${state?.lastError ?: state?.status ?: "backend tidak tersedia"}")
                    }
                }.onFailure {
                    appendAura("Gagal memulai kamera: ${it.message}")
                }
                status.text = "AURA siap."
            }
        }
    }

    private fun stopCamera() {
        cameraPreviewTimer?.stop()
        cameraPreviewTimer = null
        cameraPreviewWindow?.dispose()
        cameraPreviewWindow = null
        cameraPreviewLabel = null
        executor.execute {
            val result = runCatching { runBlocking { cameraService.stop() } }
            SwingUtilities.invokeLater {
                result.onSuccess { appendAura("AURA Vision: kamera dihentikan dan perangkat dilepas.") }
                    .onFailure { appendAura("Gagal menghentikan kamera: ${it.message}") }
                status.text = "AURA siap."
            }
        }
    }

    private fun showCameraPreview() {
        cameraPreviewTimer?.stop()
        cameraPreviewLabel = JLabel("Menghubungkan preview kamera...", SwingConstants.CENTER)
        val preview = JFrame("AURA Vision — Live Camera")
        preview.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        preview.minimumSize = Dimension(480, 360)
        preview.setSize(800, 600)
        preview.setLocationRelativeTo(this)
        preview.add(cameraPreviewLabel)
        preview.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent) {
                cameraPreviewTimer?.stop()
                cameraPreviewTimer = null
                cameraPreviewWindow = null
                cameraPreviewLabel = null
                executor.execute { runCatching { runBlocking { cameraService.stop() } } }
            }
        })
        cameraPreviewWindow = preview
        preview.isVisible = true

        cameraPreviewTimer = javax.swing.Timer(150) {
            if (!cameraFrameInFlight.compareAndSet(false, true)) return@Timer
            executor.execute {
                val frame = runCatching { runBlocking { cameraService.captureFrame() } }.getOrNull()
                SwingUtilities.invokeLater {
                    try {
                        val label = cameraPreviewLabel
                        val image = frame?.platformImage as? BufferedImage
                        if (label != null && image != null && label.width > 0 && label.height > 0) {
                            val scale = minOf(
                                label.width.toDouble() / image.width,
                                label.height.toDouble() / image.height
                            )
                            val width = (image.width * scale).toInt().coerceAtLeast(1)
                            val height = (image.height * scale).toInt().coerceAtLeast(1)
                            label.icon = ImageIcon(image.getScaledInstance(width, height, java.awt.Image.SCALE_FAST))
                            label.text = ""
                        } else if (label != null && label.icon == null) {
                            // Keep the preview window informative when the backend starts
                            // successfully but the first frame is delayed or unavailable.
                            val state = cameraService.state()
                            label.text = if (state?.status == AuraCameraManagerStatus.ERROR) {
                                "Kamera terputus atau frame gagal. Hentikan lalu pindai ulang."
                            } else {
                                "Menunggu frame dari kamera… Pastikan perangkat tersambung dan tidak dipakai aplikasi lain."
                            }
                        }
                    } finally {
                        cameraFrameInFlight.set(false)
                    }
                }
            }
        }.apply { start() }
    }

    private fun showClipboard() {
        val text = desktop.readClipboard()
        if (text.isNullOrBlank()) appendAura("Clipboard kosong atau bukan teks.")
        else appendAura("Clipboard:\n${text.take(8000)}")
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

    /** Clicking the desktop avatar arms a voice turn without opening the full module. */
    private fun handleAvatarClick() {
        SwingUtilities.invokeLater {
            if (SpeechCredentialStore.apiKey().isNullOrBlank()) {
                // First use requires an explicit provider key; make setup available from the avatar.
                restoreFromTray()
                configureSpeech()
            }
            if (speechListener.beginConversation()) {
                avatarOverlay.showSpeech("Silakan bicara. AURA sedang mendengarkan.", speaking = false)
            } else {
                avatarOverlay.showSpeech(
                    "Suara belum aktif. Periksa API key dan mikrofon melalui Atur Suara.",
                    speaking = false
                )
            }
        }
    }

    private fun configureSpeech() {
        val field = JPasswordField(32)
        val currentKey = SpeechCredentialStore.apiKey()
        if (!currentKey.isNullOrBlank()) field.text = currentKey
        val panel = JPanel(BorderLayout(8, 8))
        panel.add(JLabel("<html><b>Aktifkan suara AURA</b><br>Masukkan API key Anda untuk transkripsi kata pemicu dan suara Indonesia.<br>Audio ucapan akan dikirim ke layanan transkripsi cloud saat siaga aktif.</html>"), BorderLayout.NORTH)
        panel.add(field, BorderLayout.CENTER)
        panel.add(JLabel("Kunci yang dimasukkan di sini hanya disimpan dalam memori sampai AURA ditutup."), BorderLayout.SOUTH)
        val choice = JOptionPane.showConfirmDialog(this, panel, "Pengaturan Suara AURA", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (choice != JOptionPane.OK_OPTION) return
        val key = String(field.password).trim()
        if (key.isBlank()) {
            SpeechCredentialStore.clearSessionKey()
            speechListener.stop()
            status.text = "Siaga suara nonaktif: masukkan API key melalui Atur Suara."
            appendAura("Siaga suara dimatikan. Tidak ada audio yang dikirim tanpa API key.")
            return
        }
        SpeechCredentialStore.setSessionKey(key)
        speechListener.stop()
        speechListener.start()
    }

    private fun appendUser(text: String) { transcript.append("\nAnda: $text\n") }
    private fun appendAura(text: String) {
        transcript.append("\nAURA: $text\n")
        avatarOverlay.showSpeech(text, speaking = true)
        // Utamakan suara Indonesia. Jika tidak tersedia, jangan membacakan bahasa Indonesia dengan suara Inggris.
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            executor.execute {
                val safeText = text.replace("'", "''").replace(96.toChar(), ' ').replace("\r", " ").replace("\n", " ").take(1200)
                val script = """
                    Add-Type -AssemblyName System.Speech
                    ${'$'}v = New-Object System.Speech.Synthesis.SpeechSynthesizer
                    try {
                      ${'$'}voice = ${'$'}v.GetInstalledVoices() | Where-Object { ${'$'}_.Enabled -and ${'$'}_.VoiceInfo.Culture.Name -eq 'id-ID' } | Select-Object -First 1
                      if (${'$'}null -eq ${'$'}voice) {
                        ${'$'}key = [Environment]::GetEnvironmentVariable('AURA_SPEECH_API_KEY')
                        if ([string]::IsNullOrWhiteSpace(${'$'}key)) {
                          [Console]::Error.WriteLine('VOICE_ID_ID_NOT_INSTALLED_AND_NO_SPEECH_API_KEY')
                          exit 23
                        }
                        ${'$'}wavPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), ('aura-tts-' + [guid]::NewGuid().ToString() + '.wav'))
                        try {
                          ${'$'}payload = @{ model = 'gpt-4o-mini-tts'; voice = 'coral'; input = '$safeText'; instructions = 'Berbicaralah dalam bahasa Indonesia yang alami, jelas, hangat, dengan pelafalan dan intonasi penutur bahasa Indonesia.'; response_format = 'wav' } | ConvertTo-Json -Compress
                          Invoke-WebRequest -UseBasicParsing -Uri 'https://api.openai.com/v1/audio/speech' -Method Post -Headers @{ Authorization = ('Bearer ' + ${'$'}key) } -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes(${'$'}payload)) -OutFile ${'$'}wavPath
                          ${'$'}player = New-Object System.Media.SoundPlayer(${'$'}wavPath)
                          ${'$'}player.PlaySync()
                          ${'$'}player.Dispose()
                        } finally {
                          Remove-Item -LiteralPath ${'$'}wavPath -Force -ErrorAction SilentlyContinue
                        }
                      } else {
                        ${'$'}v.SelectVoice(${'$'}voice.VoiceInfo.Name)
                        ${'$'}v.Speak('$safeText')
                      }
                    } finally { ${'$'}v.Dispose() }
                """.trimIndent()
                runCatching {
                    val processBuilder = ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
                        .redirectErrorStream(true)
                    SpeechCredentialStore.apiKey()?.let { processBuilder.environment()["AURA_SPEECH_API_KEY"] = it }
                    val process = processBuilder.start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (exitCode == 23 || output.contains("VOICE_ID_ID_NOT_INSTALLED")) {
                        SwingUtilities.invokeLater {
                            status.text = "Suara Indonesia belum terpasang"
                            avatarOverlay.showSpeech("AURA memerlukan suara teks-ke-ucapan bahasa Indonesia. Tambahkan paket suara Indonesia di pengaturan Windows.", speaking = false)
                        }
                    }
                }
            }
        }
    }
}

fun main() { SwingUtilities.invokeLater { AuraDesktopWindow() } }
