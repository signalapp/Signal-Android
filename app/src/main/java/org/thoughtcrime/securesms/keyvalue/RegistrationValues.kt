package org.thoughtcrime.securesms.keyvalue

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.model.databaseprotos.LocalRegistrationMetadata
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies

class RegistrationValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(RegistrationValues::class)

    private const val REGISTRATION_COMPLETE = "registration.complete"
    private const val PIN_REQUIRED = "registration.pin_required"
    private const val HAS_UPLOADED_PROFILE = "registration.has_uploaded_profile"
    private const val SESSION_E164 = "registration.session_e164"
    private const val SESSION_ID = "registration.session_id"
    private const val LOCAL_REGISTRATION_DATA = "registration.local_registration_data"
    private const val RESTORE_METHOD_TOKEN = "registration.restore_method_token"
    private const val IS_OTHER_DEVICE_ANDROID = "registration.is_other_device_android"
    private const val RESTORING_ON_NEW_DEVICE = "registration.restoring_on_new_device"

    @VisibleForTesting
    const val RESTORE_DECISION_STATE = "registration.restore_decision_state"
  }

  @Synchronized
  public override fun onFirstEverAppLaunch() {
    store
      .beginWrite()
      .putBoolean(HAS_UPLOADED_PROFILE, false)
      .putBoolean(REGISTRATION_COMPLETE, false)
      .putBoolean(PIN_REQUIRED, true)
      .putBlob(RESTORE_DECISION_STATE, RestoreDecisionState.Start.encode())
      .commit()
  }

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  @Synchronized
  fun clearRegistrationComplete() {
    onFirstEverAppLaunch()
  }

  @Synchronized
  fun markRegistrationComplete() {
    store
      .beginWrite()
      .putBoolean(REGISTRATION_COMPLETE, true)
      .commit()
  }

  @CheckResult
  @Synchronized
  fun pinWasRequiredAtRegistration(): Boolean {
    return store.getBoolean(PIN_REQUIRED, false)
  }

  @get:Synchronized
  @get:CheckResult
  val isRegistrationComplete: Boolean by booleanValue(REGISTRATION_COMPLETE, true)

  var localRegistrationMetadata: LocalRegistrationMetadata? by protoValue(LOCAL_REGISTRATION_DATA, LocalRegistrationMetadata.ADAPTER)

  @get:JvmName("hasUploadedProfile")
  var hasUploadedProfile: Boolean by booleanValue(HAS_UPLOADED_PROFILE, true)
  var sessionId: String? by stringValue(SESSION_ID, null)
  var sessionE164: String? by stringValue(SESSION_E164, null)

  var isOtherDeviceAndroid: Boolean by booleanValue(IS_OTHER_DEVICE_ANDROID, false)
  var restoreMethodToken: String? by stringValue(RESTORE_METHOD_TOKEN, null)

  @get:JvmName("isRestoringOnNewDevice")
  var restoringOnNewDevice: Boolean by booleanValue(RESTORING_ON_NEW_DEVICE, false)

  var restoreDecisionState: RestoreDecisionState
    get() = store.getBlob(RESTORE_DECISION_STATE, null)?.let { RestoreDecisionState.ADAPTER.decode(it) } ?: RestoreDecisionState.Skipped
    set(newValue) {
      if (isRegistrationComplete) {
        Log.w(TAG, "Registration was completed, cannot change initial restore decision state")
      } else {
        Log.v(TAG, "Restore decision set: $newValue", Throwable())
        store.beginWrite()
          .putBlob(RESTORE_DECISION_STATE, newValue.encode())
          .apply()
        AppDependencies.incomingMessageObserver.notifyRegistrationStateChanged()
      }
    }
}
