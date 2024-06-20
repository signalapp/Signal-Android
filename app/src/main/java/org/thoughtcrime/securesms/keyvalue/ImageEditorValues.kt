package org.thoughtcrime.securesms.keyvalue

class ImageEditorValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_IMAGE_EDITOR_MARKER_WIDTH = "image.editor.marker.width"
    private const val KEY_IMAGE_EDITOR_HIGHLIGHTER_WIDTH = "image.editor.highlighter.width"
    private const val KEY_IMAGE_EDITOR_BLUR_WIDTH = "image.editor.blur.width"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = listOf(
    KEY_IMAGE_EDITOR_MARKER_WIDTH,
    KEY_IMAGE_EDITOR_HIGHLIGHTER_WIDTH,
    KEY_IMAGE_EDITOR_BLUR_WIDTH
  )

  fun setMarkerPercentage(markerPercentage: Int) {
    putInteger(KEY_IMAGE_EDITOR_MARKER_WIDTH, markerPercentage)
  }

  fun setHighlighterPercentage(highlighterPercentage: Int) {
    putInteger(KEY_IMAGE_EDITOR_HIGHLIGHTER_WIDTH, highlighterPercentage)
  }

  fun setBlurPercentage(blurPercentage: Int) {
    putInteger(KEY_IMAGE_EDITOR_BLUR_WIDTH, blurPercentage)
  }

  fun getMarkerPercentage(): Int = getInteger(KEY_IMAGE_EDITOR_MARKER_WIDTH, 0)

  fun getHighlighterPercentage(): Int = getInteger(KEY_IMAGE_EDITOR_HIGHLIGHTER_WIDTH, 0)

  fun getBlurPercentage(): Int = getInteger(KEY_IMAGE_EDITOR_BLUR_WIDTH, 0)

  fun getMarkerWidthRange(): Pair<Float, Float> = Pair(0.01f, 0.05f)

  fun getHighlighterWidthRange(): Pair<Float, Float> = Pair(0.03f, 0.08f)

  fun getBlurWidthRange(): Pair<Float, Float> = Pair(0.052f, 0.092f)
}
