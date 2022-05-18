package org.thoughtcrime.securesms.conversation.colors.ui.custom

import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.livedata.Store
import java.util.EnumMap
import kotlin.math.roundToInt

class CustomChatColorCreatorViewModel(
  private val maxSliderValue: Int,
  private val chatColorsId: ChatColors.Id,
  private val recipientId: RecipientId?,
  private val repository: CustomChatColorCreatorRepository
) : ViewModel() {

  private val store = Store<CustomChatColorCreatorState>(getInitialState())
  private val internalEvents = SingleLiveEvent<Event>()

  val state: LiveData<CustomChatColorCreatorState> = store.stateLiveData
  val events: LiveData<Event> = internalEvents

  init {
    repository.getWallpaper(recipientId) { wallpaper ->
      store.update { it.copy(wallpaper = wallpaper) }
    }

    if (chatColorsId is ChatColors.Id.Custom) {
      repository.loadColors(chatColorsId) {
        val colors: IntArray = it.getColors()

        val topColor: Int = colors.first()
        val bottomColor: Int = colors.last()

        val topHsl = floatArrayOf(0f, 0f, 0f)
        val bottomHsl = floatArrayOf(0f, 0f, 0f)

        ColorUtils.colorToHSL(topColor, topHsl)
        ColorUtils.colorToHSL(bottomColor, bottomHsl)

        val topHue: Float = topHsl[0]
        val topSaturation: Float = topHsl[1]

        val bottomHue: Float = bottomHsl[0]
        val bottomSaturation: Float = bottomHsl[1]

        val topEdge = ColorSlidersState(
          huePosition = ((topHue / 360f) * maxSliderValue).roundToInt(),
          saturationPosition = (topSaturation * maxSliderValue).roundToInt()
        )

        val bottomEdge = ColorSlidersState(
          huePosition = ((bottomHue / 360f) * maxSliderValue).roundToInt(),
          saturationPosition = (bottomSaturation * maxSliderValue).roundToInt()
        )

        store.update { state ->
          state.copy(
            degrees = it.getDegrees(),
            loading = false,
            sliderStates = EnumMap(
              mapOf(
                CustomChatColorEdge.TOP to topEdge,
                CustomChatColorEdge.BOTTOM to bottomEdge
              )
            )
          )
        }
      }
    }
  }

  fun setHueProgress(progress: Int) {
    store.update { state ->
      state.copy(
        sliderStates = state.sliderStates.apply {
          val oldData: ColorSlidersState = requireNotNull(get(state.selectedEdge))
          put(state.selectedEdge, oldData.copy(huePosition = progress))
        }
      )
    }
  }

  fun setSaturationProgress(progress: Int) {
    store.update { state ->
      state.copy(
        sliderStates = state.sliderStates.apply {
          val oldData: ColorSlidersState = requireNotNull(get(state.selectedEdge))
          put(state.selectedEdge, oldData.copy(saturationPosition = progress))
        }
      )
    }
  }

  fun setDegrees(degrees: Float) {
    store.update { it.copy(degrees = degrees) }
  }

  fun setSelectedEdge(selectedEdge: CustomChatColorEdge) {
    store.update { it.copy(selectedEdge = selectedEdge) }
  }

  fun startSave(chatColors: ChatColors) {
    if (chatColorsId is ChatColors.Id.Custom) {
      repository.getUsageCount(chatColorsId) {
        if (it > 0) {
          internalEvents.postValue(Event.Warn(it, chatColors))
        } else {
          internalEvents.postValue(Event.SaveNow(chatColors))
        }
      }
    } else {
      internalEvents.postValue(Event.SaveNow(chatColors))
    }
  }

  fun saveNow(chatColors: ChatColors, onSaved: (ChatColors) -> Unit) {
    repository.setChatColors(chatColors.withId(chatColorsId), onSaved)
  }

  private fun getInitialState() = CustomChatColorCreatorState(
    loading = chatColorsId is ChatColors.Id.Custom,
    wallpaper = null,
    sliderStates = EnumMap(
      mapOf(
        CustomChatColorEdge.TOP to ColorSlidersState(maxSliderValue / 2, maxSliderValue / 2),
        CustomChatColorEdge.BOTTOM to ColorSlidersState(maxSliderValue / 2, maxSliderValue / 2)
      )
    ),
    selectedEdge = CustomChatColorEdge.BOTTOM,
    degrees = 180f
  )

  class Factory(
    private val maxSliderValue: Int,
    private val chatColorsId: ChatColors.Id,
    private val recipientId: RecipientId?,
    private val chatColorCreatorRepository: CustomChatColorCreatorRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(CustomChatColorCreatorViewModel(maxSliderValue, chatColorsId, recipientId, chatColorCreatorRepository)))
    }
  }

  sealed class Event {
    class Warn(val usageCount: Int, val chatColors: ChatColors) : Event()
    class SaveNow(val chatColors: ChatColors) : Event()
  }
}
