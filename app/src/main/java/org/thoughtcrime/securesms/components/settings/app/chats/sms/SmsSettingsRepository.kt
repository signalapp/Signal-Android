package org.thoughtcrime.securesms.components.settings.app.chats.sms

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.util.FeatureFlags

class SmsSettingsRepository(
  private val smsDatabase: MessageTable = SignalDatabase.sms,
  private val mmsDatabase: MessageTable = SignalDatabase.mms
) {
  fun getSmsExportState(): Single<SmsExportState> {
    if (!FeatureFlags.smsExporter()) {
      return Single.just(SmsExportState.NOT_AVAILABLE)
    }

    return Single.fromCallable {
      checkInsecureMessageCount() ?: checkUnexportedInsecureMessageCount()
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  private fun checkInsecureMessageCount(): SmsExportState? {
    val totalSmsMmsCount = smsDatabase.insecureMessageCount + mmsDatabase.insecureMessageCount

    return if (totalSmsMmsCount == 0) {
      SmsExportState.NO_SMS_MESSAGES_IN_DATABASE
    } else {
      null
    }
  }

  @WorkerThread
  private fun checkUnexportedInsecureMessageCount(): SmsExportState {
    val totalUnexportedCount = smsDatabase.unexportedInsecureMessagesCount + mmsDatabase.unexportedInsecureMessagesCount

    return if (totalUnexportedCount > 0) {
      SmsExportState.HAS_UNEXPORTED_MESSAGES
    } else {
      SmsExportState.ALL_MESSAGES_EXPORTED
    }
  }
}
