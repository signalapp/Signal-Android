package org.thoughtcrime.securesms.keyboard.sticker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Px
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stickers.StickerEventListener
import org.thoughtcrime.securesms.util.DeviceProperties
import org.thoughtcrime.securesms.util.InsetItemDecoration
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.findListener
import kotlin.math.max

/**
 * Search dialog for finding stickers.
 */
class StickerSearchDialogFragment : DialogFragment(), KeyboardStickerListAdapter.EventListener, View.OnLayoutChangeListener {

  private lateinit var search: KeyboardPageSearchView
  private lateinit var list: RecyclerView
  private lateinit var noResults: View

  private lateinit var adapter: KeyboardStickerListAdapter
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

    adapter = KeyboardStickerListAdapter(GlideApp.with(this), this, DeviceProperties.shouldAllowApngStickerAnimation(requireContext()))
    layoutManager = GridLayoutManager(requireContext(), 2)

    list.layoutManager = layoutManager
    list.adapter = adapter
    list.addItemDecoration(InsetItemDecoration(StickerInsetSetter()))

    val viewModel: StickerSearchViewModel = ViewModelProvider(this, StickerSearchViewModel.Factory()).get(StickerSearchViewModel::class.java)

    viewModel.searchResults.observe(viewLifecycleOwner) { stickers ->
      adapter.submitList(stickers)
      noResults.visibility = if (stickers.isEmpty()) View.VISIBLE else View.GONE
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

    view.addOnLayoutChangeListener(this)
  }

  override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
    layoutManager.spanCount = calculateColumnCount(view?.width ?: 0)
  }

  private fun calculateColumnCount(@Px screenWidth: Int): Int {
    val divisor = resources.getDimensionPixelOffset(R.dimen.sticker_page_item_width).toFloat() + resources.getDimensionPixelOffset(R.dimen.sticker_page_item_padding).toFloat()
    return max(1, (screenWidth / divisor).toInt())
  }

  override fun onStickerClicked(sticker: KeyboardStickerListAdapter.Sticker) {
    ViewUtil.hideKeyboard(requireContext(), requireView())
    findListener<StickerEventListener>()?.onStickerSelected(sticker.stickerRecord)
    dismissAllowingStateLoss()
  }

  override fun onStickerLongClicked(sticker: KeyboardStickerListAdapter.Sticker) = Unit

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      StickerSearchDialogFragment().show(fragmentManager, "TAG")
    }
  }
}
