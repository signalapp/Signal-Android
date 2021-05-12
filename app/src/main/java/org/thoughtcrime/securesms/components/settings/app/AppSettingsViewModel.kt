package org.thoughtcrime.securesms.components.settings.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil

class AppSettingsViewModel : ViewModel() {

  val unreadPaymentsLiveData = UnreadPaymentsLiveData()
  val selfLiveData: LiveData<Recipient> = Recipient.self().live().liveData

  val state: LiveData<AppSettingsState> = LiveDataUtil.combineLatest(unreadPaymentsLiveData, selfLiveData) { payments, self ->
    val unreadPaymentsCount = payments.transform { it.unreadCount }.or(0)

    AppSettingsState(self, unreadPaymentsCount)
  }
}
