package org.thoughtcrime.securesms.scribbles

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import androidx.annotation.IntRange
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.airbnb.lottie.SimpleColorFilter
import com.google.android.material.switchmaterial.SwitchMaterial
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TooltipPopup
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.getColor
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.setColor
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.setUpForColor
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.setListeners
import org.thoughtcrime.securesms.util.visible

class ImageEditorHudV2 @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private var listener: EventListener? = null
  private var currentMode: Mode = Mode.NONE
  private var undoAvailability: Boolean = false
  private var isAvatarEdit: Boolean = false

  init {
    inflate(context, R.layout.v2_media_image_editor_hud, this)
  }

  private val undoButton: View = findViewById(R.id.image_editor_hud_undo)
  private val clearAllButton: View = findViewById(R.id.image_editor_hud_clear_all)
  private val cancelButton: View = findViewById(R.id.image_editor_hud_cancel_button)
  private val drawButton: View = findViewById(R.id.image_editor_hud_draw_button)
  private val textButton: View = findViewById(R.id.image_editor_hud_text_button)
  private val stickerButton: View = findViewById(R.id.image_editor_hud_sticker_button)
  private val blurButton: View = findViewById(R.id.image_editor_hud_blur_button)
  private val doneButton: View = findViewById(R.id.image_editor_hud_done_button)
  private val drawSeekBar: AppCompatSeekBar = findViewById(R.id.image_editor_hud_draw_color_bar)
  private val brushToggle: ImageView = findViewById(R.id.image_editor_hud_draw_brush)
  private val widthSeekBar: AppCompatSeekBar = findViewById(R.id.image_editor_hud_draw_width_bar)
  private val cropRotateButton: View = findViewById(R.id.image_editor_hud_rotate_button)
  private val cropFlipButton: View = findViewById(R.id.image_editor_hud_flip_button)
  private val cropAspectLockButton: ImageView = findViewById(R.id.image_editor_hud_aspect_lock_button)
  private val blurToggleContainer: View = findViewById(R.id.image_editor_hud_blur_toggle_container)
  private val blurToggle: SwitchMaterial = findViewById(R.id.image_editor_hud_blur_toggle)
  private val blurToast: View = findViewById(R.id.image_editor_hud_blur_toast)
  private val blurHelpText: View = findViewById(R.id.image_editor_hud_blur_help_text)
  private val colorIndicator: ImageView = findViewById(R.id.image_editor_hud_color_indicator)

  private val selectableSet: Set<View> = setOf(drawButton, textButton, stickerButton, blurButton)

  private val undoTools: Set<View> = setOf(undoButton, clearAllButton)
  private val drawTools: Set<View> = setOf(brushToggle, drawSeekBar, widthSeekBar)
  private val blurTools: Set<View> = setOf(blurToggleContainer, blurHelpText, widthSeekBar)
  private val drawButtonRow: Set<View> = setOf(cancelButton, doneButton, drawButton, textButton, stickerButton, blurButton)
  private val cropButtonRow: Set<View> = setOf(cancelButton, doneButton, cropRotateButton, cropFlipButton, cropAspectLockButton)

  private val viewsToSlide: Set<View> = drawButtonRow + cropButtonRow

  private val modeChangeAnimationThrottler = ThrottledDebouncer(ANIMATION_DURATION)
  private val undoToolsAnimationThrottler = ThrottledDebouncer(ANIMATION_DURATION)

  private val toastDebouncer = Debouncer(3000)
  private var colorIndicatorAlphaAnimator: Animator? = null

  init {
    initializeViews()
    setMode(currentMode)
  }

  private fun initializeViews() {
    undoButton.setOnClickListener { listener?.onUndo() }
    clearAllButton.setOnClickListener { listener?.onClearAll() }
    cancelButton.setOnClickListener { listener?.onCancel() }

    drawButton.setOnClickListener { setMode(Mode.DRAW) }
    blurButton.setOnClickListener { setMode(Mode.BLUR) }
    textButton.setOnClickListener { setMode(Mode.TEXT) }
    stickerButton.setOnClickListener { setMode(Mode.INSERT_STICKER) }
    brushToggle.setOnClickListener {
      if (currentMode == Mode.DRAW) {
        setMode(Mode.HIGHLIGHT)
      } else {
        setMode(Mode.DRAW)
      }
    }

    doneButton.setOnClickListener {
      if (isAvatarEdit && currentMode == Mode.CROP) {
        setMode(Mode.NONE)
      } else {
        listener?.onDone()
      }
    }

    drawSeekBar.setUpForColor(
      thumbBorderColor = Color.WHITE,
      onColorChanged = {
        updateColorIndicator()
        listener?.onColorChange(getActiveColor())
      },
      onDragStart = {
        colorIndicatorAlphaAnimator?.end()
        colorIndicatorAlphaAnimator = ObjectAnimator.ofFloat(colorIndicator, "alpha", colorIndicator.alpha, 1f)
        colorIndicatorAlphaAnimator?.duration = 150L
        colorIndicatorAlphaAnimator?.start()
      },
      onDragEnd = {
        colorIndicatorAlphaAnimator?.end()
        colorIndicatorAlphaAnimator = ObjectAnimator.ofFloat(colorIndicator, "alpha", colorIndicator.alpha, 0f)
        colorIndicatorAlphaAnimator?.duration = 150L
        colorIndicatorAlphaAnimator?.start()
      }
    )

    cropFlipButton.setOnClickListener { listener?.onFlipHorizontal() }
    cropRotateButton.setOnClickListener { listener?.onRotate90AntiClockwise() }

    cropAspectLockButton.setOnClickListener {
      listener?.onCropAspectLock()
      if (listener?.isCropAspectLocked == true) {
        cropAspectLockButton.setImageResource(R.drawable.ic_crop_lock_24)
      } else {
        cropAspectLockButton.setImageResource(R.drawable.ic_crop_unlock_24)
      }
    }

    blurToggle.setOnCheckedChangeListener { _, enabled -> listener?.onBlurFacesToggled(enabled) }

    setupWidthSeekBar()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupWidthSeekBar() {
    widthSeekBar.thumb = HSVColorSlider.createThumbDrawable(Color.WHITE)
    widthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        listener?.onBrushWidthChange(progress)
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
      override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    widthSeekBar.setOnTouchListener { v, event ->
      if (event.action == MotionEvent.ACTION_DOWN) {
        v?.animate()
          ?.setDuration(ANIMATION_DURATION)
          ?.setInterpolator(DecelerateInterpolator())
          ?.translationX(ViewUtil.dpToPx(36).toFloat())
      } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
        v?.animate()
          ?.setDuration(ANIMATION_DURATION)
          ?.setInterpolator(DecelerateInterpolator())
          ?.translationX(0f)
      }

      v.onTouchEvent(event)
    }

    widthSeekBar.progress = 20
  }

  fun setUpForAvatarEditing() {
    isAvatarEdit = true
  }

  fun setColorPalette(colors: Set<Int>) {
  }

  fun getActiveColor(): Int {
    return if (currentMode == Mode.HIGHLIGHT) {
      withHighlighterAlpha(drawSeekBar.getColor())
    } else {
      drawSeekBar.getColor()
    }
  }

  fun getColorIndex(): Int {
    return drawSeekBar.progress
  }

  fun setColorIndex(index: Int) {
    drawSeekBar.progress = index
  }

  fun setActiveColor(color: Int) {
    drawSeekBar.setColor(color or 0xFF000000.toInt())
    updateColorIndicator()
  }

  fun getActiveBrushWidth(): Int {
    return widthSeekBar.progress
  }

  fun setBlurFacesToggleEnabled(enabled: Boolean) {
    blurToggle.setOnCheckedChangeListener(null)
    blurToggle.isChecked = enabled
    blurToggle.setOnCheckedChangeListener { _, value -> listener?.onBlurFacesToggled(value) }
  }

  fun showBlurHudTooltip() {
    TooltipPopup.forTarget(blurButton)
      .setText(R.string.ImageEditorHud_new_blur_faces_or_draw_anywhere_to_blur)
      .setBackgroundTint(ContextCompat.getColor(context, R.color.core_ultramarine))
      .setTextColor(ContextCompat.getColor(context, R.color.core_white))
      .show(TooltipPopup.POSITION_BELOW)
  }

  fun showBlurToast() {
    blurToast.clearAnimation()
    blurToast.visible = true
    toastDebouncer.publish { blurToast.visible = false }
  }

  fun hideBlurToast() {
    blurToast.clearAnimation()
    blurToast.visible = false
    toastDebouncer.clear()
  }

  fun setEventListener(eventListener: EventListener?) {
    listener = eventListener
  }

  fun enterMode(mode: Mode) {
    setMode(mode, false)
  }

  fun setMode(mode: Mode) {
    setMode(mode, true)
  }

  fun getMode(): Mode = currentMode

  fun setUndoAvailability(undoAvailability: Boolean) {
    this.undoAvailability = undoAvailability

    if (currentMode != Mode.NONE) {
      if (undoAvailability) {
        animateInUndoTools()
      } else {
        animateOutUndoTools()
      }
    }
  }

  private fun setMode(mode: Mode, notify: Boolean) {
    val previousMode: Mode = currentMode
    currentMode = mode
    // updateVisibilities
    clearSelection()

    when (mode) {
      Mode.NONE -> presentModeNone()
      Mode.CROP -> presentModeCrop()
      Mode.TEXT -> presentModeText()
      Mode.DRAW -> presentModeDraw()
      Mode.BLUR -> presentModeBlur()
      Mode.HIGHLIGHT -> presentModeHighlight()
      Mode.INSERT_STICKER -> presentModeMoveDelete()
      Mode.MOVE_DELETE -> presentModeMoveDelete()
    }

    if (notify) {
      listener?.onModeStarted(mode, previousMode)
    }

    listener?.onRequestFullScreen(mode != Mode.NONE, mode != Mode.TEXT)
  }

  private fun presentModeNone() {
    if (isAvatarEdit) {
      animateViewSetChange(
        inSet = drawButtonRow,
        outSet = cropButtonRow + blurTools + drawTools
      )
      animateInUndoTools()
    } else {
      animateViewSetChange(
        inSet = setOf(),
        outSet = drawButtonRow + cropButtonRow + blurTools + drawTools
      )
      animateOutUndoTools()
    }
  }

  private fun presentModeCrop() {
    animateViewSetChange(
      inSet = cropButtonRow - if (isAvatarEdit) setOf(cropAspectLockButton) else setOf(),
      outSet = drawButtonRow + blurTools + drawTools
    )
    animateInUndoTools()
  }

  private fun presentModeDraw() {
    drawButton.isSelected = true
    brushToggle.setImageResource(R.drawable.ic_draw_white_24)
    listener?.onColorChange(getActiveColor())
    updateColorIndicator()
    animateViewSetChange(
      inSet = drawButtonRow + drawTools,
      outSet = cropButtonRow + blurTools
    )
    animateInUndoTools()
  }

  private fun presentModeHighlight() {
    drawButton.isSelected = true
    brushToggle.setImageResource(R.drawable.ic_marker_24)
    listener?.onColorChange(getActiveColor())
    updateColorIndicator()
    animateViewSetChange(
      inSet = drawButtonRow + drawTools,
      outSet = cropButtonRow + blurTools
    )
    animateInUndoTools()
  }

  private fun presentModeBlur() {
    blurButton.isSelected = true
    animateViewSetChange(
      inSet = drawButtonRow + blurTools,
      outSet = drawTools
    )
    animateInUndoTools()
  }

  private fun presentModeText() {
    textButton.isSelected = true
    animateViewSetChange(
      inSet = setOf(drawSeekBar),
      outSet = drawTools + blurTools + drawButtonRow + cropButtonRow
    )
    animateOutUndoTools()
  }

  private fun presentModeMoveDelete() {
    animateViewSetChange(
      outSet = drawTools + blurTools + drawButtonRow + cropButtonRow
    )
  }

  private fun clearSelection() {
    selectableSet.forEach { it.isSelected = false }
  }

  private fun updateColorIndicator() {
    colorIndicator.drawable.colorFilter = SimpleColorFilter(drawSeekBar.getColor())
    colorIndicator.translationX = (drawSeekBar.thumb.bounds.left.toFloat() + ViewUtil.dpToPx(16))
  }

  private fun animateViewSetChange(
    inSet: Set<View> = setOf(),
    outSet: Set<View> = setOf(),
    throttledDebouncer: ThrottledDebouncer = modeChangeAnimationThrottler
  ) {
    val actualOutSet = outSet - inSet

    throttledDebouncer.publish {
      animateInViewSet(inSet)
      animateOutViewSet(actualOutSet)
    }
  }

  private fun animateInViewSet(viewSet: Set<View>) {
    viewSet.forEach { view ->
      if (!view.isVisible) {
        view.animation = getInAnimation(view)
        view.animation.duration = ANIMATION_DURATION
        view.visibility = VISIBLE
      }
    }
  }

  private fun animateOutViewSet(viewSet: Set<View>) {
    viewSet.forEach { view ->
      if (view.isVisible) {
        val animation: Animation = getOutAnimation(view)
        animation.duration = ANIMATION_DURATION
        animation.setListeners(
          onAnimationEnd = {
            view.visibility = GONE
          }
        )

        view.startAnimation(animation)
      }
    }
  }

  private fun getInAnimation(view: View): Animation {
    return if (viewsToSlide.contains(view)) {
      AnimationUtils.loadAnimation(context, R.anim.slide_from_bottom)
    } else {
      AnimationUtils.loadAnimation(context, R.anim.fade_in)
    }
  }

  private fun getOutAnimation(view: View): Animation {
    return if (viewsToSlide.contains(view)) {
      AnimationUtils.loadAnimation(context, R.anim.slide_to_bottom)
    } else {
      AnimationUtils.loadAnimation(context, R.anim.fade_out)
    }
  }

  private fun animateInUndoTools() {
    animateViewSetChange(
      inSet = undoToolsIfAvailable(),
      throttledDebouncer = undoToolsAnimationThrottler
    )
  }

  private fun animateOutUndoTools() {
    animateViewSetChange(
      outSet = undoTools,
      throttledDebouncer = undoToolsAnimationThrottler
    )
  }

  private fun undoToolsIfAvailable(): Set<View> {
    return if (undoAvailability) {
      undoTools
    } else {
      setOf()
    }
  }

  enum class Mode {
    NONE,
    CROP,
    TEXT,
    DRAW,
    HIGHLIGHT,
    BLUR,
    MOVE_DELETE,
    INSERT_STICKER
  }

  companion object {

    private const val ANIMATION_DURATION = 250L

    private fun withHighlighterAlpha(color: Int): Int {
      return color and 0xFF000000.toInt().inv() or 0x60000000
    }
  }

  interface EventListener {
    fun onModeStarted(mode: Mode, previousMode: Mode)
    fun onColorChange(color: Int)
    fun onBrushWidthChange(@IntRange(from = 0, to = 100) widthPercentage: Int)
    fun onBlurFacesToggled(enabled: Boolean)
    fun onUndo()
    fun onClearAll()
    fun onDelete()
    fun onSave()
    fun onFlipHorizontal()
    fun onRotate90AntiClockwise()
    fun onCropAspectLock()
    val isCropAspectLocked: Boolean

    fun onRequestFullScreen(fullScreen: Boolean, hideKeyboard: Boolean)
    fun onDone()
    fun onCancel()
  }
}
