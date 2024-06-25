package org.thoughtcrime.securesms.scribbles.stickers

import android.graphics.Rect
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import org.signal.imageeditor.core.Bounds
import org.signal.imageeditor.core.RendererContext
import org.signal.imageeditor.core.SelectableRenderer
import org.signal.imageeditor.core.renderers.InvalidateableRenderer
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Analog clock sticker renderer for the image editor.
 */
class AnalogClockStickerRenderer
@JvmOverloads constructor(
  val time: Long,
  val style: AnalogClockStickerDrawable.Style = AnalogClockStickerDrawable.Style.STANDARD
) : InvalidateableRenderer(), SelectableRenderer, TappableRenderer {

  private val clockStickerDrawable = AnalogClockStickerDrawable(AppDependencies.application)
  private val insetBounds = Rect(
    Bounds.FULL_BOUNDS.left.toInt(),
    Bounds.FULL_BOUNDS.top.toInt(),
    Bounds.FULL_BOUNDS.right.toInt(),
    Bounds.FULL_BOUNDS.bottom.toInt()
  ).apply { inset(261, 261) }

  init {
    clockStickerDrawable.bounds = insetBounds
    clockStickerDrawable.setTime(time)
    clockStickerDrawable.setStyle(style)
  }

  override fun onTapped() {
    clockStickerDrawable.nextFace()
    invalidate()
  }

  override fun onSelected(selected: Boolean) {
  }

  override fun getSelectionBounds(bounds: RectF) {
    bounds.set(Bounds.FULL_BOUNDS)
  }

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeLong(time)
    dest.writeInt(clockStickerDrawable.getStyle().type)
  }

  override fun render(rendererContext: RendererContext) {
    clockStickerDrawable.draw(rendererContext.canvas)
  }

  override fun hitTest(x: Float, y: Float): Boolean {
    return Bounds.FULL_BOUNDS.contains(x, y)
  }

  companion object CREATOR : Parcelable.Creator<AnalogClockStickerRenderer> {
    override fun createFromParcel(parcel: Parcel): AnalogClockStickerRenderer {
      return AnalogClockStickerRenderer(parcel.readLong(), AnalogClockStickerDrawable.Style.fromType(parcel.readInt()))
    }

    override fun newArray(size: Int): Array<AnalogClockStickerRenderer?> {
      return arrayOfNulls(size)
    }
  }
}
