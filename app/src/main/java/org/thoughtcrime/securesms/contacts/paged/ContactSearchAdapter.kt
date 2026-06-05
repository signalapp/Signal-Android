package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.view.View
import org.signal.core.ui.compose.FastScrollCharacterProvider
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller.FastScrollAdapter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter

/**
 * Default contact search adapter. View holders, mapping models, and the helpers that register them
 * live in [ContactSearchModels].
 */
@Suppress("LeakingThis")
open class ContactSearchAdapter(
  private val context: Context,
  fixedContacts: Set<ContactSearchKey>,
  displayOptions: DisplayOptions,
  onClickCallbacks: ClickCallbacks,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks,
  callButtonClickCallbacks: CallButtonClickCallbacks
) : PagingMappingAdapter<ContactSearchKey>(), FastScrollAdapter {

  init {
    ContactSearchModels.registerStoryItems(this, displayOptions.displayCheckBox, onClickCallbacks::onStoryClicked, storyContextMenuCallbacks, displayOptions.displayStoryRing)
    ContactSearchModels.registerKnownRecipientItems(this, fixedContacts, displayOptions, onClickCallbacks::onKnownRecipientClicked, longClickCallbacks::onKnownRecipientLongClick, callButtonClickCallbacks)
    ContactSearchModels.registerHeaders(this)
    ContactSearchModels.registerExpands(this, onClickCallbacks::onExpandClicked)
    ContactSearchModels.registerChatTypeItems(this, onClickCallbacks::onChatTypeClicked)
    ContactSearchModels.registerUnknownRecipientItems(this, onClickCallbacks::onUnknownRecipientClicked, displayOptions.displayCheckBox)
  }

  override fun getBubbleText(position: Int): CharSequence {
    val model = getItem(position)
    return if (model is FastScrollCharacterProvider) {
      model.getFastScrollCharacter(context)
    } else {
      " "
    }
  }

  interface StoryContextMenuCallbacks {
    fun onOpenStorySettings(story: ContactSearchData.Story)
    fun onRemoveGroupStory(story: ContactSearchData.Story, isSelected: Boolean)
    fun onDeletePrivateStory(story: ContactSearchData.Story, isSelected: Boolean)
  }

  /**
   * Whether or not we should display a recipient's 'about' or e164, if either are
   * available.
   */
  enum class DisplaySecondaryInformation {
    NEVER,
    ALWAYS
  }

  data class DisplayOptions(
    val displayCheckBox: Boolean = false,
    val displaySecondaryInformation: DisplaySecondaryInformation = DisplaySecondaryInformation.NEVER,
    val displayCallButtons: Boolean = false,
    val displayStoryRing: Boolean = false
  )

  fun interface OnClickedCallback<D : ContactSearchData> {
    fun onClicked(view: View, data: D, isSelected: Boolean)
  }

  fun interface OnLongClickedCallback<D : ContactSearchData> {
    fun onLongClicked(view: View, data: D): Boolean
  }

  interface ClickCallbacks {
    fun onStoryClicked(view: View, story: ContactSearchData.Story, isSelected: Boolean)
    fun onKnownRecipientClicked(view: View, knownRecipient: ContactSearchData.KnownRecipient, isSelected: Boolean)
    fun onExpandClicked(expand: ContactSearchData.Expand)
    fun onUnknownRecipientClicked(view: View, unknownRecipient: ContactSearchData.UnknownRecipient, isSelected: Boolean) {
      throw NotImplementedError()
    }

    fun onChatTypeClicked(view: View, chatTypeRow: ContactSearchData.ChatTypeRow, isSelected: Boolean)
  }

  interface CallButtonClickCallbacks {
    fun onVideoCallButtonClicked(recipient: Recipient)
    fun onAudioCallButtonClicked(recipient: Recipient)
  }

  object EmptyCallButtonClickCallbacks : CallButtonClickCallbacks {
    override fun onVideoCallButtonClicked(recipient: Recipient) = Unit
    override fun onAudioCallButtonClicked(recipient: Recipient) = Unit
  }

  interface LongClickCallbacks {
    fun onKnownRecipientLongClick(view: View, data: ContactSearchData.KnownRecipient): Boolean
  }

  class LongClickCallbacksAdapter : LongClickCallbacks {
    override fun onKnownRecipientLongClick(view: View, data: ContactSearchData.KnownRecipient): Boolean = false
  }

  /**
   * Creates a [PagingMappingAdapter] backed by [ContactSearchAdapter] (or a subclass).
   * Pass a custom implementation to inject alternative adapters for testing or specialised UIs.
   */
  fun interface AdapterFactory {
    fun create(
      context: Context,
      fixedContacts: Set<ContactSearchKey>,
      displayOptions: DisplayOptions,
      callbacks: ClickCallbacks,
      longClickCallbacks: LongClickCallbacks,
      storyContextMenuCallbacks: StoryContextMenuCallbacks,
      callButtonClickCallbacks: CallButtonClickCallbacks
    ): PagingMappingAdapter<ContactSearchKey>
  }

  /** Standard implementation that creates a plain [ContactSearchAdapter]. */
  object DefaultAdapterFactory : AdapterFactory {
    override fun create(
      context: Context,
      fixedContacts: Set<ContactSearchKey>,
      displayOptions: DisplayOptions,
      callbacks: ClickCallbacks,
      longClickCallbacks: LongClickCallbacks,
      storyContextMenuCallbacks: StoryContextMenuCallbacks,
      callButtonClickCallbacks: CallButtonClickCallbacks
    ): PagingMappingAdapter<ContactSearchKey> {
      return ContactSearchAdapter(context, fixedContacts, displayOptions, callbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks)
    }
  }
}
