package org.signal.benchmark

import android.content.Context
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.account.PreKeyUpload
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import java.io.IOException
import java.util.Optional

class DummyAccountManagerFactory : AccountManagerFactory() {
  override fun createAuthenticated(context: Context, aci: ACI, pni: PNI, number: String, deviceId: Int, password: String): SignalServiceAccountManager {
    return DummyAccountManager(
      ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(number),
      aci,
      pni,
      number,
      deviceId,
      password,
      BuildConfig.SIGNAL_AGENT,
      FeatureFlags.okHttpAutomaticRetry(),
      FeatureFlags.groupLimits().hardLimit
    )
  }

  private class DummyAccountManager(configuration: SignalServiceConfiguration?, aci: ACI?, pni: PNI?, e164: String?, deviceId: Int, password: String?, signalAgent: String?, automaticNetworkRetry: Boolean, maxGroupSize: Int) : SignalServiceAccountManager(configuration, aci, pni, e164, deviceId, password, signalAgent, automaticNetworkRetry, maxGroupSize) {
    @Throws(IOException::class)
    override fun setGcmId(gcmRegistrationId: Optional<String>) {
    }

    @Throws(IOException::class)
    override fun setPreKeys(preKeyUpload: PreKeyUpload) {
    }
  }
}
