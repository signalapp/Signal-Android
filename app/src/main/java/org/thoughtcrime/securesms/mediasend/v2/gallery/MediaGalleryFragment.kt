package org.thoughtcrime.securesms.mediasend.v2.gallery

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.recyclerview.GridDividerDecoration
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaRepository
import org.thoughtcrime.securesms.mediasend.v2.MediaCountIndicatorButton
import org.thoughtcrime.securesms.util.Stopwatch
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.visible

/**
 * Displays a collection of files and folders to the user to allow them to select
 * media to send.
 */
class MediaGalleryFragment : Fragment(R.layout.v2_media_gallery_fragment) {

  private val viewModel: MediaGalleryViewModel by viewModels(
    factoryProducer = { MediaGalleryViewModel.Factory(null, null, MediaGalleryRepository(requireContext(), MediaRepository())) }
  )

  private lateinit var callbacks: Callbacks

  private lateinit var toolbar: Toolbar
  private lateinit var galleryRecycler: RecyclerView
  private lateinit var countButton: MediaCountIndicatorButton
  private lateinit var bottomBarGroup: View
  private lateinit var selectedRecycler: RecyclerView

  private var selectedMediaTouchHelper: ItemTouchHelper? = null

  private val galleryAdapter = MappingAdapter()
  private val selectedAdapter = MappingAdapter()

  private val viewStateLiveData = MutableLiveData(ViewState())

  private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      onBack()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    callbacks = requireListener()

    toolbar = view.findViewById(R.id.media_gallery_toolbar)
    galleryRecycler = view.findViewById(R.id.media_gallery_grid)
    selectedRecycler = view.findViewById(R.id.media_gallery_selected)
    countButton = view.findViewById(R.id.media_gallery_count_button)
    bottomBarGroup = view.findViewById(R.id.media_gallery_bottom_bar_group)

    (galleryRecycler.layoutManager as GridLayoutManager).spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int {
        val isFolder: Boolean = (galleryRecycler.adapter as MappingAdapter).getModel(position).map { it is MediaGallerySelectableItem.FolderModel }.orElse(false)

        return if (isFolder) 2 else 1
      }
    }

    toolbar.setNavigationOnClickListener {
      onBack()
    }

    if (callbacks.isCameraEnabled()) {
      toolbar.setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.action_camera) {
          callbacks.onNavigateToCamera()
          true
        } else {
          false
        }
      }
    } else {
      toolbar.menu.findItem(R.id.action_camera).isVisible = false
    }

    countButton.setOnClickListener {
      callbacks.onSubmit()
    }

    MediaGallerySelectedItem.register(selectedAdapter) { media ->
      callbacks.onSelectedMediaClicked(media)
    }
    selectedRecycler.adapter = selectedAdapter
    selectedMediaTouchHelper?.attachToRecyclerView(selectedRecycler)

    MediaGallerySelectableItem.registerAdapter(
      mappingAdapter = galleryAdapter,
      onMediaFolderClicked = {
        onBackPressedCallback.isEnabled = true
        viewModel.setMediaFolder(it)
      },
      onMediaClicked = { media, selected ->
        if (selected) {
          callbacks.onMediaUnselected(media)
        } else {
          callbacks.onMediaSelected(media)
        }
      },
      callbacks.isMultiselectEnabled()
    )

    galleryRecycler.adapter = galleryAdapter
    galleryRecycler.addItemDecoration(GridDividerDecoration(4, ViewUtil.dpToPx(2)))

    viewStateLiveData.observe(viewLifecycleOwner) { state ->
      bottomBarGroup.visible = state.selectedMedia.isNotEmpty()
      countButton.setCount(state.selectedMedia.size)

      val stopwatch = Stopwatch("mediaSubmit")
      selectedAdapter.submitList(state.selectedMedia.map { MediaGallerySelectedItem.Model(it) }) {
        stopwatch.split("after-submit")
        stopwatch.stop("MediaGalleryFragment")
        if (state.selectedMedia.isNotEmpty()) {
          selectedRecycler.smoothScrollToPosition(state.selectedMedia.size - 1)
        }
      }
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      toolbar.title = state.bucketTitle
    }

    val galleryItemsWithSelection = LiveDataUtil.combineLatest(
      Transformations.map(viewModel.state) { it.items },
      Transformations.map(viewStateLiveData) { it.selectedMedia }
    ) { galleryItems, selectedMedia ->
      galleryItems.map {
        if (it is MediaGallerySelectableItem.FileModel) {
          it.copy(isSelected = selectedMedia.contains(it.media))
        } else {
          it
        }
      }
    }

    galleryItemsWithSelection.observe(viewLifecycleOwner) {
      galleryAdapter.submitList(it)
    }

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
  }

  fun onBack() {
    if (viewModel.pop()) {
      onBackPressedCallback.isEnabled = false
      callbacks.onToolbarNavigationClicked()
    }
  }

  fun onViewStateUpdated(state: ViewState) {
    viewStateLiveData.value = state
  }

  fun bindSelectedMediaItemDragHelper(helper: ItemTouchHelper) {
    selectedMediaTouchHelper = helper
  }

  data class ViewState(
    val selectedMedia: List<Media> = listOf()
  )

  interface Callbacks {
    fun isCameraEnabled(): Boolean = true
    fun isMultiselectEnabled(): Boolean = false
    fun onMediaSelected(media: Media)
    fun onMediaUnselected(media: Media): Unit = throw UnsupportedOperationException()
    fun onSelectedMediaClicked(media: Media): Unit = throw UnsupportedOperationException()
    fun onNavigateToCamera(): Unit = throw UnsupportedOperationException()
    fun onSubmit(): Unit = throw UnsupportedOperationException()
    fun onToolbarNavigationClicked(): Unit = throw UnsupportedOperationException()
  }
}
