package org.thoughtcrime.securesms.conversation.mutiselect.forward

import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState

interface SearchConfigurationProvider {
  fun getSearchConfiguration(contactSearchState: ContactSearchState): ContactSearchConfiguration? = null
}
