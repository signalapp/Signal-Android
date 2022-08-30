package org.thoughtcrime.securesms.components.settings.app.chats.sms

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.util.FeatureFlags

class SmsSettingsRepository {
  fun getSmsExportState(): Single<SmsSettingsState.SmsExportState> {
    if (!FeatureFlags.smsExporter()) {
      return Single.just(SmsSettingsState.SmsExportState.NOT_AVAILABLE)
    }

    return Single.fromCallable {
      checkInsecureMessageCount() ?: checkUnexportedInsecureMessageCount()
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  private fun checkInsecureMessageCount(): SmsSettingsState.SmsExportState? {
    val smsCount = SignalDatabase.sms.insecureMessageCount
    val mmsCount = SignalDatabase.mms.insecureMessageCount
    val totalSmsMmsCount = smsCount + mmsCount

    return if (totalSmsMmsCount == 0) {
      SmsSettingsState.SmsExportState.NO_SMS_MESSAGES_IN_DATABASE
    } else {
      null
    }
  }

  @WorkerThread
  private fun checkUnexportedInsecureMessageCount(): SmsSettingsState.SmsExportState {
    val unexportedSmsCount = SignalDatabase.sms.unexportedInsecureMessages.use { it.count }
    val unexportedMmsCount = SignalDatabase.mms.unexportedInsecureMessages.use { it.count }
    val totalUnexportedCount = unexportedSmsCount + unexportedMmsCount

    return if (totalUnexportedCount > 0) {
      SmsSettingsState.SmsExportState.HAS_UNEXPORTED_MESSAGES
    } else {
      SmsSettingsState.SmsExportState.ALL_MESSAGES_EXPORTED
    }
  }
}
