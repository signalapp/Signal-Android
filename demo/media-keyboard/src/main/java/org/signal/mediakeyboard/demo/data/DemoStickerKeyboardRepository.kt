/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.signal.mediakeyboard.demo.R

/**
 * An in-memory sticker source backed by bundled vector drawables. The vectors are rasterized to
 * bitmaps because Glide has no disk-cache encoder for VectorDrawable.
 */
class DemoStickerKeyboardRepository(private val context: Context) : StickerKeyboardRepository {

  private data class DemoSticker(
    val name: String,
    @field:DrawableRes val image: Int,
    val emoji: String,
    val packId: String,
    val id: Long
  )

  private val demoStickers: List<DemoSticker> = buildList {
    var id = 0L
    fun sticker(name: String, image: Int, emoji: String, packId: String) {
      add(DemoSticker(name, image, emoji, packId, id++))
    }

    sticker("heart", R.drawable.sticker_heart, "❤️", SHAPES_PACK_ID)
    sticker("star", R.drawable.sticker_star, "⭐", SHAPES_PACK_ID)
    sticker("circle", R.drawable.sticker_circle, "🔵", SHAPES_PACK_ID)
    sticker("triangle", R.drawable.sticker_triangle, "🟢", SHAPES_PACK_ID)
    sticker("diamond", R.drawable.sticker_diamond, "💎", SHAPES_PACK_ID)
    sticker("moon", R.drawable.sticker_moon, "🌙", SHAPES_PACK_ID)
    sticker("sun", R.drawable.sticker_sun, "☀️", SHAPES_PACK_ID)
    sticker("bolt", R.drawable.sticker_bolt, "⚡", SHAPES_PACK_ID)

    sticker("happy", R.drawable.sticker_face_happy, "😀", FACES_PACK_ID)
    sticker("sad", R.drawable.sticker_face_sad, "😢", FACES_PACK_ID)
    sticker("wink", R.drawable.sticker_face_wink, "😉", FACES_PACK_ID)
    sticker("surprised", R.drawable.sticker_face_surprised, "😲", FACES_PACK_ID)
    sticker("cool", R.drawable.sticker_face_cool, "😎", FACES_PACK_ID)
    sticker("angry", R.drawable.sticker_face_angry, "😡", FACES_PACK_ID)
  }

  private val rasterized = mutableMapOf<Int, Bitmap>()

  private fun rasterize(@DrawableRes resource: Int): Bitmap {
    return rasterized.getOrPut(resource) {
      val drawable = requireNotNull(context.getDrawable(resource))
      val bitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      bitmap
    }
  }

  private val stickersByName: Map<String, KeyboardSticker> = demoStickers.associate { it.name to it.toKeyboardSticker() }
  private val recents = ArrayDeque<KeyboardSticker>()
  private val packsFlow = MutableStateFlow(buildPacks())

  override fun observeStickerPacks(): Flow<List<KeyboardStickerPack>> = packsFlow

  override fun onStickerUsed(sticker: KeyboardSticker) {
    recents.remove(sticker)
    recents.addFirst(sticker)
    while (recents.size > MAX_RECENTS) {
      recents.removeLast()
    }
    packsFlow.value = buildPacks()
  }

  private fun buildPacks(): List<KeyboardStickerPack> {
    return buildList {
      if (recents.isNotEmpty()) {
        add(
          KeyboardStickerPack(
            id = StickerKeyboardRepository.RECENT_PACK_ID,
            title = null,
            cover = null,
            stickers = recents.toList()
          )
        )
      }

      add(pack(SHAPES_PACK_ID, "Shapes", R.drawable.sticker_star))
      add(pack(FACES_PACK_ID, "Faces", R.drawable.sticker_face_happy))
    }
  }

  private fun pack(id: String, title: String, @DrawableRes cover: Int): KeyboardStickerPack {
    return KeyboardStickerPack(
      id = id,
      title = title,
      cover = rasterize(cover),
      stickers = demoStickers.filter { it.packId == id }.map { stickersByName.getValue(it.name) }
    )
  }

  private fun DemoSticker.toKeyboardSticker(): KeyboardSticker {
    return KeyboardSticker(
      packId = packId,
      packKey = "$packId-key",
      stickerId = id,
      emoji = emoji,
      image = rasterize(image)
    )
  }

  companion object {
    private const val SHAPES_PACK_ID = "shapes"
    private const val FACES_PACK_ID = "faces"
    private const val MAX_RECENTS = 12
    private const val STICKER_SIZE = 512
  }
}
