package org.thoughtcrime.securesms.mediasend.v2.text.send

import io.reactivex.rxjava3.core.Completable
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationState

class TextStoryPostSendRepository {

  fun isFirstSendToStory(shareContacts: Set<ContactSearchKey>): Boolean {
    if (SignalStore.storyValues().userHasAddedToAStory) {
      return false
    }

    return shareContacts.any { it is ContactSearchKey.Story }
  }

  fun send(contactSearchKey: Set<ContactSearchKey>, textStoryPostCreationState: TextStoryPostCreationState, linkPreview: LinkPreview?): Completable {
    // TODO [stories] -- Implementation once we know what text post messages look like.
    return Completable.complete()
  }
}
