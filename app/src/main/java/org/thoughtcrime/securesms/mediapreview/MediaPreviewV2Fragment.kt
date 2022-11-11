package org.thoughtcrime.securesms.mediapreview

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.GONE
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewGroup.VISIBLE
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.app.ShareCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.databinding.FragmentMediaPreviewV2Binding
import org.thoughtcrime.securesms.mediapreview.MediaRailAdapter.ImageLoadingListener
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaPreviewV2Fragment : Fragment(R.layout.fragment_media_preview_v2), MediaPreviewFragment.Events {
  private val TAG = Log.tag(MediaPreviewV2Fragment::class.java)

  private val lifecycleDisposable = LifecycleDisposable()
  private val binding by ViewBinderDelegate(FragmentMediaPreviewV2Binding::bind)
  private val viewModel: MediaPreviewV2ViewModel by viewModels()
  private val debouncer = Debouncer(2, TimeUnit.SECONDS)

  private lateinit var fullscreenHelper: FullscreenHelper
  private lateinit var albumRailAdapter: MediaRailAdapter

  override fun onAttach(context: Context) {
    super.onAttach(context)
    fullscreenHelper = FullscreenHelper(requireActivity())
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val args = MediaIntentFactory.requireArguments(requireArguments())
    initializeViewModel(args)
    initializeToolbar(binding.toolbar)
    initializeViewPager()
    initializeAlbumRail()
    initializeFullScreenUi()
    anchorMarginsToBottomInsets(binding.mediaPreviewDetailsContainer)
    lifecycleDisposable += viewModel.state.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe {
      bindCurrentState(it)
    }
  }

  private fun initializeViewModel(args: MediaIntentFactory.MediaPreviewArgs) {
    if (!MediaUtil.isImageType(args.initialMediaType) && !MediaUtil.isVideoType(args.initialMediaType)) {
      Log.w(TAG, "Unsupported media type sent to MediaPreviewV2Fragment, finishing.")
      Snackbar.make(binding.root, R.string.MediaPreviewActivity_unssuported_media_type, Snackbar.LENGTH_LONG)
        .setAction(R.string.MediaPreviewActivity_dismiss_due_to_error) {
          requireActivity().finish()
        }.show()
    }
    viewModel.initialize(args.showThread, args.allMediaInRail, args.leftIsRecent)
    val sorting = MediaDatabase.Sorting.deserialize(args.sorting.ordinal)
    viewModel.fetchAttachments(PartAuthority.requireAttachmentId(args.initialMediaUri), args.threadId, sorting)
  }

  @SuppressLint("RestrictedApi")
  private fun initializeToolbar(toolbar: MaterialToolbar) {
    toolbar.setNavigationOnClickListener {
      requireActivity().onBackPressed()
    }

    toolbar.setTitleTextAppearance(requireContext(), R.style.Signal_Text_TitleMedium)
    toolbar.setSubtitleTextAppearance(requireContext(), R.style.Signal_Text_BodyMedium)
    (binding.toolbar.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
    binding.toolbar.inflateMenu(R.menu.media_preview)
  }

  private fun initializeViewPager() {
    binding.mediaPager.offscreenPageLimit = OFFSCREEN_PAGE_LIMIT_DEFAULT
    binding.mediaPager.setPageTransformer(MarginPageTransformer(ViewUtil.dpToPx(24)))
    val adapter = MediaPreviewV2Adapter(this)
    binding.mediaPager.adapter = adapter
    binding.mediaPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        viewModel.setCurrentPage(position)
      }
    })
  }

  private fun initializeAlbumRail() {
    binding.mediaPreviewPlaybackControls.recyclerView.apply {
      this.itemAnimator = null // Or can crash when set to INVISIBLE while animating by FullscreenHelper https://issuetracker.google.com/issues/148720682
      PagerSnapHelper().attachToRecyclerView(this)
      albumRailAdapter = MediaRailAdapter(
        GlideApp.with(this@MediaPreviewV2Fragment),
        object : MediaRailAdapter.RailItemListener {
          override fun onRailItemClicked(distanceFromActive: Int) {
            binding.mediaPager.currentItem += distanceFromActive
          }

          override fun onRailItemDeleteClicked(distanceFromActive: Int) {
            throw UnsupportedOperationException("Callback unsupported.")
          }
        },
        false,
        object : ImageLoadingListener() {
          override fun onAllRequestsFinished() {
            crossfadeViewIn(this@apply)
          }
        }
      )
      this.adapter = albumRailAdapter
    }
  }

  private fun initializeFullScreenUi() {
    fullscreenHelper.configureToolbarLayout(binding.toolbarCutoutSpacer, binding.toolbar)
    fullscreenHelper.showAndHideWithSystemUI(requireActivity().window, binding.toolbarLayout, binding.mediaPreviewDetailsContainer)
  }

  private fun bindCurrentState(currentState: MediaPreviewV2State) {
    if (currentState.position == -1 && currentState.mediaRecords.isEmpty()) {
      onMediaNotAvailable()
      return
    }
    when (currentState.loadState) {
      MediaPreviewV2State.LoadState.DATA_LOADED -> bindDataLoadedState(currentState)
      MediaPreviewV2State.LoadState.MEDIA_READY -> bindMediaReadyState(currentState)
      else -> null
    }
  }

  private fun bindDataLoadedState(currentState: MediaPreviewV2State) {
    val currentPosition = currentState.position
    val fragmentAdapter = binding.mediaPager.adapter as MediaPreviewV2Adapter

    val backingItems = currentState.mediaRecords.mapNotNull { it.attachment }
    if (backingItems.isEmpty()) {
      onMediaNotAvailable()
      return
    }
    fragmentAdapter.updateBackingItems(backingItems)

    if (binding.mediaPager.currentItem != currentPosition) {
      binding.mediaPager.setCurrentItem(currentPosition, false)
    }
  }

  /**
   * These are binding steps that need a reference to the actual fragment within the pager.
   * This is not available until after a page has been chosen by the ViewPager, and we receive the
   * {@link OnPageChangeCallback}.
   */
  private fun bindMediaReadyState(currentState: MediaPreviewV2State) {
    if (currentState.mediaRecords.isEmpty()) {
      onMediaNotAvailable()
      return
    }

    val currentPosition = currentState.position
    val currentItem: MediaDatabase.MediaRecord = currentState.mediaRecords[currentPosition]

    // pause all other fragments
    childFragmentManager.fragments.map { fragment ->
      if (fragment.tag != "f$currentPosition") {
        (fragment as? MediaPreviewFragment)?.pause()
      }
    }

    bindTextViews(currentItem, currentState.showThread)
    bindMenuItems(currentItem)
    bindMediaPreviewPlaybackControls(currentItem, getMediaPreviewFragmentFromChildFragmentManager(currentPosition))

    val albumThumbnailMedia: List<Media> = if (currentState.allMediaInAlbumRail) {
      currentState.mediaRecords.mapNotNull { it.toMedia() }
    } else {
      currentState.albums[currentItem.attachment?.mmsId] ?: emptyList()
    }
    bindAlbumRail(albumThumbnailMedia, currentItem)
    crossfadeViewIn(binding.mediaPreviewDetailsContainer)
  }

  private fun bindTextViews(currentItem: MediaDatabase.MediaRecord, showThread: Boolean) {
    binding.toolbar.title = getTitleText(currentItem, showThread)
    binding.toolbar.subtitle = getSubTitleText(currentItem)

    val caption = currentItem.attachment?.caption
    binding.mediaPreviewCaption.text = caption
    binding.mediaPreviewCaption.visible = caption != null
  }

  private fun bindMenuItems(currentItem: MediaDatabase.MediaRecord) {
    val menu: Menu = binding.toolbar.menu
    if (currentItem.threadId == MediaIntentFactory.NOT_IN_A_THREAD.toLong()) {
      menu.findItem(R.id.delete).isVisible = false
    }

    binding.toolbar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.edit -> editMediaItem(currentItem)
        R.id.save -> saveToDisk(currentItem)
        R.id.delete -> deleteMedia(currentItem)
        android.R.id.home -> requireActivity().finish()
        else -> return@setOnMenuItemClickListener false
      }
      return@setOnMenuItemClickListener true
    }
  }

  private fun bindMediaPreviewPlaybackControls(currentItem: MediaDatabase.MediaRecord, currentFragment: MediaPreviewFragment?) {
    val mediaType: MediaPreviewPlayerControlView.MediaMode = if (currentItem.attachment?.isVideoGif == true) {
      MediaPreviewPlayerControlView.MediaMode.IMAGE
    } else {
      MediaPreviewPlayerControlView.MediaMode.fromString(currentItem.contentType)
    }
    binding.mediaPreviewPlaybackControls.setMediaMode(mediaType)
    val videoMediaPreviewFragment: VideoMediaPreviewFragment? = currentFragment as? VideoMediaPreviewFragment
    binding.mediaPreviewPlaybackControls.setShareButtonListener {
      videoMediaPreviewFragment?.pause()
      share(currentItem)
    }
    binding.mediaPreviewPlaybackControls.setForwardButtonListener {
      videoMediaPreviewFragment?.pause()
      forward(currentItem)
    }
    currentFragment?.setBottomButtonControls(binding.mediaPreviewPlaybackControls)
  }

  private fun bindAlbumRail(albumThumbnailMedia: List<Media>, currentItem: MediaDatabase.MediaRecord) {
    val albumRail: RecyclerView = binding.mediaPreviewPlaybackControls.recyclerView
    if (albumThumbnailMedia.size > 1) {
      val albumPosition = albumThumbnailMedia.indexOfFirst { it.uri == currentItem.attachment?.uri }
      if (albumRail.visibility == GONE) {
        albumRail.visibility = View.INVISIBLE
      }
      albumRailAdapter.setMedia(albumThumbnailMedia, albumPosition)
      albumRail.smoothScrollToPosition(albumPosition)
    } else {
      albumRail.visibility = View.GONE
      albumRailAdapter.setMedia(emptyList())
    }
  }

  private fun crossfadeViewIn(view: View, duration: Long = 200) {
    if (!view.isVisible) {
      val viewPropertyAnimator = view.animate()
        .alpha(1f)
        .setDuration(duration)
        .withStartAction {
          view.visibility = VISIBLE
        }
      if (Build.VERSION.SDK_INT >= 21) {
        viewPropertyAnimator.interpolator = PathInterpolator(0.17f, 0.17f, 0f, 1f)
      }
      viewPropertyAnimator.start()
    }
  }

  private fun getMediaPreviewFragmentFromChildFragmentManager(currentPosition: Int) = childFragmentManager.findFragmentByTag("f$currentPosition") as? MediaPreviewFragment

  private fun getTitleText(mediaRecord: MediaDatabase.MediaRecord, showThread: Boolean): String {
    val recipient: Recipient = Recipient.live(mediaRecord.recipientId).get()
    val defaultFromString: String = if (mediaRecord.isOutgoing) {
      getString(R.string.MediaPreviewActivity_you)
    } else {
      recipient.getDisplayName(requireContext())
    }
    if (!showThread) {
      return defaultFromString
    }

    val threadRecipient = Recipient.live(mediaRecord.threadRecipientId).get()
    return if (mediaRecord.isOutgoing) {
      if (threadRecipient.isSelf) {
        getString(R.string.note_to_self)
      } else {
        getString(R.string.MediaPreviewActivity_you_to_s, threadRecipient.getDisplayName(requireContext()))
      }
    } else {
      if (threadRecipient.isGroup) {
        getString(R.string.MediaPreviewActivity_s_to_s, defaultFromString, threadRecipient.getDisplayName(requireContext()))
      } else {
        getString(R.string.MediaPreviewActivity_s_to_you, defaultFromString)
      }
    }
  }

  private fun getSubTitleText(mediaRecord: MediaDatabase.MediaRecord): String =
    if (mediaRecord.date > 0) {
      DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), mediaRecord.date)
    } else {
      getString(R.string.MediaPreviewActivity_draft)
    }

  private fun anchorMarginsToBottomInsets(viewToAnchor: View) {
    ViewCompat.setOnApplyWindowInsetsListener(viewToAnchor) { view: View, windowInsetsCompat: WindowInsetsCompat ->
      val layoutParams = view.layoutParams as MarginLayoutParams
      layoutParams.setMargins(
        windowInsetsCompat.systemWindowInsetLeft,
        layoutParams.topMargin,
        windowInsetsCompat.systemWindowInsetRight,
        windowInsetsCompat.systemWindowInsetBottom
      )
      view.layoutParams = layoutParams
      windowInsetsCompat
    }
  }

  override fun singleTapOnMedia(): Boolean {
    fullscreenHelper.toggleUiVisibility()
    return true
  }

  override fun onMediaNotAvailable() {
    Toast.makeText(requireContext(), R.string.MediaPreviewActivity_media_no_longer_available, Toast.LENGTH_LONG).show()
    requireActivity().finish()
  }

  override fun onMediaReady() {
    viewModel.setMediaReady()
  }

  override fun onPlaying() {
    debouncer.publish { fullscreenHelper.hideSystemUI() }
  }

  override fun onStopped() {
    debouncer.clear()
  }

  private fun forward(mediaItem: MediaDatabase.MediaRecord) {
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      MultiselectForwardFragmentArgs.create(
        requireContext(),
        mediaItem.threadId,
        uri,
        attachment.contentType
      ) { args: MultiselectForwardFragmentArgs ->
        MultiselectForwardFragment.showBottomSheet(childFragmentManager, args)
      }
    }
  }

  private fun share(mediaItem: MediaDatabase.MediaRecord) {
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      val publicUri = PartAuthority.getAttachmentPublicUri(uri)
      val mimeType = Intent.normalizeMimeType(attachment.contentType)
      val shareIntent = ShareCompat.IntentBuilder(requireActivity())
        .setStream(publicUri)
        .setType(mimeType)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

      try {
        startActivity(shareIntent)
      } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity existed to share the media.", e)
        Toast.makeText(requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun saveToDisk(mediaItem: MediaDatabase.MediaRecord) {
    SaveAttachmentTask.showWarningDialog(requireContext()) { _: DialogInterface?, _: Int ->
      if (StorageUtil.canWriteToMediaStore()) {
        performSaveToDisk(mediaItem)
        return@showWarningDialog
      }
      Permissions.with(this)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .ifNecessary()
        .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
        .onAnyDenied { Toast.makeText(requireContext(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show() }
        .onAllGranted { performSaveToDisk(mediaItem) }
        .execute()
    }
  }

  fun performSaveToDisk(mediaItem: MediaDatabase.MediaRecord) {
    val saveTask = SaveAttachmentTask(requireContext())
    val saveDate = if (mediaItem.date > 0) mediaItem.date else System.currentTimeMillis()
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      saveTask.executeOnExecutor(SignalExecutors.BOUNDED_IO, SaveAttachmentTask.Attachment(uri, attachment.contentType, saveDate, null))
    }
  }

  private fun deleteMedia(mediaItem: MediaDatabase.MediaRecord) {
    val attachment: DatabaseAttachment = mediaItem.attachment ?: return

    MaterialAlertDialogBuilder(requireContext())
      .setIcon(R.drawable.ic_warning)
      .setTitle(R.string.MediaPreviewActivity_media_delete_confirmation_title)
      .setMessage(R.string.MediaPreviewActivity_media_delete_confirmation_message)
      .setCancelable(true)
      .setPositiveButton(R.string.delete) { _, _ ->
        viewModel.deleteItem(requireContext(), attachment, onSuccess = {
          requireActivity().finish()
        }, onError = {
          Log.e(TAG, "Delete failed!", it)
          requireActivity().finish()
        })
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun editMediaItem(currentItem: MediaDatabase.MediaRecord) {
    val media = currentItem.toMedia()
    if (media == null) {
      val rootView = view
      if (rootView != null) {
        Snackbar.make(rootView, R.string.MediaPreviewFragment_edit_media_error, Snackbar.LENGTH_INDEFINITE).show()
      } else {
        Toast.makeText(requireContext(), R.string.MediaPreviewFragment_edit_media_error, Toast.LENGTH_LONG).show()
      }
      return
    }
    startActivity(MediaSelectionActivity.editor(context = requireContext(), media = listOf(media)))
  }

  override fun onPause() {
    super.onPause()
    getMediaPreviewFragmentFromChildFragmentManager(binding.mediaPager.currentItem)?.pause()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    viewModel.onDestroyView()
  }

  companion object {
    const val ARGS_KEY: String = "args"

    @JvmStatic
    fun isContentTypeSupported(contentType: String?): Boolean {
      return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)
    }
  }
}
