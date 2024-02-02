/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity

class PlayerActivity : ComponentActivity() {
  lateinit var frameMetadata: TextView
  lateinit var disposeOpText: TextView
  lateinit var blendOpText: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.player_activity)

    val image = findViewById<ImageView>(R.id.player_image)
    val prevButton = findViewById<Button>(R.id.prev_button)
    val nextButton = findViewById<Button>(R.id.next_button)
    frameMetadata = findViewById<TextView>(R.id.frame_metadata)

    val decoder = ApngDecoder(assets.open("broken03.png"))
    val drawable = ApngDrawable(decoder).apply {
      stop()
      debugDrawBounds = true
    }

    image.setImageDrawable(drawable)

    prevButton.setOnClickListener {
      drawable.prevFrame()
      updateFrameInfo(drawable)
    }

    nextButton.setOnClickListener {
      drawable.nextFrame()
      updateFrameInfo(drawable)
    }

    updateFrameInfo(drawable)
  }

  @SuppressLint("SetTextI18n")
  private fun updateFrameInfo(drawable: ApngDrawable) {
    frameMetadata.text = """
      Frame: ${drawable.position + 1}/${drawable.frameCount}
      --
      Width: ${drawable.currentFrame.fcTL.width}
      Height: ${drawable.currentFrame.fcTL.height}
      --
      xOffset: ${drawable.currentFrame.fcTL.xOffset}
      yOffset: ${drawable.currentFrame.fcTL.yOffset}
      --
      DisposeOp: ${drawable.currentFrame.fcTL.disposeOp.name}
      BlendOp: ${drawable.currentFrame.fcTL.blendOp.name}
      
      WARNING: Going backwards can break rendering.
    """.trimIndent()
  }
}
