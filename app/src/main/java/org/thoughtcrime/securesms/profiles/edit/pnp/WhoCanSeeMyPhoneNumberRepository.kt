package org.thoughtcrime.securesms.profiles.edit.pnp

import io.reactivex.rxjava3.core.Completable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Manages the current phone-number listing state.
 */
class WhoCanSeeMyPhoneNumberRepository {

  fun getCurrentState(): WhoCanSeeMyPhoneNumberState {
    return when (SignalStore.phoneNumberPrivacy().phoneNumberListingMode) {
      PhoneNumberPrivacyValues.PhoneNumberListingMode.LISTED -> WhoCanSeeMyPhoneNumberState.EVERYONE
      PhoneNumberPrivacyValues.PhoneNumberListingMode.UNLISTED -> WhoCanSeeMyPhoneNumberState.NOBODY
    }
  }

  fun onSave(whoCanSeeMyPhoneNumberState: WhoCanSeeMyPhoneNumberState): Completable {
    return Completable.fromAction {
      SignalStore.phoneNumberPrivacy().phoneNumberListingMode = when (whoCanSeeMyPhoneNumberState) {
        WhoCanSeeMyPhoneNumberState.EVERYONE -> PhoneNumberPrivacyValues.PhoneNumberListingMode.LISTED
        WhoCanSeeMyPhoneNumberState.NOBODY -> PhoneNumberPrivacyValues.PhoneNumberListingMode.UNLISTED
      }

      ApplicationDependencies.getJobManager().add(RefreshAttributesJob())
    }
  }
}
