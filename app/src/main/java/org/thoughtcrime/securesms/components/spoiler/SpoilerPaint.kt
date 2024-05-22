package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.annotation.MainThread
import org.signal.core.util.DimensionUnit
import org.signal.core.util.dp
import org.thoughtcrime.securesms.components.spoiler.SpoilerPaint.update
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.Util
import kotlin.random.Random

/**
 * A wrapper around a paint object that can be used to apply the spoiler effect.
 *
 * The intended usage pattern is to call [update] before your item needs to be drawn.
 * Then, draw with the [paint].
 */
object SpoilerPaint {

  /**
   * A paint that can be used to apply the spoiler effect.
   */
  var shader: BitmapShader? = null

  private val WIDTH = if (Util.isLowMemory(AppDependencies.application)) 50.dp else 100.dp
  private val HEIGHT = if (Util.isLowMemory(AppDependencies.application)) 20.dp else 40.dp
  private val PARTICLES_PER_PIXEL = if (Util.isLowMemory(AppDependencies.application)) 0.001f else 0.002f

  private var shaderBitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ALPHA_8)
  private var bufferBitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ALPHA_8)

  private val bounds: Rect = Rect(0, 0, WIDTH, HEIGHT)
  private val paddedBounds: Rect

  private val alphaStrength = arrayOf(0.9f, 0.7f, 0.5f)
  private val particlePaints = listOf(Paint(), Paint(), Paint())
  private var lastDrawTime: Long = 0
  private val random = Random(System.currentTimeMillis())

  private var particleCount = ((bounds.width() * bounds.height()) * PARTICLES_PER_PIXEL).toInt()
  private val allParticles = Array(3) { Array(particleCount) { Particle(random) } }
  private val allPoints = Array(3) { FloatArray(particleCount * 2) { 0f } }

  private val velocity: Float = DimensionUnit.DP.toPixels(16f) / 1000f

  init {
    val strokeWidth = DimensionUnit.DP.toPixels(1.5f)

    particlePaints.forEachIndexed { index, paint ->
      paint.alpha = (255 * alphaStrength[index]).toInt()
      paint.strokeCap = Paint.Cap.ROUND
      paint.strokeWidth = strokeWidth
    }

    paddedBounds = Rect(
      bounds.left - strokeWidth.toInt(),
      bounds.top - strokeWidth.toInt(),
      bounds.right + strokeWidth.toInt(),
      bounds.bottom + strokeWidth.toInt()
    )

    update()
  }

  /**
   * Invoke every time before you need to use the [shader].
   */
  @MainThread
  fun update() {
    val now = System.currentTimeMillis()
    var dt = now - lastDrawTime
    if (dt < 48) {
      return
    } else if (dt > 64) {
      dt = 48
    }
    lastDrawTime = now

    // The shader draws the live contents of the bitmap at potentially any point.
    // That means that if we draw directly to the in-use bitmap, it could potentially flicker.
    // To avoid that, we draw into a buffer, then swap the buffer into the shader when it's fully drawn.
    val canvas = Canvas(bufferBitmap)
    bufferBitmap.eraseColor(Color.TRANSPARENT)
    draw(canvas, dt)

    val swap = shaderBitmap
    shaderBitmap = bufferBitmap
    bufferBitmap = swap

    shader = BitmapShader(shaderBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
  }

  /**
   * Draws the newest particle state into the canvas based on [dt].
   *
   * Note that we use [paddedBounds] here so that the draw space starts just outside the visible area.
   *
   * This is because we're going to end up tiling this, and being fuzzy around the edges helps reduce
   * the visual "gaps" between the tiles.
   */
  private fun draw(canvas: Canvas, dt: Long) {
    for (allIndex in allParticles.indices) {
      val particles = allParticles[allIndex]
      for (index in 0 until particleCount) {
        val particle = particles[index]

        particle.timeRemaining = particle.timeRemaining - dt
        if (particle.timeRemaining < 0) {
          particle.x = (random.nextFloat() * paddedBounds.width()) + paddedBounds.left
          particle.y = (random.nextFloat() * paddedBounds.height()) + paddedBounds.top
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

    canvas.drawPoints(allPoints[0], 0, particleCount * 2, particlePaints[0])
    canvas.drawPoints(allPoints[1], 0, particleCount * 2, particlePaints[1])
    canvas.drawPoints(allPoints[2], 0, particleCount * 2, particlePaints[2])
  }

  private fun nextDirection(): Float {
    val rand = random.nextFloat()
    return if (rand < 0.5f) {
      0.1f + 0.9f * rand
    } else {
      -0.1f - 0.9f * (rand - 0.5f)
    }
  }

  private data class Particle(
    var x: Float,
    var y: Float,
    var xVel: Float,
    var yVel: Float,
    var timeRemaining: Float
  ) {
    constructor(random: Random) : this(
      x = -1f,
      y = -1f,
      xVel = if (random.nextFloat() < 0.5f) 1f else -1f,
      yVel = if (random.nextFloat() < 0.5f) 1f else -1f,
      timeRemaining = -1f
    )
  }
}
