package org.thoughtcrime.securesms.keyboard.sticker

import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Px
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView
import org.thoughtcrime.securesms.keyboard.findListener
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stickers.StickerKeyboardPageAdapter
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider
import org.thoughtcrime.securesms.util.DeviceProperties
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Search dialog for finding stickers.
 */
class StickerSearchDialogFragment : DialogFragment(), StickerKeyboardPageAdapter.EventListener {

  private lateinit var search: KeyboardPageSearchView
  private lateinit var list: RecyclerView
  private lateinit var noResults: View

  private lateinit var adapter: StickerKeyboardPageAdapter
  private lateinit var layoutManager: GridLayoutManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_Animated_Bottom)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.sticker_search_dialog_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    search = view.findViewById(R.id.sticker_search_text)
    list = view.findViewById(R.id.sticker_search_list)
    noResults = view.findViewById(R.id.sticker_search_no_results)

    adapter = StickerKeyboardPageAdapter(GlideApp.with(this), this, DeviceProperties.shouldAllowApngStickerAnimation(requireContext()))
    layoutManager = GridLayoutManager(requireContext(), 2)

    list.layoutManager = layoutManager
    list.adapter = adapter

    onScreenWidthChanged(getScreenWidth())

    val viewModel: StickerSearchViewModel = ViewModelProviders.of(this, StickerSearchViewModel.Factory(requireContext())).get(StickerSearchViewModel::class.java)

    viewModel.searchResults.observe(viewLifecycleOwner) { stickerRecords ->
      adapter.setStickers(stickerRecords, calculateStickerSize(getScreenWidth()))
      noResults.visibility = if (stickerRecords.isEmpty()) View.VISIBLE else View.GONE
    }

    search.enableBackNavigation()
    search.callbacks = object : KeyboardPageSearchView.Callbacks {
      override fun onQueryChanged(query: String) {
        viewModel.query(query)
      }

      override fun onNavigationClicked() {
        ViewUtil.hideKeyboard(requireContext(), view)
        dismissAllowingStateLoss()
      }
    }

    search.requestFocus()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    onScreenWidthChanged(getScreenWidth())
  }

  private fun onScreenWidthChanged(@Px newWidth: Int) {
    layoutManager.spanCount = calculateColumnCount(newWidth)
    adapter.setStickerSize(calculateStickerSize(newWidth))
  }

  private fun getScreenWidth(): Int {
    val size = Point()
    requireActivity().windowManager.defaultDisplay.getSize(size)
    return size.x
  }

  private fun calculateColumnCount(@Px screenWidth: Int): Int {
    val modifier = resources.getDimensionPixelOffset(R.dimen.sticker_page_item_padding).toFloat()
    val divisor = resources.getDimensionPixelOffset(R.dimen.sticker_page_item_divisor).toFloat()
    return ((screenWidth - modifier) / divisor).toInt()
  }

  private fun calculateStickerSize(@Px screenWidth: Int): Int {
    val multiplier = resources.getDimensionPixelOffset(R.dimen.sticker_page_item_multiplier).toFloat()
    val columnCount = calculateColumnCount(screenWidth)
    return ((screenWidth - (columnCount + 1) * multiplier) / columnCount).toInt()
  }

  companion object {
    fun show(fragmentManager: FragmentManager) {
      StickerSearchDialogFragment().show(fragmentManager, "TAG")
    }
  }

  override fun onStickerClicked(sticker: StickerRecord) {
    ViewUtil.hideKeyboard(requireContext(), requireView())
    findListener<StickerKeyboardProvider.StickerEventListener>()?.onStickerSelected(sticker)
    dismissAllowingStateLoss()
  }

  override fun onStickerLongClicked(targetView: View) = Unit
}
