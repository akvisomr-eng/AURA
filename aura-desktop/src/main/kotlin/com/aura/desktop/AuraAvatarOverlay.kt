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
 * Ambient Surya Majapahit-inspired avatar. Mood is an expressive UI state,
 * not a claim that the model experiences human feelings.
 */
class AuraAvatarOverlay(private val onAvatarClicked: () -> Unit) {
    private val window = JWindow()
    private val message = JLabel("Halo, saya AURA. Saya siap membantu Anda.")
    private val avatar = SunAvatar()
    private var dragOrigin: Point? = null
    private val hideTimer = Timer(9000) { avatar.setSpeaking(false); window.isVisible = false }

    init {
        window.background = Color(0, 0, 0, 0)
        window.type = Window.Type.POPUP
        window.isAlwaysOnTop = true
        window.layout = BorderLayout(0, 8)
        window.add(avatar, BorderLayout.CENTER)

        val bubble = JPanel(BorderLayout(8, 0))
        bubble.background = Color(13, 24, 39, 235)
        bubble.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(255, 183, 45, 190), 1, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        )
        message.foreground = Color(245, 248, 255)
        message.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        bubble.add(message, BorderLayout.CENTER)
        val close = JButton("×")
        close.isFocusable = false
        close.toolTipText = "Sembunyikan avatar"
        close.foreground = Color(240, 200, 120)
        close.background = Color(13, 24, 39)
        close.isBorderPainted = false
        close.addActionListener { hideTimer.stop(); avatar.setSpeaking(false); window.isVisible = false }
        bubble.add(close, BorderLayout.EAST)
        window.add(bubble, BorderLayout.SOUTH)

        val drag = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { dragOrigin = e.point }
            override fun mouseDragged(e: MouseEvent) {
                val origin = dragOrigin ?: return
                val p = window.location
                window.location = Point(p.x + e.x - origin.x, p.y + e.y - origin.y)
            }
            override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 1) onAvatarClicked() }
        }
        avatar.addMouseListener(drag)
        avatar.addMouseMotionListener(drag)
        bubble.addMouseListener(drag)
        bubble.addMouseMotionListener(drag)

        window.setSize(330, 310)
        val screen = Toolkit.getDefaultToolkit().screenSize
        window.setLocation(screen.width - window.width - 28, screen.height - window.height - 75)
        avatar.startAnimation()
    }

    fun showSpeech(text: String, speaking: Boolean = true) {
        SwingUtilities.invokeLater {
            message.text = "<html><div style='width:245px'>${escapeHtml(text.take(180))}</div></html>"
            avatar.setMoodFromText(text)
            avatar.setSpeaking(speaking)
            window.isVisible = true
            hideTimer.restart()
        }
    }

    fun show() { SwingUtilities.invokeLater { window.isVisible = true } }
    fun hide() { SwingUtilities.invokeLater { hideTimer.stop(); avatar.setSpeaking(false); window.isVisible = false } }

    /** Call when the actual speech engine reports playback completion. */
    fun finishSpeaking() { SwingUtilities.invokeLater { avatar.setSpeaking(false) } }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private class SunAvatar : JPanel() {
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
            if (!speaking) {
                idleTicks++
                if (idleTicks >= 100) {
                    idleTicks = 0
                    // Gentle ambient color breathing; avoid abrupt random flicker.
                    mood = idleMoods[(mood.ordinal + 1 + (System.nanoTime().toInt().and(1))) % idleMoods.size]
                }
            }
            repaint()
        }

        init {
            isOpaque = false
            preferredSize = Dimension(220, 220)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
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
            val side = minOf(width, height) - 18
            val cx = width / 2.0
            val cy = height / 2.0
            val radius = side * 0.34
            val palette = mood
            val pulse = (sin(phase) * 4).toInt()
            val glow = 22 + pulse
            g.color = Color(palette.ray.red, palette.ray.green, palette.ray.blue, 35)
            g.fillOval((cx - radius - glow).toInt(), (cy - radius - glow).toInt(),
                (2 * (radius + glow)).toInt(), (2 * (radius + glow)).toInt())

            val rays = 16
            for (i in 0 until rays) {
                val angle = (Math.PI * 2 * i / rays) + phase * 0.12
                val inner = radius * 0.84
                val outer = radius * (1.27 + if (speaking) 0.07 * sin(phase + i) else 0.025 * sin(phase + i))
                val x1 = cx + cos(angle) * inner
                val y1 = cy + sin(angle) * inner
                val x2 = cx + cos(angle) * outer
                val y2 = cy + sin(angle) * outer
                g.stroke = BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val shade = if (i % 3 == 0) 0.78f else 1.0f
                g.color = Color((palette.ray.red * shade).toInt().coerceIn(0,255),
                    (palette.ray.green * shade).toInt().coerceIn(0,255),
                    (palette.ray.blue * shade).toInt().coerceIn(0,255))
                g.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
            }

            val face = java.awt.geom.Ellipse2D.Double(cx - radius * .82, cy - radius * .82, radius * 1.64, radius * 1.64)
            g.paint = GradientPaint(0f, (cy - radius).toFloat(), palette.bright, 0f,
                (cy + radius).toFloat(), palette.deep)
            g.fill(face)
            g.color = Color(255, 245, 220, 210)
            g.stroke = BasicStroke(3f)
            g.draw(face)

            val eyeY = cy - radius * .12
            val eyeW = radius * .19
            val eyeH = radius * if (sin(phase * .45) > .985) .035 else .095
            g.color = Color(45, 31, 48)
            g.fillOval((cx - radius * .43).toInt(), (eyeY - eyeH / 2).toInt(), eyeW.toInt(), eyeH.toInt().coerceAtLeast(3))
            g.fillOval((cx + radius * .24).toInt(), (eyeY - eyeH / 2).toInt(), eyeW.toInt(), eyeH.toInt().coerceAtLeast(3))

            val mouthW = radius * .34
            val mouthH = if (speaking) radius * (.07 + .16 * (0.5 + 0.5 * sin(phase * 2.8))) else radius * .06
            g.color = Color(65, 34, 32)
            g.fillOval((cx - mouthW / 2).toInt(), (cy + radius * .28).toInt(), mouthW.toInt(), mouthH.toInt().coerceAtLeast(4))
            g.color = Color(255, 255, 255, 130)
            g.stroke = BasicStroke(1.5f)
            g.drawArc((cx - radius * .46).toInt(), (cy - radius * .5).toInt(), (radius * .92).toInt(), (radius * .92).toInt(), 25, 130)
            g.dispose()
        }
    }
}
