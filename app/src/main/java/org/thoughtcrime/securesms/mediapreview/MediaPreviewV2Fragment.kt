package org.thoughtcrime.securesms.mediapreview

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.databinding.FragmentMediaPreviewV2Binding
import org.thoughtcrime.securesms.mediapreview.mediarail.CenterDecoration
import org.thoughtcrime.securesms.mediapreview.mediarail.MediaRailAdapter
import org.thoughtcrime.securesms.mediapreview.mediarail.MediaRailAdapter.ImageLoadingListener
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteDeleteUtil
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MediaPreviewV2Fragment : LoggingFragment(R.layout.fragment_media_preview_v2), MediaPreviewFragment.Events {

  private val lifecycleDisposable = LifecycleDisposable()
  private val binding by ViewBinderDelegate(FragmentMediaPreviewV2Binding::bind)
  private val viewModel: MediaPreviewV2ViewModel by viewModels()
  private val debouncer = Debouncer(2, TimeUnit.SECONDS)

  private lateinit var pagerAdapter: MediaPreviewV2Adapter
  private lateinit var albumRailAdapter: MediaRailAdapter
  private lateinit var fullscreenHelper: FullscreenHelper

  private var individualItemWidth: Int = 0

  override fun onAttach(context: Context) {
    super.onAttach(context)
    fullscreenHelper = FullscreenHelper(requireActivity())
    individualItemWidth = context.resources.getDimension(R.dimen.media_rail_item_size).roundToInt()
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
    lifecycleDisposable +=
      viewModel
        .state
        .distinctUntilChanged()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
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
    val sorting = MediaTable.Sorting.deserialize(args.sorting.ordinal)
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
    pagerAdapter = MediaPreviewV2Adapter(this)
    binding.mediaPager.adapter = pagerAdapter
    binding.mediaPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        viewModel.setCurrentPage(position)
      }
    })
  }

  private fun initializeAlbumRail() {
    binding.mediaPreviewPlaybackControls.recyclerView.apply {
      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
      addItemDecoration(CenterDecoration(0))
      albumRailAdapter = MediaRailAdapter(
        GlideApp.with(this@MediaPreviewV2Fragment),
        { media -> jumpViewPagerToMedia(media) },
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
      else -> Unit
    }
  }

  private fun bindDataLoadedState(currentState: MediaPreviewV2State) {
    val currentPosition = currentState.position

    val backingItems = currentState.mediaRecords.mapNotNull { it.attachment }
    if (backingItems.isEmpty()) {
      onMediaNotAvailable()
      return
    }
    pagerAdapter.updateBackingItems(backingItems)

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

    val currentPosition: Int = currentState.position
    val currentItem: MediaTable.MediaRecord = currentState.mediaRecords[currentPosition]
    val currentItemTag: String? = pagerAdapter.getFragmentTag(currentPosition)

    childFragmentManager.fragments.forEach { fragment ->
      if (fragment.tag != currentItemTag) {
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

    fullscreenHelper.showSystemUI()
    crossfadeViewIn(binding.mediaPreviewDetailsContainer)
  }

  private fun bindTextViews(currentItem: MediaTable.MediaRecord, showThread: Boolean) {
    binding.toolbar.title = getTitleText(currentItem, showThread)
    binding.toolbar.subtitle = getSubTitleText(currentItem)
    val messageId: Long? = currentItem.attachment?.mmsId
    if (messageId != null) {
      binding.toolbar.setOnClickListener { v ->
        viewModel.jumpToFragment(v.context, messageId).subscribeBy(
          onSuccess = { startActivity(it) },
          onError = {
            Log.e(TAG, "Could not find message position for message ID: $messageId", it)
            Toast.makeText(v.context, R.string.MediaPreviewActivity_error_finding_message, Toast.LENGTH_LONG).show()
          }
        )
      }
    }

    val caption = currentItem.attachment?.caption
    binding.mediaPreviewCaption.text = caption
    binding.mediaPreviewCaption.visible = caption != null
  }

  private fun bindMenuItems(currentItem: MediaTable.MediaRecord) {
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

  private fun bindMediaPreviewPlaybackControls(currentItem: MediaTable.MediaRecord, currentFragment: MediaPreviewFragment?) {
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

  private fun bindAlbumRail(albumThumbnailMedia: List<Media>, currentItem: MediaTable.MediaRecord) {
    val albumRail: RecyclerView = binding.mediaPreviewPlaybackControls.recyclerView
    if (albumThumbnailMedia.size > 1) {
      val albumPosition = albumThumbnailMedia.indexOfFirst { it.uri == currentItem.attachment?.uri }
      if (albumRail.visibility == GONE) {
        albumRail.visibility = View.INVISIBLE
      }

      albumRailAdapter.currentItemPosition = albumPosition
      albumRailAdapter.submitList(albumThumbnailMedia)
      scrollAlbumRailToCurrentAdapterPosition()
    } else {
      albumRail.visibility = View.GONE
      albumRailAdapter.submitList(emptyList())
      albumRailAdapter.imageLoadingListener.reset()
    }
  }

  private fun scrollAlbumRailToCurrentAdapterPosition() {
    val currentItemPosition = albumRailAdapter.currentItemPosition
    val currentList = albumRailAdapter.currentList
    val albumRail: RecyclerView = binding.mediaPreviewPlaybackControls.recyclerView
    albumRail.scrollToPosition(currentItemPosition)
    for (i in currentList.indices) {
      val isSelected = i == currentItemPosition
      val stableId = albumRailAdapter.getItemId(i)
      val viewHolder = albumRail.findViewHolderForItemId(stableId) as? MediaRailAdapter.MediaRailViewHolder
      viewHolder?.setSelectedItem(isSelected)
    }
    val offsetFromStart = (albumRail.width - individualItemWidth) / 2
    val smoothScroller = OffsetSmoothScroller(requireContext(), offsetFromStart)
    smoothScroller.targetPosition = currentItemPosition
    val layoutManager = albumRail.layoutManager as LinearLayoutManager
    layoutManager.startSmoothScroll(smoothScroller)
  }

  private fun crossfadeViewIn(view: View, duration: Long = 200) {
    if (!view.isVisible && !fullscreenHelper.isSystemUiVisible) {
      val viewPropertyAnimator = view.animate()
        .alpha(1f)
        .setDuration(duration)
        .withStartAction {
          view.visibility = VISIBLE
        }
        .withEndAction {
          if (getView() != null && view == binding.mediaPreviewPlaybackControls.recyclerView) {
            scrollAlbumRailToCurrentAdapterPosition()
          }
        }
      if (Build.VERSION.SDK_INT >= 21) {
        viewPropertyAnimator.interpolator = PathInterpolator(0.17f, 0.17f, 0f, 1f)
      }
      viewPropertyAnimator.start()
    }
  }

  private fun getMediaPreviewFragmentFromChildFragmentManager(currentPosition: Int): MediaPreviewFragment? {
    return childFragmentManager.findFragmentByTag(pagerAdapter.getFragmentTag(currentPosition)) as? MediaPreviewFragment
  }

  private fun jumpViewPagerToMedia(media: Media) {
    val position = pagerAdapter.findItemPosition(media)
    binding.mediaPager.setCurrentItem(position, true)
  }

  private fun getTitleText(mediaRecord: MediaTable.MediaRecord, showThread: Boolean): String {
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

  private fun getSubTitleText(mediaRecord: MediaTable.MediaRecord): CharSequence {
    val text = if (mediaRecord.date > 0) {
      DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), mediaRecord.date)
    } else {
      getString(R.string.MediaPreviewActivity_draft)
    }
    val builder = SpannableStringBuilder(text)

    val onSurfaceColor = ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurface)
    val chevron = ContextUtil.requireDrawable(requireContext(), R.drawable.ic_chevron_end_24)
    chevron.colorFilter = PorterDuffColorFilter(onSurfaceColor, PorterDuff.Mode.SRC_IN)

    SpanUtil.appendCenteredImageSpan(builder, chevron, 10, 10)
    return builder
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

  override fun onStopped(tag: String?) {
    if (tag == null) {
      return
    }

    if (pagerAdapter.getFragmentTag(viewModel.currentPosition) == tag) {
      debouncer.clear()
      fullscreenHelper.showSystemUI()
    }
  }

  override fun unableToPlayMedia() {
    Toast.makeText(requireContext(), R.string.MediaPreviewActivity_unable_to_play_media, Toast.LENGTH_LONG).show()
    requireActivity().finish()
  }

  private fun forward(mediaItem: MediaTable.MediaRecord) {
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

  private fun share(mediaItem: MediaTable.MediaRecord) {
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

  private fun saveToDisk(mediaItem: MediaTable.MediaRecord) {
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

  @Suppress("DEPRECATION")
  fun performSaveToDisk(mediaItem: MediaTable.MediaRecord) {
    val saveTask = SaveAttachmentTask(requireContext())
    val saveDate = if (mediaItem.date > 0) mediaItem.date else System.currentTimeMillis()
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      saveTask.executeOnExecutor(SignalExecutors.BOUNDED_IO, SaveAttachmentTask.Attachment(uri, attachment.contentType, saveDate, null))
    }
  }

  private fun deleteMedia(mediaItem: MediaTable.MediaRecord) {
    val attachment: DatabaseAttachment = mediaItem.attachment ?: return

    MaterialAlertDialogBuilder(requireContext()).apply {
      setIcon(R.drawable.ic_warning)
      setTitle(R.string.MediaPreviewActivity_media_delete_confirmation_title)
      setMessage(R.string.MediaPreviewActivity_media_delete_confirmation_message)
      setCancelable(true)
      setNegativeButton(android.R.string.cancel, null)
      setPositiveButton(R.string.ConversationFragment_delete_for_me) { _, _ ->
        lifecycleDisposable += viewModel.localDelete(requireContext(), attachment)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy(
            onComplete = {
              requireActivity().finish()
            },
            onError = {
              Log.e(TAG, "Delete failed!", it)
              Toast.makeText(requireContext(), R.string.MediaPreviewFragment_media_delete_error, Toast.LENGTH_LONG).show()
              requireActivity().finish()
            }
          )
      }

      if (canRemotelyDelete(attachment)) {
        setNeutralButton(R.string.ConversationFragment_delete_for_everyone) { _, _ ->
          lifecycleDisposable += viewModel.remoteDelete(attachment)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
              onComplete = {
                requireActivity().finish()
              },
              onError = {
                Log.e(TAG, "Delete failed!", it)
                Toast.makeText(requireContext(), R.string.MediaPreviewFragment_media_delete_error, Toast.LENGTH_LONG).show()
                requireActivity().finish()
              }
            )
        }
      }
    }.show()
  }

  fun canRemotelyDelete(attachment: DatabaseAttachment): Boolean {
    val mmsId = attachment.mmsId
    val attachmentCount = SignalDatabase.attachments.getAttachmentsForMessage(mmsId).size
    return attachmentCount <= 1 && RemoteDeleteUtil.isValidSend(listOf(SignalDatabase.mms.getMessageRecord(mmsId)), System.currentTimeMillis())
  }

  private fun editMediaItem(currentItem: MediaTable.MediaRecord) {
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

  private class OffsetSmoothScroller(context: Context, val offset: Int) : LinearSmoothScroller(context) {
    override fun getHorizontalSnapPreference(): Int {
      return SNAP_TO_START
    }

    override fun calculateDxToMakeVisible(view: View?, snapPreference: Int): Int {
      return offset + super.calculateDxToMakeVisible(view, snapPreference)
    }
  }

  companion object {
    private val TAG = Log.tag(MediaPreviewV2Fragment::class.java)

    const val ARGS_KEY: String = "args"

    @JvmStatic
    fun isContentTypeSupported(contentType: String?): Boolean {
      return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)
    }
  }
}
