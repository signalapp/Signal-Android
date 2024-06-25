package org.thoughtcrime.securesms.mediasend.v2.gallery

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import org.signal.core.util.Stopwatch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.recyclerview.GridDividerDecoration
import org.thoughtcrime.securesms.databinding.V2MediaGalleryFragmentBinding
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaRepository
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil
import org.thoughtcrime.securesms.permissions.PermissionCompat
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
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

  private var selectedMediaTouchHelper: ItemTouchHelper? = null
  private var shouldEnableScrolling: Boolean = true

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
    val binding = V2MediaGalleryFragmentBinding.bind(view)

    SystemWindowInsetsSetter.attach(view, viewLifecycleOwner, WindowInsetsCompat.Type.navigationBars())

    binding.mediaGalleryToolbar.updateLayoutParams<ConstraintLayout.LayoutParams> {
      topMargin = ViewUtil.getStatusBarHeight(view)
    }

    binding.mediaGalleryStatusBarBackground.updateLayoutParams {
      height = ViewUtil.getStatusBarHeight(view)
    }

    binding.mediaGalleryGrid.layoutManager = object : GridLayoutManager(requireContext(), 4) {
      override fun canScrollVertically() = shouldEnableScrolling
    }

    (binding.mediaGalleryGrid.layoutManager as GridLayoutManager).spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int {
        val isFolder: Boolean = (binding.mediaGalleryGrid.adapter as MappingAdapter).getModel(position).map { it is MediaGallerySelectableItem.FolderModel }.orElse(false)

        return if (isFolder) 2 else 1
      }
    }

    binding.mediaGalleryToolbar.setNavigationOnClickListener {
      onBack()
    }

    Material3OnScrollHelper(
      activity = requireActivity(),
      views = listOf(binding.mediaGalleryToolbar, binding.mediaGalleryStatusBarBackground),
      lifecycleOwner = viewLifecycleOwner
    ).attach(binding.mediaGalleryGrid)

    if (callbacks.isCameraEnabled()) {
      binding.mediaGalleryToolbar.setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.action_camera) {
          if (CameraXUtil.isSupported()) {
            callbacks.onNavigateToCamera()
          } else {
            Permissions.with(this)
              .request(Manifest.permission.CAMERA)
              .ifNecessary()
              .onAllGranted { callbacks.onNavigateToCamera() }
              .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.CameraXFragment_to_capture_photos_and_video_allow_camera), R.drawable.ic_camera_24)
              .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_capture_photos), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_capture_photos_videos, getParentFragmentManager())
              .onAnyDenied { Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_capture_photos, Toast.LENGTH_LONG).show() }
              .execute()
          }
          true
        } else {
          false
        }
      }
    } else {
      binding.mediaGalleryToolbar.menu.findItem(R.id.action_camera).isVisible = false
    }

    binding.mediaGalleryCountButton.setOnClickListener {
      callbacks.onSubmit()
    }

    MediaGallerySelectedItem.register(selectedAdapter) { media ->
      callbacks.onSelectedMediaClicked(media)
    }
    binding.mediaGallerySelected.adapter = selectedAdapter
    selectedMediaTouchHelper?.attachToRecyclerView(binding.mediaGallerySelected)

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

    binding.mediaGalleryGrid.adapter = galleryAdapter
    binding.mediaGalleryGrid.addItemDecoration(GridDividerDecoration(4, ViewUtil.dpToPx(2)))

    viewStateLiveData.observe(viewLifecycleOwner) { state ->
      binding.mediaGalleryBottomBarGroup.visible = state.selectedMedia.isNotEmpty()
      binding.mediaGalleryCountButton.setCount(state.selectedMedia.size)

      val stopwatch = Stopwatch("mediaSubmit")
      selectedAdapter.submitList(state.selectedMedia.map { MediaGallerySelectedItem.Model(it) }) {
        stopwatch.split("after-submit")
        stopwatch.stop("MediaGalleryFragment")
        if (state.selectedMedia.isNotEmpty()) {
          binding.mediaGallerySelected.smoothScrollToPosition(state.selectedMedia.size - 1)
        }
      }
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      binding.mediaGalleryToolbar.title = state.bucketTitle ?: requireContext().getString(R.string.AttachmentKeyboard_gallery)
    }

    binding.mediaGalleryAllowAccess.setOnClickListener { requestRequiredPermissions() }

    val galleryItemsWithSelection = LiveDataUtil.combineLatest(
      viewModel.state.map { it.items },
      viewStateLiveData.map { it.selectedMedia }
    ) { galleryItems, selectedMedia ->
      galleryItems.map {
        if (it is MediaGallerySelectableItem.FileModel) {
          it.copy(isSelected = selectedMedia.contains(it.media), selectionOneBasedIndex = selectedMedia.indexOf(it.media) + 1)
        } else {
          it
        }
      }
    }

    galleryItemsWithSelection.observe(viewLifecycleOwner) {
      if (!Permissions.hasAll(requireContext(), *PermissionCompat.forImagesAndVideos())) {
        binding.mediaGalleryMissingPermissions.visibility = View.VISIBLE
        shouldEnableScrolling = false
        galleryAdapter.submitList((1..100).map { MediaGallerySelectableItem.PlaceholderModel() })
      } else {
        binding.mediaGalleryMissingPermissions.visibility = View.GONE
        shouldEnableScrolling = true
        galleryAdapter.submitList(it)
      }
    }

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
  }

  private fun refreshMediaGallery() {
    viewModel.refreshMediaGallery()
  }

  @Deprecated("Deprecated in Java")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun requestRequiredPermissions() {
    Permissions.with(this)
      .request(*PermissionCompat.forImagesAndVideos())
      .ifNecessary()
      .onAllGranted { refreshMediaGallery() }
      .withPermanentDenialDialog(getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio), null, R.string.AttachmentManager_signal_allow_storage, R.string.AttachmentManager_signal_to_show_photos, parentFragmentManager)
      .onAnyDenied { Toast.makeText(requireContext(), R.string.AttachmentManager_signal_needs_storage_access, Toast.LENGTH_LONG).show() }
      .execute()
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
