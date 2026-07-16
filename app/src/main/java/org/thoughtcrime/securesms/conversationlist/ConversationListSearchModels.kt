/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchModels.EmptyModel
import org.thoughtcrime.securesms.contacts.paged.ContactSearchModels.GroupWithMembersModel
import org.thoughtcrime.securesms.contacts.paged.ContactSearchModels.MessageModel
import org.thoughtcrime.securesms.contacts.paged.ContactSearchModels.ThreadModel
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProviderBuilder
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * Holds the [MappingModel]s and [MappingViewHolder]s used by [ConversationListSearchAdapter] on top of the
 * base set in [org.thoughtcrime.securesms.contacts.paged.ContactSearchModels], along with helpers for
 * registering them on a [MappingAdapter] (RecyclerView) or building a [MappingEntryProvider] (Compose).
 */
object ConversationListSearchModels {

  const val PAYLOAD_TIMESTAMP = 0

  fun registerThreads(
    mappingAdapter: MappingAdapter,
    onClicked: ContactSearchAdapter.OnClickedCallback<ContactSearchData.Thread>,
    onLongClicked: (View, ContactSearchData.Thread) -> Boolean,
    lifecycleOwner: LifecycleOwner,
    requestManager: RequestManager
  ) {
    mappingAdapter.registerFactory(
      ThreadModel::class.java,
      LayoutFactory({ ThreadViewHolder(onClicked, onLongClicked, lifecycleOwner, requestManager, it) }, R.layout.conversation_list_item_view)
    )
  }

  fun registerMessages(
    mappingAdapter: MappingAdapter,
    onClicked: ContactSearchAdapter.OnClickedCallback<ContactSearchData.Message>,
    lifecycleOwner: LifecycleOwner,
    requestManager: RequestManager
  ) {
    mappingAdapter.registerFactory(
      MessageModel::class.java,
      LayoutFactory({ MessageViewHolder(onClicked, lifecycleOwner, requestManager, it) }, R.layout.conversation_list_item_view)
    )
  }

  fun registerGroupsWithMembers(
    mappingAdapter: MappingAdapter,
    onClicked: ContactSearchAdapter.OnClickedCallback<ContactSearchData.GroupWithMembers>,
    lifecycleOwner: LifecycleOwner,
    requestManager: RequestManager
  ) {
    mappingAdapter.registerFactory(
      GroupWithMembersModel::class.java,
      LayoutFactory({ GroupWithMembersViewHolder(onClicked, lifecycleOwner, requestManager, it) }, R.layout.conversation_list_item_view)
    )
  }

  fun registerEmpty(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(
      EmptyModel::class.java,
      LayoutFactory({ EmptyViewHolder(it) }, R.layout.conversation_list_empty_search_state)
    )
  }

  fun registerChatFilters(mappingAdapter: MappingAdapter, onClearFilterClicked: () -> Unit) {
    mappingAdapter.registerFactory(
      ChatFilterMappingModel::class.java,
      LayoutFactory({ ChatFilterViewHolder(it, onClearFilterClicked) }, R.layout.conversation_list_item_clear_filter)
    )
    mappingAdapter.registerFactory(
      ChatFilterEmptyMappingModel::class.java,
      LayoutFactory({ ChatFilterViewHolder(it, onClearFilterClicked) }, R.layout.conversation_list_item_clear_filter_empty)
    )
  }

  /**
   * Returns a [MappingEntryProvider] containing the same set of view holders registered by the
   * adapter-side `register*` methods, suitable for use with a Compose `MappingLazyColumn`.
   */
  fun composeEntries(
    onThreadClicked: ContactSearchAdapter.OnClickedCallback<ContactSearchData.Thread>,
    onThreadLongClicked: (View, ContactSearchData.Thread) -> Boolean,
    onMessageClicked: ContactSearchAdapter.OnClickedCallback<ContactSearchData.Message>,
    onGroupWithMembersClicked: ContactSearchAdapter.OnClickedCallback<ContactSearchData.GroupWithMembers>,
    onClearFilterClicked: () -> Unit,
    lifecycleOwner: LifecycleOwner,
    requestManager: RequestManager
  ): MappingEntryProvider<Any> {
    return MappingEntryProviderBuilder<Any>().apply {
      viewHolder<ThreadModel>(
        key = { model -> "Thread:${model.thread.contactSearchKey}" }
      ) { ctx ->
        LayoutFactory(
          { view -> ThreadViewHolder(onThreadClicked, onThreadLongClicked, lifecycleOwner, requestManager, view) },
          R.layout.conversation_list_item_view
        ).createViewHolder(FrameLayout(ctx))
      }
      viewHolder<MessageModel>(
        key = { model -> "Message:${model.message.contactSearchKey}" }
      ) { ctx ->
        LayoutFactory(
          { view -> MessageViewHolder(onMessageClicked, lifecycleOwner, requestManager, view) },
          R.layout.conversation_list_item_view
        ).createViewHolder(FrameLayout(ctx))
      }
      viewHolder<GroupWithMembersModel>(
        key = { model -> "GroupWithMembers:${model.groupWithMembers.contactSearchKey}" }
      ) { ctx ->
        LayoutFactory(
          { view -> GroupWithMembersViewHolder(onGroupWithMembersClicked, lifecycleOwner, requestManager, view) },
          R.layout.conversation_list_item_view
        ).createViewHolder(FrameLayout(ctx))
      }
      viewHolder<EmptyModel> { ctx ->
        LayoutFactory(
          { view -> EmptyViewHolder(view) },
          R.layout.conversation_list_empty_search_state
        ).createViewHolder(FrameLayout(ctx))
      }
      viewHolder<ChatFilterMappingModel> { ctx ->
        LayoutFactory(
          { view -> ChatFilterViewHolder<ChatFilterMappingModel>(view, onClearFilterClicked) },
          R.layout.conversation_list_item_clear_filter
        ).createViewHolder(FrameLayout(ctx))
      }
      viewHolder<ChatFilterEmptyMappingModel> { ctx ->
        LayoutFactory(
          { view -> ChatFilterViewHolder<ChatFilterEmptyMappingModel>(view, onClearFilterClicked) },
          R.layout.conversation_list_item_clear_filter_empty
        ).createViewHolder(FrameLayout(ctx))
      }
    }.build()
  }

