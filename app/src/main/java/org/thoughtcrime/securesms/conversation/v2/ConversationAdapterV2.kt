/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.google.android.exoplayer2.MediaItem
import org.signal.core.util.logging.Log
import org.signal.core.util.toOptional
import org.thoughtcrime.securesms.BindableConversationItem
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationAdapterBridge
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.Colorizable
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.v2.data.ConversationElementKey
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.conversation.v2.data.ConversationUpdate
import org.thoughtcrime.securesms.conversation.v2.data.IncomingMedia
import org.thoughtcrime.securesms.conversation.v2.data.IncomingTextOnly
import org.thoughtcrime.securesms.conversation.v2.data.OutgoingMedia
import org.thoughtcrime.securesms.conversation.v2.data.OutgoingTextOnly
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Playable
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicyEnforcer
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.ProjectionList
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import java.util.Locale
import java.util.Optional

class ConversationAdapterV2(
  private val lifecycleOwner: LifecycleOwner,
  private val glideRequests: GlideRequests,
  private val clickListener: ConversationAdapter.ItemClickListener,
  private var hasWallpaper: Boolean,
  private val colorizer: Colorizer
) : PagingMappingAdapter<ConversationElementKey>(), ConversationAdapterBridge {

  companion object {
    private val TAG = Log.tag(ConversationAdapterV2::class.java)
  }

  private val _selected = hashSetOf<MultiselectPart>()

  override val selectedItems: Set<MultiselectPart>
    get() = _selected.toSet()

  private var searchQuery: String? = null
  private var inlineContent: ConversationMessage? = null

  private var recordToPulse: ConversationMessage? = null
  private var pulseRequest: ConversationAdapterBridge.PulseRequest? = null

  private val condensedMode: ConversationItemDisplayMode? = null

  init {
    registerFactory(ConversationUpdate::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_update, parent, false)
      ConversationUpdateViewHolder(view)
    }

    registerFactory(OutgoingTextOnly::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_sent_text_only, parent, false)
      OutgoingTextOnlyViewHolder(view)
    }

    registerFactory(OutgoingMedia::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_sent_multimedia, parent, false)
      OutgoingMediaViewHolder(view)
    }

    registerFactory(IncomingTextOnly::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_received_text_only, parent, false)
      IncomingTextOnlyViewHolder(view)
    }

    registerFactory(IncomingMedia::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_received_multimedia, parent, false)
      IncomingMediaViewHolder(view)
    }
  }

  fun getAdapterPositionForMessagePosition(startPosition: Int): Int {
    return startPosition - 1
  }

  fun getLastVisibleConversationMessage(position: Int): ConversationMessage? {
    return try {
      // todo [cody] handle conversation banner adjustment
      getConversationMessage(position)
    } catch (e: IndexOutOfBoundsException) {
      Log.w(TAG, "Race condition changed size of conversation", e)
      null
    }
  }

  fun canJumpToPosition(absolutePosition: Int): Boolean {
    // todo [cody] handle typing indicator
    val position = absolutePosition

    if (position < 0) {
      return false
    }

    if (position > super.getItemCount()) {
      Log.d(TAG, "Could not access corrected position $position as it is out of bounds.")
      return false
    }

    return isRangeAvailable(position - 10, position + 5)
  }

  fun playInlineContent(conversationMessage: ConversationMessage?) {
    if (this.inlineContent !== conversationMessage) {
      this.inlineContent = conversationMessage
      notifyDataSetChanged()
    }
  }

  override fun getConversationMessage(position: Int): ConversationMessage? {
    return when (val item = getItem(position)) {
      is ConversationMessageElement -> item.conversationMessage
      null -> null
      else -> throw AssertionError("Invalid item: ${item.javaClass}")
    }
  }

  override fun hasNoConversationMessages(): Boolean {
    return itemCount == 0
  }

  /**
   * Momentarily highlights a mention at the requested position.
   */
  fun pulseAtPosition(position: Int) {
    if (position >= 0 && position < itemCount) {
      // todo [cody] adjust for typing indicator
      val correctedPosition = position

      recordToPulse = getConversationMessage(correctedPosition)
      if (recordToPulse != null) {
        pulseRequest = ConversationAdapterBridge.PulseRequest(position, recordToPulse!!.messageRecord.isOutgoing)
      }
      notifyItemChanged(correctedPosition)
    }
  }

  override fun consumePulseRequest(): ConversationAdapterBridge.PulseRequest? {
    val request = pulseRequest
    pulseRequest = null
    return request
  }

  fun onHasWallpaperChanged(hasChanged: Boolean) {
    // todo [cody] implement
  }

  private inner class ConversationUpdateViewHolder(itemView: View) : ConversationViewHolder<ConversationUpdate>(itemView) {
    override fun bind(model: ConversationUpdate) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class OutgoingTextOnlyViewHolder(itemView: View) : ConversationViewHolder<OutgoingTextOnly>(itemView) {
    override fun bind(model: OutgoingTextOnly) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class OutgoingMediaViewHolder(itemView: View) : ConversationViewHolder<OutgoingMedia>(itemView) {
    override fun bind(model: OutgoingMedia) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class IncomingTextOnlyViewHolder(itemView: View) : ConversationViewHolder<IncomingTextOnly>(itemView) {
    override fun bind(model: IncomingTextOnly) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class IncomingMediaViewHolder(itemView: View) : ConversationViewHolder<IncomingMedia>(itemView) {
    override fun bind(model: IncomingMedia) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private abstract inner class ConversationViewHolder<T>(itemView: View) : MappingViewHolder<T>(itemView), GiphyMp4Playable, Colorizable {
    val bindable: BindableConversationItem
      get() = itemView as BindableConversationItem

    protected val previousMessage: Optional<MessageRecord>
      get() = getConversationMessage(bindingAdapterPosition + 1)?.messageRecord.toOptional()

    protected val nextMessage: Optional<MessageRecord>
      get() = getConversationMessage(bindingAdapterPosition - 1)?.messageRecord.toOptional()

    protected val displayMode: ConversationItemDisplayMode
      get() = condensedMode ?: ConversationItemDisplayMode.STANDARD

    override fun showProjectionArea() {
      bindable.showProjectionArea()
    }

    override fun hideProjectionArea() {
      bindable.hideProjectionArea()
    }

    override fun getMediaItem(): MediaItem? {
      return bindable.mediaItem
    }

    override fun getPlaybackPolicyEnforcer(): GiphyMp4PlaybackPolicyEnforcer? {
      return bindable.playbackPolicyEnforcer
    }

    override fun getGiphyMp4PlayableProjection(recyclerView: ViewGroup): Projection {
      return bindable.getGiphyMp4PlayableProjection(recyclerView)
    }

    override fun canPlayContent(): Boolean {
      return bindable.canPlayContent()
    }

    override fun shouldProjectContent(): Boolean {
      return bindable.shouldProjectContent()
    }

    override fun getColorizerProjections(coordinateRoot: ViewGroup): ProjectionList {
      return bindable.getColorizerProjections(coordinateRoot)
    }
  }
}
