package org.thoughtcrime.securesms.mediasend.v2.text

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.transition.TransitionManager
import com.airbnb.lottie.SimpleColorFilter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations
import org.thoughtcrime.securesms.scribbles.HSVColorSlider
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.getColor
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.setColor
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.setUpForColor
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.setIncognitoKeyboardEnabled
import java.util.Locale

/**
 * Allows user to enter and style the text of a text-based story post
 */
class TextStoryPostTextEntryFragment : KeyboardEntryDialogFragment(
  contentLayoutId = R.layout.stories_text_post_text_entry_fragment
) {

  private val viewModel: TextStoryPostCreationViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    }
  )

  private lateinit var scene: ConstraintLayout
  private lateinit var input: EditText
  private lateinit var confirmButton: View
  private lateinit var colorBar: AppCompatSeekBar
  private lateinit var colorIndicator: ImageView
  private lateinit var alignmentButton: TextAlignmentButton
  private lateinit var scaleBar: AppCompatSeekBar
  private lateinit var backgroundButton: TextColorStyleButton
  private lateinit var fontButton: TextFontButton

  private lateinit var fadeableViews: List<View>

  private var colorIndicatorAlphaAnimator: Animator? = null
  private var bufferFilter = BufferFilter()
  private var allCapsFilter = InputFilter.AllCaps()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    requireDialog().window?.attributes?.windowAnimations = R.style.TextSecure_Animation_TextStoryPostEntryDialog

    initializeViews(view)
    initializeInput()
    initializeAlignmentButton()
    initializeColorBar()
    initializeConfirmButton()
    initializeWidthBar()
    initializeBackgroundButton()
    initializeFontButton()
    initializeViewModel()

    view.setOnClickListener { dismissAllowingStateLoss() }
  }

  private fun initializeViews(view: View) {
    scene = view.findViewById(R.id.scene)
    input = view.findViewById(R.id.input)
    confirmButton = view.findViewById(R.id.confirm)
    colorBar = view.findViewById(R.id.color_bar)
    colorIndicator = view.findViewById(R.id.color_indicator)
    alignmentButton = view.findViewById(R.id.alignment_button)
    fontButton = view.findViewById(R.id.font_button)
    scaleBar = view.findViewById(R.id.width_bar)
    backgroundButton = view.findViewById(R.id.background_button)

    fadeableViews = listOf(
      confirmButton,
      fontButton,
      backgroundButton
    )

    if (FeatureFlags.storiesTextFunctions()) {
      fadeableViews = fadeableViews + alignmentButton
      alignmentButton.visibility = View.VISIBLE
      scaleBar.visibility = View.VISIBLE
    }
  }

  private fun initializeInput() {
    TextStoryTextWatcher.install(input)

    input.filters = input.filters + bufferFilter
    input.doOnTextChanged { _, _, _, _ ->
      presentHint()
    }
    input.doAfterTextChanged { text ->
      viewModel.setTemporaryBody(text?.toString() ?: "")
    }
    input.setText(viewModel.getBody())
    input.setIncognitoKeyboardEnabled(TextSecurePreferences.isIncognitoKeyboardEnabled(requireContext()))
  }

  private fun presentHint() {
    if (TextUtils.isEmpty(input.text)) {
      input.alpha = 0.6f
      if (input.filters.contains(allCapsFilter)) {
        input.hint = getString(R.string.TextStoryPostTextEntryFragment__add_text).uppercase(Locale.getDefault())
      } else {
        input.setHint(R.string.TextStoryPostTextEntryFragment__add_text)
      }
    } else {
      input.alpha = 1f
      input.hint = ""
    }
  }

  private fun initializeBackgroundButton() {
    backgroundButton.onTextColorStyleChanged = {
      viewModel.setTextColorStyle(it)
    }
  }

  private fun initializeFontButton() {
    fontButton.onTextFontChanged = {
      viewModel.setTextFont(it)
    }
  }

  private fun initializeColorBar() {
    colorIndicator.background = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_color_preview)
    colorBar.setUpForColor(
      thumbBorderColor = Color.WHITE,
      onColorChanged = {
        colorIndicator.drawable.colorFilter = SimpleColorFilter(colorBar.getColor())
        colorIndicator.translationX = (colorBar.thumb.bounds.left.toFloat() + ViewUtil.dpToPx(16))
        viewModel.setTextColor(colorBar.getColor())
      },
      onDragStart = {
        colorIndicatorAlphaAnimator?.end()
        colorIndicatorAlphaAnimator = ObjectAnimator.ofFloat(colorIndicator, "alpha", colorIndicator.alpha, 1f)
        colorIndicatorAlphaAnimator?.duration = 150L
        colorIndicatorAlphaAnimator?.start()

        TransitionManager.endTransitions(scene)

        val constraintSet = ConstraintSet()
        constraintSet.clone(scene)
        fadeableViews.forEach {
          constraintSet.setVisibility(it.id, ConstraintSet.INVISIBLE)
        }
        constraintSet.applyTo(scene)

        TransitionManager.beginDelayedTransition(scene)
        constraintSet.connect(colorBar.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(colorBar.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.applyTo(scene)
      },
      onDragEnd = {
        colorIndicatorAlphaAnimator?.end()
        colorIndicatorAlphaAnimator = ObjectAnimator.ofFloat(colorIndicator, "alpha", colorIndicator.alpha, 0f)
        colorIndicatorAlphaAnimator?.duration = 150L
        colorIndicatorAlphaAnimator?.start()

        TransitionManager.endTransitions(scene)
        TransitionManager.beginDelayedTransition(scene)

        val constraintSet = ConstraintSet()
        constraintSet.clone(scene)
        fadeableViews.forEach {
          constraintSet.setVisibility(it.id, ConstraintSet.VISIBLE)
        }
        constraintSet.connect(colorBar.id, ConstraintSet.START, backgroundButton.id, ConstraintSet.END)
        constraintSet.connect(colorBar.id, ConstraintSet.END, fontButton.id, ConstraintSet.START)
        constraintSet.applyTo(scene)
      }
    )

    colorBar.setColor(viewModel.getTextColor())
  }

  private fun initializeConfirmButton() {
    confirmButton.setOnClickListener {
      dismissAllowingStateLoss()
    }
  }

  private fun initializeAlignmentButton() {
    alignmentButton.onAlignmentChangedListener = { alignment ->
      viewModel.setAlignment(alignment)
    }
  }

  private fun initializeViewModel() {
    viewModel.typeface.observe(viewLifecycleOwner) { typeface ->
      input.typeface = typeface
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      input.setTextColor(state.textForegroundColor)
      input.setHintTextColor(state.textForegroundColor)

      if (state.textBackgroundColor == Color.TRANSPARENT) {
        input.background = null
      } else {
        input.background = AppCompatResources.getDrawable(requireContext(), R.drawable.rounded_rectangle_secondary_18)?.apply {
          colorFilter = SimpleColorFilter(state.textBackgroundColor)
        }
      }

      alignmentButton.setAlignment(state.textAlignment)
      scaleBar.progress = state.textScale
      val scale = TextStoryScale.convertToScale(state.textScale)
      input.scaleX = scale
      input.scaleY = scale
      input.gravity = state.textAlignment.gravity
      input.updateLayoutParams<FrameLayout.LayoutParams> {
        gravity = state.textAlignment.gravity
      }

      if (state.textFont.isAllCaps && !input.filters.contains(allCapsFilter)) {
        input.filters = input.filters + allCapsFilter
        val selectionStart = input.selectionStart
        val selectionEnd = input.selectionEnd
        val text = bufferFilter.text
        bufferFilter.text = ""
        input.setText(text)
        input.setSelection(selectionStart, selectionEnd)
      } else if (!state.textFont.isAllCaps && input.filters.contains(allCapsFilter)) {
        input.filters = (input.filters.toList() - allCapsFilter).toTypedArray()
        val selectionStart = input.selectionStart
        val selectionEnd = input.selectionEnd
        val text = bufferFilter.text
        bufferFilter.text = ""
        input.setText(text)
        input.setSelection(selectionStart, selectionEnd)
      }

      backgroundButton.setTextColorStyle(state.textColorStyle)
      fontButton.setTextFont(state.textFont)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun initializeWidthBar() {
    scaleBar.progressDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_width_slider_bg)
    scaleBar.thumb = HSVColorSlider.createThumbDrawable(Color.WHITE)
    scaleBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        viewModel.setTextScale(progress)
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
      override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    scaleBar.setOnTouchListener { v, event ->
      if (event.action == MotionEvent.ACTION_DOWN) {
        animateWidthBarIn()
      } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
        animateWidthBarOut()
      }

      v.onTouchEvent(event)
    }
  }

  private fun animateWidthBarIn() {
    scaleBar.animate()
      .setDuration(250L)
      .setInterpolator(MediaAnimations.interpolator)
      .translationX(ViewUtil.dpToPx(36).toFloat())
  }

  private fun animateWidthBarOut() {
    scaleBar.animate()
      .setDuration(250L)
      .setInterpolator(MediaAnimations.interpolator)
      .translationX(0f)
  }

  override fun onResume() {
    super.onResume()
    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(input)
  }

  override fun onPause() {
    super.onPause()
    ViewUtil.hideKeyboard(requireContext(), input)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    viewModel.setBody(bufferFilter.text)
    findListener<Callback>()?.onTextStoryPostTextEntryDismissed()
  }

  interface Callback {
    fun onTextStoryPostTextEntryDismissed()
  }

  /**
   * BufferFilter records the input to a text field such that a later filter can capitalize text without the buffer
   * being modified.
   */
  class BufferFilter : InputFilter {

    var text: CharSequence = ""

    override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence? {
      text = if (source.isNullOrEmpty()) {
        text.removeRange(dstart, dend)
      } else {
        text.replaceRange(dstart, dend, source.subSequence(start, end))
      }

      return null
    }
  }
}
