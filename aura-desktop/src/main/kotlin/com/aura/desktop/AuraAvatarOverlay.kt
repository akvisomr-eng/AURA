package com.aura.desktop

import com.aura.core.affect.AuraAffectPolicy
import com.aura.core.affect.AuraMood
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Desktop-first AURA avatar inspired by the Surya Majapahit emblem.
 * The avatar stays visible while speech bubbles are transient. The expression
 * is an interface state, not a claim that the model experiences human feelings.
 */
class AuraAvatarOverlay(private val onAvatarClicked: () -> Unit) {
    private val window = JWindow()
    private val message = JLabel("Halo, saya AURA. Saya siap membantu Anda.")
    private val avatar = SuryaMajapahitAvatar()
    private var dragOrigin: Point? = null
    private var avatarSize = 104
    private var bubbleVisible = false
    private val hideBubbleTimer = Timer(9000) { hideBubble() }

    init {
        window.background = Color(0, 0, 0, 0)
        window.type = Window.Type.UTILITY
        window.isAlwaysOnTop = true
        window.layout = BorderLayout(0, 7)
        window.add(avatar, BorderLayout.CENTER)
        buildSpeechBubble()

        val drag = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragOrigin = e.point
                if (SwingUtilities.isRightMouseButton(e)) showAvatarMenu(e)
            }
            override fun mouseDragged(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) return
                val origin = dragOrigin ?: return
                val p = window.location
                window.location = Point(p.x + e.x - origin.x, p.y + e.y - origin.y)
            }
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) return
                if (e.clickCount == 2) onAvatarClicked()
                else if (e.clickCount == 1) onAvatarClicked()
            }
        }
        avatar.addMouseListener(drag)
        avatar.addMouseMotionListener(drag)

        applyAvatarSize()
        val screen = Toolkit.getDefaultToolkit().screenSize
        window.setLocation(screen.width - window.width - 22, screen.height - window.height - 70)
        avatar.startAnimation()
    }

    private fun buildSpeechBubble() {
        val bubble = JPanel(BorderLayout(8, 0))
        bubble.background = Color(13, 24, 39, 238)
        bubble.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(255, 183, 45, 190), 1, true),
            BorderFactory.createEmptyBorder(8, 11, 8, 11)
        )
        message.foreground = Color(245, 248, 255)
        message.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        bubble.add(message, BorderLayout.CENTER)
        val close = JButton("×")
        close.isFocusable = false
        close.toolTipText = "Tutup pesan, avatar tetap tampil"
        close.foreground = Color(240, 200, 120)
        close.background = Color(13, 24, 39)
        close.isBorderPainted = false
        close.addActionListener { hideBubble() }
        bubble.add(close, BorderLayout.EAST)
        bubble.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { dragOrigin = e.point }
            override fun mouseDragged(e: MouseEvent) {
                val origin = dragOrigin ?: return
                val p = window.location
                window.location = Point(p.x + e.x - origin.x, p.y + e.y - origin.y)
            }
        })
        window.add(bubble, BorderLayout.SOUTH)
        bubble.isVisible = false
    }

    private fun showAvatarMenu(event: MouseEvent) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("Buka AURA").apply { addActionListener { onAvatarClicked() } })
        menu.addSeparator()
        menu.add(JMenuItem("Avatar kecil (64 px)").apply { addActionListener { setAvatarSize(64) } })
        menu.add(JMenuItem("Avatar sedang (88 px)").apply { addActionListener { setAvatarSize(88) } })
        menu.add(JMenuItem("Avatar besar (120 px)").apply { addActionListener { setAvatarSize(120) } })
        menu.add(JMenuItem(if (bubbleVisible) "Sembunyikan pesan" else "Tampilkan pesan").apply {
            addActionListener { if (bubbleVisible) hideBubble() else showSpeech("Halo, saya AURA. Saya siap membantu Anda.", false) }
        })
        menu.add(JMenuItem("Sembunyikan avatar").apply { addActionListener { hide() } })
        menu.show(avatar, event.x, event.y)
    }

    fun setAvatarSize(size: Int) {
        avatarSize = size.coerceIn(48, 160)
        SwingUtilities.invokeLater {
            val old = window.location
            applyAvatarSize()
            window.setLocation(old)
        }
    }

    private fun applyAvatarSize() {
        avatar.preferredSize = Dimension(avatarSize, avatarSize)
        avatar.minimumSize = Dimension(avatarSize, avatarSize)
        avatar.maximumSize = Dimension(avatarSize, avatarSize)
        window.pack()
    }

    fun showSpeech(text: String, speaking: Boolean = true) {
        SwingUtilities.invokeLater {
            message.text = "<html><div style='width:245px'>${escapeHtml(text.take(180))}</div></html>"
            avatar.setMoodFromText(text)
            avatar.setSpeaking(speaking)
            showBubbleWindow()
            hideBubbleTimer.restart()
        }
    }

    private fun showBubbleWindow() {
        bubbleVisible = true
        window.contentPane.getComponent(1).isVisible = true
        window.pack()
        window.isVisible = true
    }

    private fun hideBubble() {
        hideBubbleTimer.stop()
        avatar.setSpeaking(false)
        bubbleVisible = false
        if (window.contentPane.componentCount > 1) window.contentPane.getComponent(1).isVisible = false
        window.pack()
        // Keep the avatar available on the desktop; never auto-hide it with the speech bubble.
        window.isVisible = true
    }

    fun show() { SwingUtilities.invokeLater { window.isVisible = true } }
    fun hide() {
        SwingUtilities.invokeLater {
            hideBubbleTimer.stop()
            avatar.setSpeaking(false)
            window.isVisible = false
        }
    }

    /** Call when the actual speech engine reports playback completion. */
    fun finishSpeaking() { SwingUtilities.invokeLater { avatar.setSpeaking(false) } }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private class SuryaMajapahitAvatar : JPanel() {
        private enum class Mood(val bright: Color, val deep: Color, val ray: Color) {
            JOY(Color(255, 235, 105), Color(255, 133, 35), Color(255, 199, 56)),
            CALM(Color(142, 231, 229), Color(43, 139, 190), Color(97, 220, 235)),
            CURIOUS(Color(188, 170, 255), Color(99, 81, 206), Color(171, 143, 255)),
            FOCUSED(Color(160, 190, 255), Color(61, 87, 183), Color(114, 157, 255)),
            EMPATHETIC(Color(255, 183, 205), Color(190, 83, 137), Color(255, 147, 190)),
            PLAYFULLY_ANNOYED(Color(255, 166, 119), Color(190, 65, 54), Color(255, 115, 89)),
            NEUTRAL(Color(255, 224, 106), Color(218, 94, 14), Color(255, 184, 45))
        }

        private var phase = 0.0
        private var speaking = false
        private var mood = Mood.NEUTRAL
        private var idleTicks = 0
        private val idleMoods = arrayOf(Mood.NEUTRAL, Mood.CALM, Mood.CURIOUS, Mood.JOY, Mood.FOCUSED)
        private val timer = Timer(70) {
            phase += if (speaking) 0.28 else 0.055
            if (!speaking && ++idleTicks >= 100) {
                idleTicks = 0
                mood = idleMoods[(mood.ordinal + 1 + (System.nanoTime().toInt().and(1))) % idleMoods.size]
            }
            repaint()
        }

        init {
            isOpaque = false
            preferredSize = Dimension(104, 104)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "AURA — klik untuk membuka, klik kanan untuk ukuran dan opsi"
        }

        fun startAnimation() { timer.start() }
        fun setSpeaking(value: Boolean) { speaking = value; if (value) idleTicks = 0; repaint() }

        fun setMoodFromText(text: String) {
            mood = when (AuraAffectPolicy.fromAssistantText(text).mood) {
                AuraMood.JOY, AuraMood.CELEBRATION -> Mood.JOY
                AuraMood.CALM, AuraMood.REST -> Mood.CALM
                AuraMood.CURIOUS -> Mood.CURIOUS
                AuraMood.FOCUSED, AuraMood.CONCERN -> Mood.FOCUSED
                AuraMood.EMPATHETIC -> Mood.EMPATHETIC
                AuraMood.PLAYFULLY_ANNOYED -> Mood.PLAYFULLY_ANNOYED
                AuraMood.NEUTRAL -> Mood.NEUTRAL
            }
            idleTicks = 0
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val side = minOf(width, height) - 10
            val cx = width / 2.0
            val cy = height / 2.0
            val radius = side * 0.29
            val pulse = (sin(phase) * 2.5).toInt()
            val glow = side * 0.10 + pulse
            g.color = Color(mood.ray.red, mood.ray.green, mood.ray.blue, 30)
            g.fillOval((cx - radius - glow).toInt(), (cy - radius - glow).toInt(),
                (2 * (radius + glow)).toInt(), (2 * (radius + glow)).toInt())

            // Surya Majapahit-inspired solar emblem: eight principal rays and eight
            // shorter diagonal rays, arranged around a central medallion.
            for (i in 0 until 16) {
                val angle = Math.PI * 2 * i / 16 + phase * 0.035
                val principal = i % 2 == 0
                val inner = radius * 0.90
                val outer = radius * if (principal) 1.48 else 1.25
                val x1 = cx + cos(angle) * inner
                val y1 = cy + sin(angle) * inner
                val x2 = cx + cos(angle) * outer
                val y2 = cy + sin(angle) * outer
                g.color = if (principal) mood.ray else mood.bright
                g.stroke = BasicStroke(if (principal) 3.4f else 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
            }

            val face = java.awt.geom.Ellipse2D.Double(cx - radius * .82, cy - radius * .82, radius * 1.64, radius * 1.64)
            g.paint = GradientPaint(0f, (cy - radius).toFloat(), mood.bright, 0f, (cy + radius).toFloat(), mood.deep)
            g.fill(face)
            g.color = Color(255, 245, 220, 220)
            g.stroke = BasicStroke(1.8f)
            g.draw(face)

            // A simple, warm expression remains secondary to the solar emblem.
            val eyeY = cy - radius * .12
            val eyeW = radius * .14
            val eyeH = radius * if (sin(phase * .45) > .985) .025 else .07
            g.color = Color(45, 31, 48)
            g.fillOval((cx - radius * .38).toInt(), (eyeY - eyeH / 2).toInt(), eyeW.toInt().coerceAtLeast(2), eyeH.toInt().coerceAtLeast(2))
            g.fillOval((cx + radius * .24).toInt(), (eyeY - eyeH / 2).toInt(), eyeW.toInt().coerceAtLeast(2), eyeH.toInt().coerceAtLeast(2))
            val mouthW = radius * .28
            val mouthH = if (speaking) radius * (.05 + .14 * (0.5 + 0.5 * sin(phase * 2.8))) else radius * .045
            g.color = Color(65, 34, 32)
            g.fillOval((cx - mouthW / 2).toInt(), (cy + radius * .25).toInt(), mouthW.toInt().coerceAtLeast(2), mouthH.toInt().coerceAtLeast(2))
            g.dispose()
        }
    }
}
