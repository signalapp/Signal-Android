/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.app.ShareCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.viewModel
import org.signal.core.util.logging.Log
import org.signal.mediaoverview.MediaOverviewAction
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewScreen
import org.signal.mediaoverview.MediaOverviewSharedElements
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentSaver
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.mediapreview.MediaPreviewCache
import org.thoughtcrime.securesms.mediaviewer.MediaViewerActivity
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SaveAttachmentUtil
import org.signal.mediaoverview.R as MediaOverviewR

/**
 * Shows every attachment and link in a conversation, or the storage used across all of them.
 */
class MediaOverviewActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(MediaOverviewActivity::class)
    private const val THREAD_ID_EXTRA = "thread_id"

    @JvmStatic
    fun forThread(context: Context, threadId: Long): Intent {
      return Intent(context, MediaOverviewActivity::class.java)
        .putExtra(THREAD_ID_EXTRA, threadId)
    }

    @JvmStatic
    fun forAll(context: Context): Intent {
      return forThread(context, MediaTable.ALL_THREADS.toLong())
    }
  }

  private val viewModel: MediaOverviewViewModel by viewModel {
    val threadId = intent.getLongExtra(THREAD_ID_EXTRA, Long.MIN_VALUE)
    check(threadId != Long.MIN_VALUE) { "Missing thread id." }
    MediaOverviewViewModel(threadId)
  }

  private lateinit var voiceNoteMediaController: VoiceNoteMediaController
  private val sharedElements = MediaOverviewSharedElements()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    super.onCreate(savedInstanceState, ready)

    voiceNoteMediaController = VoiceNoteMediaController(this, false)
    voiceNoteMediaController.voiceNotePlaybackState.observe(this) { onPlaybackStateChanged(it) }

    setContent {
      SignalTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()

        CollectActions(viewModel.actions) { handleAction(it) }

        MediaOverviewScreen(
          state = state,
          media = viewModel.media,
          onEvent = viewModel::onEvent,
          sharedElements = sharedElements
        )
      }
    }
  }

  private fun onPlaybackStateChanged(playbackState: VoiceNotePlaybackState) {
    val uri = playbackState.uri.takeIf { playbackState != VoiceNotePlaybackState.NONE }
    val progress = if (playbackState.trackDuration > 0) {
      (playbackState.playheadPositionMillis.toFloat() / playbackState.trackDuration).coerceIn(0f, 1f)
    } else {
      0f
    }

    viewModel.onEvent(MediaOverviewEvent.AudioPlaybackChanged(uri, playbackState.isPlaying, progress))
  }

  private fun handleAction(action: MediaOverviewAction) {
    when (action) {
      MediaOverviewAction.NavigateBack -> {
        onBackPressedDispatcher.onBackPressed()
      }
      is MediaOverviewAction.OpenMediaPreview -> {
        openMediaPreview(action)
      }
      is MediaOverviewAction.OpenConversation -> {
        startActivity(action.intent)
      }
      is MediaOverviewAction.OpenLink -> {
        CommunicationActions.openBrowserLink(this, action.url)
      }
      is MediaOverviewAction.OpenFileExternally -> {
        openFileExternally(action.uri, action.contentType)
      }
      is MediaOverviewAction.ShareMedia -> {
        val shareIntent = ShareCompat.IntentBuilder(this)
          .setStream(PartAuthority.getAttachmentPublicUri(action.uri))
          .setType(Intent.normalizeMimeType(action.contentType))
          .createChooserIntent()
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startShareChooser(shareIntent)
      }
      is MediaOverviewAction.ShareLink -> {
        val shareIntent = ShareCompat.IntentBuilder(this)
          .setText(action.url)
          .setType("text/plain")
          .createChooserIntent()
        startShareChooser(shareIntent)
      }
      is MediaOverviewAction.ShowForwardSheet -> {
        MultiselectForwardFragmentArgs.create(this, action.threadId, action.uri, action.contentType) { args ->
          MultiselectForwardFragment.showBottomSheet(supportFragmentManager, args)
        }
      }
      is MediaOverviewAction.SaveMedia -> {
        val attachments = action.media.map {
          SaveAttachmentUtil.SaveAttachment(uri = it.uri, contentType = it.contentType, date = it.date, fileName = it.fileName)
        }.toSet()

        lifecycleScope.launch {
          AttachmentSaver(AttachmentSaver.ActivityHost(this@MediaOverviewActivity)).saveAttachments(attachments)
          viewModel.onEvent(MediaOverviewEvent.SaveFinished)
        }
      }
      is MediaOverviewAction.PlayAudio -> {
        voiceNoteMediaController.startSinglePlayback(action.uri, action.messageId, action.progress)
      }
      is MediaOverviewAction.PauseAudio -> {
        voiceNoteMediaController.pausePlayback(action.uri)
      }
      MediaOverviewAction.OpenStorageSettings -> {
        startActivity(AppSettingsActivity.manageStorage(this))
      }
      MediaOverviewAction.ShowMediaNotSentYet -> {
        Toast.makeText(this, MediaOverviewR.string.MediaOverviewScreen__media_not_sent_yet, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun openMediaPreview(action: MediaOverviewAction.OpenMediaPreview) {
    val thumbnail = sharedElements.get(action.key)
    if (thumbnail == null || thumbnail.drawable == null) {
      MediaPreviewCache.drawable = null
      startActivity(action.intent)
      return
    }

    MediaPreviewCache.drawable = thumbnail.drawable
    thumbnail.transitionName = MediaViewerActivity.SHARED_ELEMENT_TRANSITION_NAME
    val options = ActivityOptions.makeSceneTransitionAnimation(this, thumbnail, MediaViewerActivity.SHARED_ELEMENT_TRANSITION_NAME)
    startActivity(action.intent, options.toBundle())
  }

  private fun openFileExternally(uri: Uri, contentType: String?) {
    val intent = Intent(Intent.ACTION_VIEW)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      .setDataAndType(PartAuthority.getAttachmentPublicUri(uri), contentType)

    try {
      startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity existed to view the media.")
      Toast.makeText(this, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show()
    }
  }

  private fun startShareChooser(shareIntent: Intent) {
    if (Build.VERSION.SDK_INT < 34) {
      shareIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, ShareActivity::class.java)))
    }

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity found to share media", e)
      Toast.makeText(this, R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
    }
  }
}
