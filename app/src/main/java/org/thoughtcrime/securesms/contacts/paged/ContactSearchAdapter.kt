package org.thoughtcrime.securesms.contacts.paged

import android.view.View
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter

/**
 * Default contact search adapter, using the models defined in `ContactSearchItems`
 */
class ContactSearchAdapter(
  displayCheckBox: Boolean,
  displaySmsTag: ContactSearchItems.DisplaySmsTag,
  recipientListener: (View, ContactSearchData.KnownRecipient, Boolean) -> Unit,
  storyListener: (View, ContactSearchData.Story, Boolean) -> Unit,
  storyContextMenuCallbacks: ContactSearchItems.StoryContextMenuCallbacks,
  expandListener: (ContactSearchData.Expand) -> Unit
) : PagingMappingAdapter<ContactSearchKey>() {
  init {
    ContactSearchItems.registerStoryItems(this, displayCheckBox, storyListener, storyContextMenuCallbacks)
    ContactSearchItems.registerKnownRecipientItems(this, displayCheckBox, displaySmsTag, recipientListener)
    ContactSearchItems.registerHeaders(this)
    ContactSearchItems.registerExpands(this, expandListener)
  }
}
