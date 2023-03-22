package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.Util
import kotlin.random.Random

/**
 * Drawable that animates a sparkle effect for spoilers.
 */
class SpoilerDrawable(@ColorInt color: Int) : Drawable() {

  private val alphaStrength = arrayOf(0.9f, 0.7f, 0.5f)
  private val paints = listOf(Paint(), Paint(), Paint())
  private var lastDrawTime: Long = 0

  private var particleCount = 60

  private var allParticles = Array(3) { Array(particleCount) { Particle(random) } }
  private var allPoints = Array(3) { FloatArray(particleCount * 2) { 0f } }

  init {
    for (paint in paints) {
      paint.strokeCap = Paint.Cap.ROUND
      paint.strokeWidth = DimensionUnit.DP.toPixels(1.5f)
    }

    alpha = 255
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
  }

  override fun onBoundsChange(bounds: Rect) {
    val pixelArea = (bounds.right - bounds.left) * (bounds.bottom - bounds.top)

    val newParticleCount = (pixelArea.toFloat() * PARTICLES_PER_PIXEL).toInt()
    if (newParticleCount != particleCount) {
      if (newParticleCount > allParticles[0].size) {
        allParticles = Array(3) { i ->
          Array(newParticleCount) { particleIndex ->
            allParticles[i].getOrNull(particleIndex) ?: Particle(random)
          }
        }

        allPoints = Array(3) { i ->
          FloatArray(newParticleCount * 2) { pointIndex ->
            allPoints[i].getOrNull(pointIndex) ?: 0f
          }
        }
      }
      particleCount = newParticleCount
    }
  }

  override fun draw(canvas: Canvas) {
    val left = bounds.left
    val top = bounds.top
    val right = bounds.right
    val bottom = bounds.bottom

    val now = System.currentTimeMillis()
    val dt = now - lastDrawTime
    lastDrawTime = now

    for (allIndex in allParticles.indices) {
      val particles = allParticles[allIndex]
      for (index in 0 until particleCount) {
        val particle = particles[index]

        particle.timeRemaining = particle.timeRemaining - dt
        if (particle.timeRemaining < 0 || !bounds.contains(particle.x.toInt(), particle.y.toInt())) {
          particle.x = (random.nextFloat() * (right - left)) + left
          particle.y = (random.nextFloat() * (bottom - top)) + top
          particle.xVel = nextDirection()
          particle.yVel = nextDirection()
          particle.timeRemaining = 350 + 750 * random.nextFloat()
        } else {
          val change = dt * velocity
          particle.x += particle.xVel * change
          particle.y += particle.yVel * change
        }

        allPoints[allIndex][index * 2] = particle.x
        allPoints[allIndex][index * 2 + 1] = particle.y
      }
    }

    canvas.drawPoints(allPoints[0], 0, particleCount * 2, paints[0])
    canvas.drawPoints(allPoints[1], 0, particleCount * 2, paints[1])
    canvas.drawPoints(allPoints[2], 0, particleCount * 2, paints[2])
  }

  override fun setAlpha(alpha: Int) {
    paints.forEachIndexed { index, paint ->
      paint.alpha = (alpha * alphaStrength[index]).toInt()
    }
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    for (paint in paints) {
      paint.colorFilter = colorFilter
    }
  }

  @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
  override fun getOpacity(): Int {
    return PixelFormat.TRANSPARENT
  }

  data class Particle(
    var x: Float,
    var y: Float,
    var xVel: Float,
    var yVel: Float,
    var timeRemaining: Float
  ) {
    constructor(random: Random) : this(
      -1f,
      -1f,
      if (random.nextFloat() < 0.5f) 1f else -1f,
      if (random.nextFloat() < 0.5f) 1f else -1f,
      500 + 1000 * random.nextFloat()
    )
  }

  companion object {
    private val PARTICLES_PER_PIXEL = if (Util.isLowMemory(ApplicationDependencies.getApplication())) 0.002f else 0.005f
    private val velocity: Float = DimensionUnit.DP.toPixels(16f) / 1000f
    private val random = Random(System.currentTimeMillis())

    fun nextDirection(): Float {
      val rand = random.nextFloat()
      return if (rand < 0.5f) {
        0.1f + 0.9f * rand
      } else {
        -0.1f - 0.9f * (rand - 0.5f)
      }
    }
  }
}
