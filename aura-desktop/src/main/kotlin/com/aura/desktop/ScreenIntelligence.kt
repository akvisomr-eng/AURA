package com.aura.desktop

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage

data class ScreenSnapshot(
    val width: Int,
    val height: Int,
    val monitorCount: Int,
    val image: BufferedImage
)

class ScreenIntelligence {
    fun capturePrimary(): ScreenSnapshot {
        check(!GraphicsEnvironment.isHeadless()) { "Screen tidak tersedia pada lingkungan headless." }
        val bounds = Rectangle(Toolkit.getDefaultToolkit().screenSize)
        val image = Robot().createScreenCapture(bounds)
        return ScreenSnapshot(image.width, image.height, GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size, image)
    }
}
