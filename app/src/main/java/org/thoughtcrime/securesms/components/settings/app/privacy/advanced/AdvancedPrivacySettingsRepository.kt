package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.DeviceDeregisteredException
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.IOException
import java.util.concurrent.ExecutionException

private val TAG = Log.tag(AdvancedPrivacySettingsRepository::class.java)

class AdvancedPrivacySettingsRepository(private val context: Context) {

  suspend fun disablePushMessages(): DisablePushMessagesResult = withContext(Dispatchers.IO) {
    val clearTokenError: Throwable? = when (val result = SignalNetwork.account.clearFcmToken()) {
      is RequestResult.Success, is RequestResult.NonSuccess -> null
      is RequestResult.RetryableNetworkError -> result.networkError
      is RequestResult.ApplicationError -> result.cause
    }

    if (clearTokenError != null) {
      Log.w(TAG, clearTokenError)
      if (clearTokenError !is DeviceDeregisteredException) {
        return@withContext DisablePushMessagesResult.NETWORK_ERROR
      }
    }

    try {
      if (SignalStore.account.fcmEnabled) {
        Tasks.await(FirebaseInstallations.getInstance().delete())
      }
      DisablePushMessagesResult.SUCCESS
    } catch (ioe: IOException) {
      Log.w(TAG, ioe)
      DisablePushMessagesResult.NETWORK_ERROR
    } catch (e: InterruptedException) {
      Log.w(TAG, "Interrupted while deleting", e)
      DisablePushMessagesResult.NETWORK_ERROR
    } catch (e: ExecutionException) {
      Log.w(TAG, "Error deleting", e.cause)
      DisablePushMessagesResult.NETWORK_ERROR
    }
  }

  fun syncShowSealedSenderIconState() {
    SignalExecutors.BOUNDED.execute {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          TextSecurePreferences.isReadReceiptsEnabled(context),
          TextSecurePreferences.isTypingIndicatorsEnabled(context),
          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
          SignalStore.settings.isLinkPreviewsEnabled
        )
      )
    }
  }

  enum class DisablePushMessagesResult {
    SUCCESS,
    NETWORK_ERROR
  }
}