  enum class ChatFilterOptions(val code: String) {
    WITH_TIP("with-tip"),
    WITHOUT_TIP("without-tip");

    companion object {
      fun fromCode(code: String): ChatFilterOptions {
        return entries.firstOrNull { it.code == code } ?: WITHOUT_TIP
      }
    }
  }

  open class BaseChatFilterMappingModel<T : BaseChatFilterMappingModel<T>>(val options: ChatFilterOptions) : MappingModel<T> {
    override fun areItemsTheSame(newItem: T): Boolean = true

    override fun areContentsTheSame(newItem: T): Boolean = options == newItem.options
  }

  class ChatFilterMappingModel(options: ChatFilterOptions) : BaseChatFilterMappingModel<ChatFilterMappingModel>(options)

  class ChatFilterEmptyMappingModel(options: ChatFilterOptions) : BaseChatFilterMappingModel<ChatFilterEmptyMappingModel>(options)

  private abstract class ConversationListItemViewHolder<M : MappingModel<M>>(
    itemView: View
  ) : MappingViewHolder<M>(itemView) {
    private val conversationListItem: ConversationListItem = itemView as ConversationListItem

    override fun bind(model: M) {
      if (payload.contains(PAYLOAD_TIMESTAMP)) {
        conversationListItem.updateTimestamp()
        return
      }

      fullBind(model)
    }

    abstract fun fullBind(model: M)
  }

  private class EmptyViewHolder(
    itemView: View
  ) : MappingViewHolder<EmptyModel>(itemView) {

    private val noResults = itemView.findViewById<TextView>(R.id.search_no_results)

    override fun bind(model: EmptyModel) {
      if (payload.isNotEmpty()) {
        return
      }

      noResults.text = context.getString(R.string.SearchFragment_no_results, model.empty.query ?: "")
    }
  }

  private class ThreadViewHolder(
    private val threadListener: ContactSearchAdapter.OnClickedCallback<ContactSearchData.Thread>,
    private val threadLongClickListener: (View, ContactSearchData.Thread) -> Boolean,
    private val lifecycleOwner: LifecycleOwner,
    private val requestManager: RequestManager,
    itemView: View
  ) : ConversationListItemViewHolder<ThreadModel>(itemView) {
    override fun fullBind(model: ThreadModel) {
      itemView.setOnClickListener {
        threadListener.onClicked(itemView, model.thread, false)
      }

      itemView.setOnLongClickListener {
        threadLongClickListener(itemView, model.thread)
      }

      (itemView as ConversationListItem).bindThread(
        lifecycleOwner,
        model.thread.threadWithRecipient,
        requestManager,
        Locale.getDefault(),
        emptySet(),
        ConversationSet(),
        model.thread.query,
        true,
        false,
        null
      )
    }
  }

  private class MessageViewHolder(
    private val messageListener: ContactSearchAdapter.OnClickedCallback<ContactSearchData.Message>,
    private val lifecycleOwner: LifecycleOwner,
    private val requestManager: RequestManager,
    itemView: View
  ) : ConversationListItemViewHolder<MessageModel>(itemView) {
    override fun fullBind(model: MessageModel) {
      itemView.setOnClickListener {
        messageListener.onClicked(itemView, model.message, false)
      }

      (itemView as ConversationListItem).bindMessage(
        lifecycleOwner,
        model.message.messageResult,
        requestManager,
        Locale.getDefault(),
        model.message.query
      )
    }
  }

  private class GroupWithMembersViewHolder(
    private val groupWithMembersListener: ContactSearchAdapter.OnClickedCallback<ContactSearchData.GroupWithMembers>,
    private val lifecycleOwner: LifecycleOwner,
    private val requestManager: RequestManager,
    itemView: View
  ) : ConversationListItemViewHolder<GroupWithMembersModel>(itemView) {
    override fun fullBind(model: GroupWithMembersModel) {
      itemView.setOnClickListener {
        groupWithMembersListener.onClicked(itemView, model.groupWithMembers, false)
      }

      (itemView as ConversationListItem).bindGroupWithMembers(
        lifecycleOwner,
        model.groupWithMembers,
        requestManager,
        Locale.getDefault()
      )
    }
  }

  private class ChatFilterViewHolder<T : BaseChatFilterMappingModel<T>>(itemView: View, listener: () -> Unit) : MappingViewHolder<T>(itemView) {

    private val tip = itemView.findViewById<View>(R.id.clear_filter_tip)

    init {
      itemView.findViewById<View>(R.id.clear_filter).setOnClickListener { listener() }
    }

    override fun bind(model: T) {
      tip.visible = model.options == ChatFilterOptions.WITH_TIP
    }
  }
}
