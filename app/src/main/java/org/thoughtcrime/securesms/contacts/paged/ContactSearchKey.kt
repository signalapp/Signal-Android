package org.thoughtcrime.securesms.contacts.paged

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.ShareContact

/**
 * Represents a row in a list of Contact results.
 */
sealed class ContactSearchKey {

  /**
   * Generates a ShareContact object used to display which contacts have been selected. This should *not*
   * be used for the final sharing process, as it is not always truthful about, for example,
   * a group vs. a group's Story.
   */
  open fun requireShareContact(): ShareContact = error("This key cannot be converted into a ShareContact")

  open fun requireRecipientSearchKey(): RecipientSearchKey = error("This key cannot be parcelized")

  open fun requireSelectedContact(): SelectedContact = error("This key cannot be converted into a SelectedContact")

  @Parcelize
  data class RecipientSearchKey(val recipientId: RecipientId, val isStory: Boolean) : ContactSearchKey(), Parcelable {
    override fun requireRecipientSearchKey(): RecipientSearchKey = this

    override fun requireShareContact(): ShareContact = ShareContact(recipientId)

    override fun requireSelectedContact(): SelectedContact = SelectedContact.forRecipientId(recipientId)
  }

  data class UnknownRecipientKey(val sectionKey: ContactSearchConfiguration.SectionKey, val query: String) : ContactSearchKey() {
    override fun requireSelectedContact(): SelectedContact = when (sectionKey) {
      ContactSearchConfiguration.SectionKey.USERNAME -> SelectedContact.forUsername(null, query)
      ContactSearchConfiguration.SectionKey.PHONE_NUMBER -> SelectedContact.forPhone(null, query)
      else -> error("Unexpected section for unknown recipient: $sectionKey")
    }

    override fun toString(): String {
      return "UnknownRecipientKey(sectionKey=$sectionKey)"
    }
  }

  /**
   * Key to a header for a given section
   */
  data class Header(val sectionKey: ContactSearchConfiguration.SectionKey) : ContactSearchKey()

  /**
   * Key to an expand button for a given section
   */
  data class Expand(val sectionKey: ContactSearchConfiguration.SectionKey) : ContactSearchKey()

  /**
   * Arbitrary takes a string type and will map to exactly one ArbitraryData object.
   *
   * This is used to allow arbitrary extra data to be added to the contact search system.
   */
  data class Arbitrary(val type: String) : ContactSearchKey()

  /**
   * Search key for a ThreadRecord
   */
  data class Thread(val threadId: Long) : ContactSearchKey()

  /**
   * Search key for [ContactSearchData.GroupWithMembers]
   */
  data class GroupWithMembers(val groupId: GroupId) : ContactSearchKey()

  /**
   * Search key for a MessageRecord
   */
  data class Message(val messageId: Long) : ContactSearchKey()

  /**
   * Search key for a ChatType
   */
  data class ChatTypeSearchKey(val chatType: ChatType) : ContactSearchKey() {
    override fun requireSelectedContact(): SelectedContact {
      return SelectedContact.forChatType(chatType)
    }
  }

  object Empty : ContactSearchKey()
}
