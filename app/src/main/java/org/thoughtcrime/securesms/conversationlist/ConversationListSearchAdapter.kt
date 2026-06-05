package org.thoughtcrime.securesms.conversationlist

import android.content.Context
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversationlist.ConversationListSearchModels.ChatFilterEmptyMappingModel
import org.thoughtcrime.securesms.conversationlist.ConversationListSearchModels.ChatFilterMappingModel
import org.thoughtcrime.securesms.conversationlist.ConversationListSearchModels.ChatFilterOptions
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

/**
 * Adapter for ConversationList search. Adds factories to render ThreadModel and MessageModel using ConversationListItem,
 * as well as ChatFilter row support and empty state handler.
 */
class ConversationListSearchAdapter(
  context: Context,
  fixedContacts: Set<ContactSearchKey>,
  displayOptions: DisplayOptions,
  onClickedCallbacks: ConversationListSearchClickCallbacks,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks,
  callButtonClickCallbacks: CallButtonClickCallbacks,
  lifecycleOwner: LifecycleOwner,
  requestManager: RequestManager
) : ContactSearchAdapter(context, fixedContacts, displayOptions, onClickedCallbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks), TimestampPayloadSupport {

  init {
    ConversationListSearchModels.registerThreads(this, onClickedCallbacks::onThreadClicked, onClickedCallbacks::onThreadLongClicked, lifecycleOwner, requestManager)
    ConversationListSearchModels.registerMessages(this, onClickedCallbacks::onMessageClicked, lifecycleOwner, requestManager)
    ConversationListSearchModels.registerGroupsWithMembers(this, onClickedCallbacks::onGroupWithMembersClicked, lifecycleOwner, requestManager)
    ConversationListSearchModels.registerEmpty(this)
    ConversationListSearchModels.registerChatFilters(this, onClickedCallbacks::onClearFilterClicked)
  }

  override fun notifyTimestampPayloadUpdate() {
    notifyItemRangeChanged(0, itemCount, ConversationListSearchModels.PAYLOAD_TIMESTAMP)
  }

  class ChatFilterRepository : ArbitraryRepository {
    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int = section.types.size

    override fun getData(
      section: ContactSearchConfiguration.Section.Arbitrary,
      query: String?,
      startIndex: Int,
      endIndex: Int,
      totalSearchSize: Int
    ): List<ContactSearchData.Arbitrary> {
      return section.types.map {
        ContactSearchData.Arbitrary(it, bundleOf("total-size" to totalSearchSize))
      }
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      val options = ChatFilterOptions.fromCode(arbitrary.type)
      val totalSearchSize = arbitrary.data?.getInt("total-size", -1) ?: -1
      return if (totalSearchSize == 1) {
        ChatFilterEmptyMappingModel(options)
      } else {
        ChatFilterMappingModel(options)
      }
    }
  }

  interface ConversationListSearchClickCallbacks : ClickCallbacks {
    fun onThreadClicked(view: View, thread: ContactSearchData.Thread, isSelected: Boolean)
    fun onThreadLongClicked(view: View, thread: ContactSearchData.Thread): Boolean
    fun onMessageClicked(view: View, thread: ContactSearchData.Message, isSelected: Boolean)
    fun onGroupWithMembersClicked(view: View, groupWithMembers: ContactSearchData.GroupWithMembers, isSelected: Boolean)
    fun onClearFilterClicked()
  }
}
