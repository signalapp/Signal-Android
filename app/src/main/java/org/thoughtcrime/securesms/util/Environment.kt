package org.thoughtcrime.securesms.util

import com.google.android.gms.wallet.WalletConstants
import org.signal.donations.GooglePayApi
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.BuildConfig

object Environment {
  private const val GOOGLE_PLAY_BILLING_APPLICATION_ID = "org.thoughtcrime.securesms"

  const val IS_STAGING: Boolean = BuildConfig.BUILD_ENVIRONMENT_TYPE == "Staging" || BuildConfig.BUILD_ENVIRONMENT_TYPE == "Pnp"
  const val IS_NIGHTLY: Boolean = BuildConfig.BUILD_DISTRIBUTION_TYPE == "nightly"
  const val IS_WEBSITE: Boolean = BuildConfig.BUILD_DISTRIBUTION_TYPE == "website"

  object Backups {
    @JvmStatic
    fun supportsGooglePlayBilling(): Boolean {
      return BuildConfig.APPLICATION_ID == GOOGLE_PLAY_BILLING_APPLICATION_ID
    }
  }

  object Donations {
    @JvmStatic
    @get:JvmName("getGooglePayConfiguration")
    val GOOGLE_PAY_CONFIGURATION = GooglePayApi.Configuration(
      walletEnvironment = if (IS_STAGING) WalletConstants.ENVIRONMENT_TEST else WalletConstants.ENVIRONMENT_PRODUCTION
    )

    @JvmStatic
    @get:JvmName("getStripeConfiguration")
    val STRIPE_CONFIGURATION = StripeApi.Configuration(
      baseUrl = BuildConfig.STRIPE_BASE_URL,
      publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
    )
  }

  object Calling {
    @JvmStatic
    fun defaultSfuUrl(): String {
      return if (IS_STAGING) BuildConfig.SIGNAL_STAGING_SFU_URL else BuildConfig.SIGNAL_SFU_URL
    }
  }
}
