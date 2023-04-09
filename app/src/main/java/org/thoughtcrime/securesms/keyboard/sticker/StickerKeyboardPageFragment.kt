package org.thoughtcrime.securesms.keyboard.sticker

import android.os.Bundle
import android.view.View
import androidx.annotation.Px
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import com.google.android.material.appbar.AppBarLayout
import org.signal.libsignal.protocol.util.Pair
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stickers.StickerEventListener
import org.thoughtcrime.securesms.stickers.StickerRolloverTouchListener
import org.thoughtcrime.securesms.stickers.StickerRolloverTouchListener.RolloverStickerRetriever
import org.thoughtcrime.securesms.util.DeviceProperties
import org.thoughtcrime.securesms.util.InsetItemDecoration
import org.thoughtcrime.securesms.util.Throttler
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.fragments.requireListener
import java.util.Optional
import kotlin.math.abs
import kotlin.math.max

open class StickerKeyboardPageFragment :
  LoggingFragment(R.layout.keyboard_pager_sticker_page_fragment),
  KeyboardStickerListAdapter.EventListener,
  StickerRolloverTouchListener.RolloverEventListener,
  RolloverStickerRetriever,
  DatabaseObserver.Observer,
  View.OnLayoutChangeListener {

  protected lateinit var stickerList: RecyclerView
  protected lateinit var stickerListAdapter: KeyboardStickerListAdapter
  protected lateinit var layoutManager: GridLayoutManager
  private lateinit var listTouchListener: StickerRolloverTouchListener
  private lateinit var stickerPacksRecycler: RecyclerView
  private lateinit var appBarLayout: AppBarLayout
  private lateinit var stickerPacksAdapter: KeyboardStickerPackListAdapter

  private lateinit var viewModel: StickerKeyboardPageViewModel

  private val packIdSelectionOnScroll: UpdatePackSelectionOnScroll = UpdatePackSelectionOnScroll()
  private val observerThrottler: Throttler = Throttler(500)
  private val stickerThrottler: Throttler = Throttler(100)
  private var firstLoad: Boolean = true

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val glideRequests = GlideApp.with(this)
    stickerListAdapter = KeyboardStickerListAdapter(glideRequests, this, DeviceProperties.shouldAllowApngStickerAnimation(requireContext()))
    layoutManager = GridLayoutManager(requireContext(), 2).apply {
      spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
          val model: Optional<MappingModel<*>> = stickerListAdapter.getModel(position)
          if (model.isPresent && model.get() is KeyboardStickerListAdapter.Header) {
            return spanCount
          }
          return 1
        }
      }
    }
    listTouchListener = StickerRolloverTouchListener(requireContext(), glideRequests, this, this)

    stickerList = view.findViewById(R.id.sticker_keyboard_list)
    stickerList.layoutManager = layoutManager
    stickerList.adapter = stickerListAdapter
    stickerList.addOnItemTouchListener(listTouchListener)
    stickerList.addOnScrollListener(packIdSelectionOnScroll)
    stickerList.addItemDecoration(InsetItemDecoration(StickerInsetSetter()))

    stickerPacksRecycler = view.findViewById(R.id.sticker_packs_recycler)

    stickerPacksAdapter = KeyboardStickerPackListAdapter(glideRequests, DeviceProperties.shouldAllowApngStickerAnimation(requireContext()), this::onTabSelected)
    stickerPacksRecycler.adapter = stickerPacksAdapter

    appBarLayout = view.findViewById(R.id.sticker_keyboard_search_appbar)

    view.findViewById<KeyboardPageSearchView>(R.id.sticker_keyboard_search_text).callbacks = object : KeyboardPageSearchView.Callbacks {
      override fun onClicked() {
        requireListener<Callback>().openStickerSearch()
      }
    }

    view.findViewById<View>(R.id.sticker_search).setOnClickListener {
      requireListener<Callback>().openStickerSearch()
    }

    view.findViewById<View>(R.id.sticker_manage).setOnClickListener { findListener<StickerEventListener>()?.onStickerManagementClicked() }

    ApplicationDependencies.getDatabaseObserver().registerStickerObserver(this)
    ApplicationDependencies.getDatabaseObserver().registerStickerPackObserver(this)

    view.addOnLayoutChangeListener(this)
  }

  override fun onDestroyView() {
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(this)
    requireView().removeOnLayoutChangeListener(this)
    super.onDestroyView()
  }

  @Suppress("DEPRECATION")
  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    viewModel = ViewModelProvider(requireActivity(), StickerKeyboardPageViewModel.Factory())
      .get(StickerKeyboardPageViewModel::class.java)

    viewModel.stickers.observe(viewLifecycleOwner, this::updateStickerList)
    viewModel.packs.observe(viewLifecycleOwner, stickerPacksAdapter::submitList)
    viewModel.getSelectedPack().observe(viewLifecycleOwner, this::updateCategoryTab)

    viewModel.refreshStickers()
  }

  open fun updateStickerList(stickers: MappingModelList) {
    if (firstLoad) {
      stickerListAdapter.submitList(stickers, this::scrollOnLoad)
      firstLoad = false
    } else {
      stickerListAdapter.submitList(stickers)
    }
  }

  open fun scrollOnLoad() {
    layoutManager.scrollToPositionWithOffset(1, 0)
  }

  private fun onTabSelected(stickerPack: KeyboardStickerPackListAdapter.StickerPack) {
    scrollTo(stickerPack.packRecord.packId)
    viewModel.selectPack(stickerPack.packRecord.packId)
  }

  private fun updateCategoryTab(packId: String) {
    stickerPacksRecycler.post {
      val index: Int = stickerPacksAdapter.indexOfFirst(KeyboardStickerPackListAdapter.StickerPack::class.java) { it.packRecord.packId == packId }

      if (index != -1) {
        stickerPacksRecycler.smoothScrollToPosition(index)
      }
    }
  }

  private fun scrollTo(packId: String) {
    val index = stickerListAdapter.indexOfFirst(KeyboardStickerListAdapter.StickerHeader::class.java) { it.packId == packId }
    if (index != -1) {
      appBarLayout.setExpanded(false, true)
      packIdSelectionOnScroll.startAutoScrolling()
      smoothScrollToPositionTop(index)
    }
  }

  private fun smoothScrollToPositionTop(position: Int) {
    val currentPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
    val shortTrip = abs(currentPosition - position) < 40
    if (shortTrip) {
      val smoothScroller: SmoothScroller = object : LinearSmoothScroller(context) {
        override fun getVerticalSnapPreference(): Int {
          return SNAP_TO_START
        }
      }
      smoothScroller.targetPosition = position
      layoutManager.startSmoothScroll(smoothScroller)
    } else {
      layoutManager.scrollToPositionWithOffset(position, 0)
    }
  }

  override fun onStickerClicked(sticker: KeyboardStickerListAdapter.Sticker) {
    stickerThrottler.publish { findListener<StickerEventListener>()?.onStickerSelected(sticker.stickerRecord) }
  }

  override fun onStickerLongClicked(sticker: KeyboardStickerListAdapter.Sticker) {
    listTouchListener.enterHoverMode(stickerList, sticker)
  }

  override fun getStickerDataFromView(view: View): Pair<Any, String>? {
    val position: Int = stickerList.getChildAdapterPosition(view)
    val model: Optional<MappingModel<*>> = stickerListAdapter.getModel(position)
    if (model.isPresent && model.get() is KeyboardStickerListAdapter.Sticker) {
      val sticker = model.get() as KeyboardStickerListAdapter.Sticker
      return Pair(sticker.uri, sticker.stickerRecord.emoji)
    }

    return null
  }

  override fun onStickerPopupStarted() = Unit
  override fun onStickerPopupEnded() = Unit

  override fun onChanged() {
    observerThrottler.publish(viewModel::refreshStickers)
  }

  override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
    onScreenWidthChanged(view?.width ?: 0)
  }

  private fun onScreenWidthChanged(@Px newWidth: Int) {
    layoutManager.spanCount = calculateColumnCount(newWidth)
  }

  private fun calculateColumnCount(@Px screenWidth: Int): Int {
    val divisor = resources.getDimensionPixelOffset(R.dimen.sticker_page_item_width).toFloat() + resources.getDimensionPixelOffset(R.dimen.sticker_page_item_padding).toFloat()
    return max(1, (screenWidth / divisor).toInt())
  }

  private inner class UpdatePackSelectionOnScroll : RecyclerView.OnScrollListener() {

    private var doneScrolling: Boolean = true

    fun startAutoScrolling() {
      doneScrolling = false
    }

    @Override
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState == RecyclerView.SCROLL_STATE_IDLE && !doneScrolling) {
        doneScrolling = true
        onScrolled(recyclerView, 0, 0)
      }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      if (recyclerView.layoutManager == null || !doneScrolling) {
        return
      }

      val layoutManager = recyclerView.layoutManager as LinearLayoutManager
      val index = layoutManager.findFirstCompletelyVisibleItemPosition()
      val item: Optional<MappingModel<*>> = stickerListAdapter.getModel(index)
      if (item.isPresent && item.get() is KeyboardStickerListAdapter.HasPackId) {
        viewModel.selectPack((item.get() as KeyboardStickerListAdapter.HasPackId).packId)
      }
    }
  }

  interface Callback {
    fun openStickerSearch()
  }
}
