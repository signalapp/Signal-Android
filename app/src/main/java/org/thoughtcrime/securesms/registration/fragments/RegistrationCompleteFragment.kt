package org.thoughtcrime.securesms.registration.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.ActivityNavigator
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity
import org.thoughtcrime.securesms.pin.PinRestoreActivity
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel

/**
 * [RegistrationCompleteFragment] is not visible to the user, but functions as basically a redirect towards one of:
 * - [PIN Restore flow activity](org.thoughtcrime.securesms.pin.PinRestoreActivity)
 * - [Profile](org.thoughtcrime.securesms.profiles.edit.EditProfileActivity) / [PIN creation](org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity) flow activities (this class chains the necessary activities together as an intent)
 * - Exit registration flow and progress to conversation list
 */
class RegistrationCompleteFragment : LoggingFragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.fragment_registration_blank, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val activity = requireActivity()
    val viewModel: RegistrationViewModel by viewModels(ownerProducer = { requireActivity() })

    if (viewModel.isReregister) {
      SignalStore.misc().shouldShowLinkedDevicesReminder = true
    }

    if (SignalStore.storageService().needsAccountRestore()) {
      Log.i(TAG, "Performing pin restore.")
      activity.startActivity(Intent(activity, PinRestoreActivity::class.java))
    } else {
      val isProfileNameEmpty = Recipient.self().profileName.isEmpty
      val isAvatarEmpty = !AvatarHelper.hasAvatar(activity, Recipient.self().id)
      val needsProfile = isProfileNameEmpty || isAvatarEmpty
      val needsPin = !SignalStore.kbsValues().hasPin() && !viewModel.isReregister

      Log.i(TAG, "Pin restore flow not required. Profile name: $isProfileNameEmpty | Profile avatar: $isAvatarEmpty | Needs PIN: $needsPin")

      if (!needsProfile && !needsPin) {
        ApplicationDependencies.getJobManager()
          .startChain(ProfileUploadJob())
          .then(listOf(MultiDeviceProfileKeyUpdateJob(), MultiDeviceProfileContentUpdateJob()))
          .enqueue()
        RegistrationUtil.maybeMarkRegistrationComplete()
      }

      var startIntent = MainActivity.clearTop(activity)

      if (needsPin) {
        startIntent = chainIntents(CreateKbsPinActivity.getIntentForPinCreate(activity), startIntent)
      }

      if (needsProfile) {
        startIntent = chainIntents(EditProfileActivity.getIntentForUserProfile(activity), startIntent)
      }

      activity.startActivity(startIntent)
    }

    activity.finish()
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity)
  }

  private fun chainIntents(sourceIntent: Intent, nextIntent: Intent): Intent {
    sourceIntent.putExtra("next_intent", nextIntent)
    return sourceIntent
  }

  companion object {
    private val TAG = Log.tag(RegistrationCompleteFragment::class.java)
  }
}
