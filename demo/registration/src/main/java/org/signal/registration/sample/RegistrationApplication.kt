/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample

import android.app.Application
import android.os.Build
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.Base64
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationDependencies
import org.signal.registration.sample.debug.DebugNetworkController
import org.signal.registration.sample.dependencies.RealNetworkController
import org.signal.registration.sample.dependencies.RealStorageController
import org.signal.registration.sample.storage.RegistrationPreferences
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl
import org.whispersystems.signalservice.internal.configuration.SignalSvr2Url
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.InputStream
import java.util.Optional

class RegistrationApplication : Application() {

  companion object {
    // Staging SVR2 mrEnclave value
    private const val SVR2_MRENCLAVE = "a75542d82da9f6914a1e31f8a7407053b99cc99a0e7291d8fbd394253e19b036"
  }

  override fun onCreate() {
    super.onCreate()

    Log.initialize(AndroidLogger)

    RegistrationPreferences.init(this)

    val trustStore = SampleTrustStore()
    val configuration = createServiceConfiguration(trustStore)
    val pushServiceSocket = createPushServiceSocket(configuration)
    val realNetworkController = RealNetworkController(this, pushServiceSocket, configuration, SVR2_MRENCLAVE)
    val networkController = DebugNetworkController(realNetworkController)
    val storageController = RealStorageController(this)

    RegistrationDependencies.provide(
      RegistrationDependencies(
        networkController = networkController,
        storageController = storageController
      )
    )
  }

  private fun createPushServiceSocket(configuration: SignalServiceConfiguration): PushServiceSocket {
    val credentialsProvider = NoopCredentialsProvider()
    val signalAgent = "Signal-Android/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}"

    return PushServiceSocket(
      configuration,
      credentialsProvider,
      signalAgent,
      true // automaticNetworkRetry
    )
  }

  private fun createServiceConfiguration(trustStore: TrustStore): SignalServiceConfiguration {
    return SignalServiceConfiguration(
      signalServiceUrls = arrayOf(SignalServiceUrl("https://chat.staging.signal.org", trustStore)),
      signalCdnUrlMap = mapOf(
        0 to arrayOf(SignalCdnUrl("https://cdn-staging.signal.org", trustStore)),
        2 to arrayOf(SignalCdnUrl("https://cdn2-staging.signal.org", trustStore)),
        3 to arrayOf(SignalCdnUrl("https://cdn3-staging.signal.org", trustStore))
      ),
      signalStorageUrls = arrayOf(SignalStorageUrl("https://storage-staging.signal.org", trustStore)),
      signalCdsiUrls = arrayOf(SignalCdsiUrl("https://cdsi.staging.signal.org", trustStore)),
      signalSvr2Urls = arrayOf(SignalSvr2Url("https://svr2.staging.signal.org", trustStore)),
      networkInterceptors = emptyList(),
      dns = Optional.empty(),
      signalProxy = Optional.empty(),
      systemHttpProxy = Optional.empty(),
      zkGroupServerPublicParams = Base64.decode("ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUjlENAErBme1YHmOSpU6tr6doJ66dPzVAWIanmO/5mgjNEDeK7DDqQdB1xd03HT2Qs2TxY3kCK8aAb/0iM0HQiXjxZ9HIgYhbtvGEnDKW5ILSUydqH/KBhW4Pb0jZWnqN/YgbWDKeJxnDbYcUob5ZY5Lt5ZCMKuaGUvCJRrCtuugSMaqjowCGRempsDdJEt+cMaalhZ6gczklJB/IbdwENW9KeVFPoFNFzhxWUIS5ML9riVYhAtE6JE5jX0xiHNVIIPthb458cfA8daR0nYfYAUKogQArm0iBezOO+mPk5vCNWI+wwkyFCqNDXz/qxl1gAntuCJtSfq9OC3NkdhQlgYQ=="),
      genericServerPublicParams = Base64.decode("AHILOIrFPXX9laLbalbA9+L1CXpSbM/bTJXZGZiuyK1JaI6dK5FHHWL6tWxmHKYAZTSYmElmJ5z2A5YcirjO/yfoemE03FItyaf8W1fE4p14hzb5qnrmfXUSiAIVrhaXVwIwSzH6RL/+EO8jFIjJ/YfExfJ8aBl48CKHgu1+A6kWynhttonvWWx6h7924mIzW0Czj2ROuh4LwQyZypex4GuOPW8sgIT21KNZaafgg+KbV7XM1x1tF3XA17B4uGUaDbDw2O+nR1+U5p6qHPzmJ7ggFjSN6Utu+35dS1sS0P9N"),
      backupServerPublicParams = Base64.decode("AHYrGb9IfugAAJiPKp+mdXUx+OL9zBolPYHYQz6GI1gWjpEu5me3zVNSvmYY4zWboZHif+HG1sDHSuvwFd0QszSwuSF4X4kRP3fJREdTZ5MCR0n55zUppTwfHRW2S4sdQ0JGz7YDQIJCufYSKh0pGNEHL6hv79Agrdnr4momr3oXdnkpVBIp3HWAQ6IbXQVSG18X36GaicI1vdT0UFmTwU2KTneluC2eyL9c5ff8PcmiS+YcLzh0OKYQXB5ZfQ06d6DiINvDQLy75zcfUOniLAj0lGJiHxGczin/RXisKSR8"),
      censored = false
    )
  }

  private inner class SampleTrustStore : TrustStore {
    override fun getKeyStoreInputStream(): InputStream {
      return resources.openRawResource(R.raw.whisper)
    }

    override fun getKeyStorePassword(): String {
      return "whisper"
    }
  }

  private class NoopCredentialsProvider : CredentialsProvider {
    override fun getAci(): ACI? = null
    override fun getPni(): PNI? = null
    override fun getE164(): String? = null
    override fun getDeviceId(): Int = 1
    override fun getPassword(): String? = null
  }
}
