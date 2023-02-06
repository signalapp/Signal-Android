package org.thoughtcrime.securesms.conversation.colors.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ui.ChatColorPreviewView
import org.thoughtcrime.securesms.conversation.colors.ui.ChatColorSelectionViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.customizeOnDraw

private const val MAX_SEEK_DIVISIONS = 1023
private const val MAX_HUE = 360

private const val PAGE_ARG = "page"
private const val SINGLE_PAGE = 0
private const val GRADIENT_PAGE = 1

class CustomChatColorCreatorPageFragment :
  Fragment(R.layout.custom_chat_color_creator_fragment_page) {

  private lateinit var hueSlider: AppCompatSeekBar
  private lateinit var saturationSlider: AppCompatSeekBar
  private lateinit var preview: ChatColorPreviewView

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val args: CustomChatColorCreatorFragmentArgs = CustomChatColorCreatorFragmentArgs.fromBundle(requireArguments())
    val chatColorSelectionViewModel: ChatColorSelectionViewModel = ChatColorSelectionViewModel.getOrCreate(requireActivity(), args.recipientId)
    val page: Int = requireArguments().getInt(PAGE_ARG)
    val factory: CustomChatColorCreatorViewModel.Factory = CustomChatColorCreatorViewModel.Factory(MAX_SEEK_DIVISIONS, ChatColors.Id.forLongValue(args.chatColorId), args.recipientId, createRepository())
    val viewModel: CustomChatColorCreatorViewModel = ViewModelProvider(
      requireParentFragment(),
      factory
    )[CustomChatColorCreatorViewModel::class.java]

    preview = view.findViewById(R.id.chat_color_preview)

    val hueThumb = ThumbDrawable(requireContext())
    val saturationThumb = ThumbDrawable(requireContext())

    val gradientTool: CustomChatColorGradientToolView = view.findViewById(R.id.gradient_tool)
    val save: View = view.findViewById(R.id.save)
    val scrollView: ScrollView = view.findViewById(R.id.scroll_view)

    if (page == SINGLE_PAGE) {
      gradientTool.visibility = View.GONE
    } else {
      gradientTool.setListener(object : CustomChatColorGradientToolView.Listener {
        override fun onGestureStarted() {
          scrollView.requestDisallowInterceptTouchEvent(true)
        }

        override fun onGestureFinished() {
          scrollView.requestDisallowInterceptTouchEvent(false)
        }

        override fun onDegreesChanged(degrees: Float) {
          viewModel.setDegrees(degrees)
        }

        override fun onSelectedEdgeChanged(edge: CustomChatColorEdge) {
          viewModel.setSelectedEdge(edge)
        }
      })
    }

    hueSlider = view.findViewById(R.id.hue_slider)
    saturationSlider = view.findViewById(R.id.saturation_slider)

    hueSlider.thumb = hueThumb
    saturationSlider.thumb = saturationThumb

    hueSlider.max = MAX_SEEK_DIVISIONS
    saturationSlider.max = MAX_SEEK_DIVISIONS

    val colors: IntArray = (0..MAX_SEEK_DIVISIONS).map { hue ->
      ColorUtils.HSLToColor(
        floatArrayOf(
          hue.toHue(MAX_SEEK_DIVISIONS),
          1f,
          calculateLightness(hue.toFloat(), valueFor60To80 = 0.4f)
        )
      )
    }.toIntArray()

    val hueGradientDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)

    hueSlider.progressDrawable = hueGradientDrawable.forSeekBar()

    val saturationProgressDrawable = GradientDrawable().apply {
      orientation = GradientDrawable.Orientation.LEFT_RIGHT
    }

    saturationSlider.progressDrawable = saturationProgressDrawable.forSeekBar()

    hueSlider.setOnSeekBarChangeListener(
      OnProgressChangedListener {
        viewModel.setHueProgress(it)
      }
    )

    saturationSlider.setOnSeekBarChangeListener(
      OnProgressChangedListener {
        viewModel.setSaturationProgress(it)
      }
    )

    viewModel.events.observe(viewLifecycleOwner) { event ->
      when (event) {
        is CustomChatColorCreatorViewModel.Event.SaveNow -> {
          viewModel.saveNow(event.chatColors) { colors ->
            chatColorSelectionViewModel.save(colors)
          }
          Navigation.findNavController(requireParentFragment().requireView()).popBackStack()
        }
        is CustomChatColorCreatorViewModel.Event.Warn -> MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.CustomChatColorCreatorFragmentPage__edit_color)
          .setMessage(resources.getQuantityString(R.plurals.CustomChatColorCreatorFragmentPage__this_color_is_used, event.usageCount, event.usageCount))
          .setPositiveButton(R.string.save) { dialog, _ ->
            dialog.dismiss()
            viewModel.saveNow(event.chatColors) { colors ->
              chatColorSelectionViewModel.save(colors)
            }
            Navigation.findNavController(requireParentFragment().requireView()).popBackStack()
          }
          .setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
          }
          .show()
      }
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.loading) {
        return@observe
      }

      val sliderState: ColorSlidersState = requireNotNull(state.sliderStates[state.selectedEdge])

      hueSlider.progress = sliderState.huePosition
      saturationSlider.progress = sliderState.saturationPosition

      val color: Int = sliderState.getColor()
      hueThumb.setColor(sliderState.getHueColor())
      saturationThumb.setColor(color)
      saturationProgressDrawable.colors = sliderState.getSaturationColors()

      preview.setWallpaper(state.wallpaper)

      if (page == 0) {
        val chatColor = ChatColors.forColor(ChatColors.Id.NotSet, color)
        preview.setChatColors(chatColor)
        save.setOnClickListener {
          viewModel.startSave(chatColor)
        }
      } else {
        val topEdgeColor: ColorSlidersState = requireNotNull(state.sliderStates[CustomChatColorEdge.TOP])
        val bottomEdgeColor: ColorSlidersState = requireNotNull(state.sliderStates[CustomChatColorEdge.BOTTOM])
        val chatColor: ChatColors = ChatColors.forGradient(
          ChatColors.Id.NotSet,
          ChatColors.LinearGradient(
            state.degrees,
            intArrayOf(topEdgeColor.getColor(), bottomEdgeColor.getColor()),
            floatArrayOf(0f, 1f)
          )
        )
        preview.setChatColors(chatColor)
        gradientTool.setSelectedEdge(state.selectedEdge)
        gradientTool.setDegrees(state.degrees)
        gradientTool.setTopColor(topEdgeColor.getColor())
        gradientTool.setBottomColor(bottomEdgeColor.getColor())
        save.setOnClickListener {
          viewModel.startSave(chatColor)
        }
      }
    }

    if (page == 1 && SignalStore.chatColorsValues().shouldShowGradientTooltip) {
      view.post {
        SignalStore.chatColorsValues().shouldShowGradientTooltip = false
        val contentView = layoutInflater.inflate(R.layout.gradient_tool_tooltip, view as ViewGroup, false)
        val popupWindow = PopupWindow(contentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        popupWindow.isOutsideTouchable = false
        popupWindow.isFocusable = true

        if (Build.VERSION.SDK_INT > 21) {
          popupWindow.elevation = ViewUtil.dpToPx(8).toFloat()
        }

        popupWindow.showAsDropDown(gradientTool, 0, -gradientTool.measuredHeight + ViewUtil.dpToPx(48))
      }
    }
  }

  private fun createRepository(): CustomChatColorCreatorRepository {
    return CustomChatColorCreatorRepository(requireContext())
  }

  @ColorInt
  private fun ColorSlidersState.getHueColor(): Int {
    val hue = huePosition.toHue(MAX_SEEK_DIVISIONS)
    return ColorUtils.HSLToColor(
      floatArrayOf(
        hue,
        1f,
        calculateLightness(hue, 0.4f)
      )
    )
  }

  @ColorInt
  private fun ColorSlidersState.getColor(): Int {
    val hue = huePosition.toHue(MAX_SEEK_DIVISIONS)
    return ColorUtils.HSLToColor(
      floatArrayOf(
        hue,
        saturationPosition.toUnit(MAX_SEEK_DIVISIONS),
        calculateLightness(hue)
      )
    )
  }

  private fun ColorSlidersState.getSaturationColors(): IntArray {
    val hue = huePosition.toHue(MAX_SEEK_DIVISIONS)
    val level = calculateLightness(hue)

    return listOf(0f, 1f).map {
      ColorUtils.HSLToColor(
        floatArrayOf(
          hue,
          it,
          level
        )
      )
    }.toIntArray()
  }

  private fun calculateLightness(hue: Float, valueFor60To80: Float = 0.3f): Float {
    val point1 = PointF()
    val point2 = PointF()

    if (hue >= 0f && hue < 60f) {
      point1.set(0f, 0.45f)
      point2.set(60f, valueFor60To80)
    } else if (hue >= 60f && hue < 180f) {
      return valueFor60To80
    } else if (hue >= 180f && hue < 240f) {
      point1.set(180f, valueFor60To80)
      point2.set(240f, 0.5f)
    } else if (hue >= 240f && hue < 300f) {
      point1.set(240f, 0.5f)
      point2.set(300f, 0.4f)
    } else if (hue >= 300f && hue < 360f) {
      point1.set(300f, 0.4f)
      point2.set(360f, 0.45f)
    } else {
      return 0.45f
    }

    return interpolate(point1, point2, hue)
  }

  private fun interpolate(point1: PointF, point2: PointF, x: Float): Float {
    return ((point1.y * (point2.x - x)) + (point2.y * (x - point1.x))) / (point2.x - point1.x)
  }

  private fun Number.toHue(max: Number): Float {
    return Util.clamp(toFloat() * (MAX_HUE / max.toFloat()), 0f, MAX_HUE.toFloat())
  }

  private fun Number.toUnit(max: Number): Float {
    return Util.clamp(toFloat() / max.toFloat(), 0f, 1f)
  }

  private fun Drawable.forSeekBar(): Drawable {
    val height: Int = ViewUtil.dpToPx(8)
    val radii: FloatArray = (1..8).map { 50f }.toFloatArray()
    val bounds = RectF()
    val clipPath = Path()

    return customizeOnDraw { wrapped, canvas ->
      canvas.save()
      bounds.set(this.bounds)
      bounds.inset(0f, (height / 2f) + 1)

      clipPath.rewind()
      clipPath.addRoundRect(bounds, radii, Path.Direction.CW)

      canvas.clipPath(clipPath)
      wrapped.draw(canvas)
      canvas.restore()
    }
  }

  private class OnProgressChangedListener(private val updateFn: (Int) -> Unit) :
    SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
      updateFn(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
  }

  private class ThumbDrawable(context: Context) : Drawable() {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.signal_background_primary)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.TRANSPARENT
    }

    private val borderWidth: Int = ViewUtil.dpToPx(THUMB_MARGIN)
    private val thumbInnerSize: Int = ViewUtil.dpToPx(THUMB_INNER_SIZE)
    private val innerRadius: Float = thumbInnerSize / 2f
    private val thumbSize: Float = (thumbInnerSize + borderWidth).toFloat()
    private val thumbRadius: Float = thumbSize / 2f

    override fun getIntrinsicHeight(): Int = ViewUtil.dpToPx(48)

    override fun getIntrinsicWidth(): Int = ViewUtil.dpToPx(48)

    fun setColor(@ColorInt color: Int) {
      paint.color = color
      invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
      canvas.drawCircle(
        (bounds.width() / 2f) + bounds.left,
        (bounds.height() / 2f) + bounds.top,
        thumbRadius,
        borderPaint
      )
      canvas.drawCircle(
        (bounds.width() / 2f) + bounds.left,
        (bounds.height() / 2f) + bounds.top,
        innerRadius,
        paint
      )
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun getOpacity(): Int = PixelFormat.TRANSPARENT

    companion object {
      @Dimension(unit = Dimension.DP)
      private val THUMB_INNER_SIZE = 16

      @Dimension(unit = Dimension.DP)
      private val THUMB_MARGIN = 1
    }
  }

  companion object {

    fun forSingle(bundle: Bundle): Fragment = forPage(SINGLE_PAGE, bundle)

    fun forGradient(bundle: Bundle): Fragment = forPage(GRADIENT_PAGE, bundle)

    private fun forPage(page: Int, bundle: Bundle): Fragment = CustomChatColorCreatorPageFragment().apply {
      arguments = Bundle().apply {
        putInt(PAGE_ARG, page)
        putAll(bundle)
      }
    }
  }
}
