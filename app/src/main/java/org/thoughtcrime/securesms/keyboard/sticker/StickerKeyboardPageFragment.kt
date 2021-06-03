package org.thoughtcrime.securesms.keyboard.sticker

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.MediaKeyboardBottomTabAdapter
import org.thoughtcrime.securesms.components.emoji.MediaKeyboardProvider
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider.StickerEventListener

class StickerKeyboardPageFragment : LoggingFragment(R.layout.keyboard_pager_sticker_page_fragment) {

  private val presenter: StickerPresenter = StickerPresenter()
  private lateinit var provider: StickerKeyboardProvider

  private lateinit var stickerPager: ViewPager
  private lateinit var stickerPacksRecycler: RecyclerView
  private lateinit var manageStickers: View
  private lateinit var tabAdapter: MediaKeyboardBottomTabAdapter

  private lateinit var viewModel: StickerKeyboardPageViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    stickerPager = view.findViewById(R.id.sticker_pager)
    manageStickers = view.findViewById(R.id.sticker_manage)
    stickerPacksRecycler = view.findViewById(R.id.sticker_packs_recycler)

    view.findViewById<KeyboardPageSearchView>(R.id.sticker_keyboard_search_text).callbacks = object : KeyboardPageSearchView.Callbacks {
      override fun onClicked() {
        StickerSearchDialogFragment.show(requireActivity().supportFragmentManager)
      }
    }

    view.findViewById<View>(R.id.sticker_search).setOnClickListener { StickerSearchDialogFragment.show(requireActivity().supportFragmentManager) }

    view.findViewById<AppBarLayout>(R.id.sticker_appbar).setExpanded(false)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    viewModel = ViewModelProviders.of(requireActivity()).get(StickerKeyboardPageViewModel::class.java)

    tabAdapter = MediaKeyboardBottomTabAdapter(GlideApp.with(this), this::onTabSelected)
    stickerPacksRecycler.adapter = tabAdapter

    provider = StickerKeyboardProvider(requireActivity(), findListener() ?: throw AssertionError("No sticker listener"))
    provider.requestPresentation(presenter, true)
  }

  private fun findListener(): StickerEventListener? {
    return parentFragment as? StickerEventListener ?: requireActivity() as? StickerEventListener
  }

  private fun onTabSelected(index: Int) {
    stickerPager.currentItem = index
    stickerPacksRecycler.smoothScrollToPosition(index)
    viewModel.selectedTab = index
  }

  private inner class StickerPresenter : MediaKeyboardProvider.Presenter {
    override fun present(
      provider: MediaKeyboardProvider,
      pagerAdapter: PagerAdapter,
      iconProvider: MediaKeyboardProvider.TabIconProvider,
      backspaceObserver: MediaKeyboardProvider.BackspaceObserver?,
      addObserver: MediaKeyboardProvider.AddObserver?,
      searchObserver: MediaKeyboardProvider.SearchObserver?,
      startingIndex: Int
    ) {
      if (stickerPager.adapter != pagerAdapter) {
        stickerPager.adapter = pagerAdapter
      }
      stickerPager.currentItem = viewModel.selectedTab

      stickerPager.clearOnPageChangeListeners()
      stickerPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
        override fun onPageSelected(position: Int) {
          tabAdapter.setActivePosition(position)
          stickerPacksRecycler.smoothScrollToPosition(position)
          provider.setCurrentPosition(position)
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit
        override fun onPageScrollStateChanged(state: Int) = Unit
      })

      tabAdapter.setTabIconProvider(iconProvider, pagerAdapter.count)
      tabAdapter.setActivePosition(stickerPager.currentItem)

      manageStickers.setOnClickListener { addObserver?.onAddClicked() }
    }

    override fun getCurrentPosition(): Int {
      return stickerPager.currentItem
    }

    override fun requestDismissal() = Unit
    override fun isVisible(): Boolean = true
  }
}
