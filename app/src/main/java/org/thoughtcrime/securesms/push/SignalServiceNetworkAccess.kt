package org.thoughtcrime.securesms.push

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.TlsVersion
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.CustomDns
import org.thoughtcrime.securesms.net.DeprecatedClientPreventionInterceptor
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.net.RemoteDeprecationDetectorInterceptor
import org.thoughtcrime.securesms.net.SequentialDns
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor
import org.thoughtcrime.securesms.net.StaticDns
import org.thoughtcrime.securesms.util.Base64
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl
import java.io.IOException
import java.util.Optional

/**
 * Provides a [SignalServiceConfiguration] to be used with our service layer.
 * If you're looking for a place to start, look at [getConfiguration].
 */
open class SignalServiceNetworkAccess(context: Context) {
  companion object {
    private val TAG = Log.tag(SignalServiceNetworkAccess::class.java)

    @JvmField
    val DNS: Dns = SequentialDns(
      Dns.SYSTEM,
      CustomDns("1.1.1.1"),
      StaticDns(
        mapOf(
          BuildConfig.SIGNAL_URL.stripProtocol() to BuildConfig.SIGNAL_SERVICE_IPS.toSet(),
          BuildConfig.STORAGE_URL.stripProtocol() to BuildConfig.SIGNAL_STORAGE_IPS.toSet(),
          BuildConfig.SIGNAL_CDN_URL.stripProtocol() to BuildConfig.SIGNAL_CDN_IPS.toSet(),
          BuildConfig.SIGNAL_CDN2_URL.stripProtocol() to BuildConfig.SIGNAL_CDN2_IPS.toSet(),
          BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL.stripProtocol() to BuildConfig.SIGNAL_CDS_IPS.toSet(),
          BuildConfig.SIGNAL_KEY_BACKUP_URL.stripProtocol() to BuildConfig.SIGNAL_KBS_IPS.toSet(),
          BuildConfig.SIGNAL_SFU_URL.stripProtocol() to BuildConfig.SIGNAL_SFU_IPS.toSet(),
          BuildConfig.CONTENT_PROXY_HOST.stripProtocol() to BuildConfig.SIGNAL_CONTENT_PROXY_IPS.toSet()
        )
      )
    )

    private fun String.stripProtocol(): String {
      return this.removePrefix("https://")
    }

    private const val COUNTRY_CODE_EGYPT = 20
    private const val COUNTRY_CODE_UAE = 971
    private const val COUNTRY_CODE_OMAN = 968
    private const val COUNTRY_CODE_QATAR = 974
    private const val COUNTRY_CODE_IRAN = 98
    private const val COUNTRY_CODE_CUBA = 53
    private const val COUNTRY_CODE_UZBEKISTAN = 998
    private const val COUNTRY_CODE_UKRAINE = 380

    private const val G_HOST = "reflector-nrgwuv7kwq-uc.a.run.app"
    private const val F_SERVICE_HOST = "chat-signal.global.ssl.fastly.net"
    private const val F_STORAGE_HOST = "storage.signal.org.global.prod.fastly.net"
    private const val F_CDN_HOST = "cdn.signal.org.global.prod.fastly.net"
    private const val F_CDN2_HOST = "cdn2.signal.org.global.prod.fastly.net"
    private const val F_CDSI_HOST = "cdsi-signal.global.ssl.fastly.net"
    private const val F_KBS_HOST = "api.backup.signal.org.global.prod.fastly.net"

    private val GMAPS_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val GMAIL_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val PLAY_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val APP_CONNECTION_SPEC = ConnectionSpec.MODERN_TLS
  }

  private val serviceTrustStore: TrustStore = SignalServiceTrustStore(context)
  private val gTrustStore: TrustStore = DomainFrontingTrustStore(context)
  private val fTrustStore: TrustStore = DomainFrontingDigicertTrustStore(context)

  private val interceptors: List<Interceptor> = listOf(
    StandardUserAgentInterceptor(),
    RemoteDeprecationDetectorInterceptor(),
    DeprecatedClientPreventionInterceptor(),
    DeviceTransferBlockingInterceptor.getInstance()
  )

  private val zkGroupServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val baseGHostConfigs: List<HostConfig> = listOf(
    HostConfig("https://www.google.com", G_HOST, GMAIL_CONNECTION_SPEC),
    HostConfig("https://android.clients.google.com", G_HOST, PLAY_CONNECTION_SPEC),
    HostConfig("https://clients3.google.com", G_HOST, GMAPS_CONNECTION_SPEC),
    HostConfig("https://clients4.google.com", G_HOST, GMAPS_CONNECTION_SPEC),
    HostConfig("https://inbox.google.com", G_HOST, GMAIL_CONNECTION_SPEC)
  )

  private val fUrls = arrayOf("https://cdn.sstatic.net", "https://github.githubassets.com", "https://pinterest.com", "https://open.scdn.co", "https://www.redditstatic.com")

  private val fConfig: SignalServiceConfiguration = SignalServiceConfiguration(
    fUrls.map { SignalServiceUrl(it, F_SERVICE_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    mapOf(
      0 to fUrls.map { SignalCdnUrl(it, F_CDN_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
      2 to fUrls.map { SignalCdnUrl(it, F_CDN2_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray()
    ),
    fUrls.map { SignalKeyBackupServiceUrl(it, F_KBS_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    fUrls.map { SignalStorageUrl(it, F_STORAGE_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    fUrls.map { SignalCdsiUrl(it, F_CDSI_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    interceptors,
    Optional.of(DNS),
    Optional.empty(),
    zkGroupServerPublicParams
  )

  private val censorshipConfiguration: Map<Int, SignalServiceConfiguration> = mapOf(
    COUNTRY_CODE_EGYPT to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.eg", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UAE to buildGConfiguration(
      listOf(HostConfig("https://www.google.ae", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_OMAN to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.om", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_QATAR to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.qa", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UZBEKISTAN to buildGConfiguration(
      listOf(HostConfig("https://www.google.co.uz", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UKRAINE to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.ua", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_IRAN to fConfig,
    COUNTRY_CODE_CUBA to fConfig
  )

  private val defaultCensoredConfiguration: SignalServiceConfiguration = buildGConfiguration(baseGHostConfigs)

  private val defaultCensoredCountryCodes: Set<Int> = setOf(
    COUNTRY_CODE_EGYPT,
    COUNTRY_CODE_UAE,
    COUNTRY_CODE_OMAN,
    COUNTRY_CODE_QATAR,
    COUNTRY_CODE_IRAN,
    COUNTRY_CODE_CUBA,
    COUNTRY_CODE_UZBEKISTAN
  )

  open val uncensoredConfiguration: SignalServiceConfiguration = SignalServiceConfiguration(
    arrayOf(SignalServiceUrl(BuildConfig.SIGNAL_URL, serviceTrustStore)),
    mapOf(
      0 to arrayOf(SignalCdnUrl(BuildConfig.SIGNAL_CDN_URL, serviceTrustStore)),
      2 to arrayOf(SignalCdnUrl(BuildConfig.SIGNAL_CDN2_URL, serviceTrustStore))
    ),
    arrayOf(SignalKeyBackupServiceUrl(BuildConfig.SIGNAL_KEY_BACKUP_URL, serviceTrustStore)),
    arrayOf(SignalStorageUrl(BuildConfig.STORAGE_URL, serviceTrustStore)),
    arrayOf(SignalCdsiUrl(BuildConfig.SIGNAL_CDSI_URL, serviceTrustStore)),
    interceptors,
    Optional.of(DNS),
    if (SignalStore.proxy().isProxyEnabled) Optional.ofNullable(SignalStore.proxy().proxy) else Optional.empty(),
    zkGroupServerPublicParams
  )

  open fun getConfiguration(): SignalServiceConfiguration {
    return getConfiguration(SignalStore.account().e164)
  }

  open fun getConfiguration(e164: String?): SignalServiceConfiguration {
    if (e164 == null || SignalStore.proxy().isProxyEnabled) {
      return uncensoredConfiguration
    }

    val countryCode: Int = PhoneNumberUtil.getInstance().parse(e164, null).countryCode

    return when (SignalStore.settings().censorshipCircumventionEnabled) {
      SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        censorshipConfiguration[countryCode] ?: defaultCensoredConfiguration
      }
      SettingsValues.CensorshipCircumventionEnabled.DISABLED -> {
        uncensoredConfiguration
      }
      SettingsValues.CensorshipCircumventionEnabled.DEFAULT -> {
        if (defaultCensoredCountryCodes.contains(countryCode)) {
          censorshipConfiguration[countryCode] ?: defaultCensoredConfiguration
        } else {
          uncensoredConfiguration
        }
      }
    }
  }

  fun isCensored(): Boolean {
    return isCensored(SignalStore.account().e164)
  }

  fun isCensored(number: String?): Boolean {
    return getConfiguration(number) != uncensoredConfiguration
  }

  fun isCountryCodeCensoredByDefault(countryCode: Int): Boolean {
    return defaultCensoredCountryCodes.contains(countryCode)
  }

  private fun buildGConfiguration(
    hostConfigs: List<HostConfig>
  ): SignalServiceConfiguration {
    val serviceUrls: Array<SignalServiceUrl> = hostConfigs.map { SignalServiceUrl("${it.baseUrl}/service", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdnUrls: Array<SignalCdnUrl> = hostConfigs.map { SignalCdnUrl("${it.baseUrl}/cdn", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdn2Urls: Array<SignalCdnUrl> = hostConfigs.map { SignalCdnUrl("${it.baseUrl}/cdn2", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val kbsUrls: Array<SignalKeyBackupServiceUrl> = hostConfigs.map { SignalKeyBackupServiceUrl("${it.baseUrl}/backup", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val storageUrls: Array<SignalStorageUrl> = hostConfigs.map { SignalStorageUrl("${it.baseUrl}/storage", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdsiUrls: Array<SignalCdsiUrl> = hostConfigs.map { SignalCdsiUrl("${it.baseUrl}/cdsi", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()

    return SignalServiceConfiguration(
      serviceUrls,
      mapOf(
        0 to cdnUrls,
        2 to cdn2Urls
      ),
      kbsUrls,
      storageUrls,
      cdsiUrls,
      interceptors,
      Optional.of(DNS),
      Optional.empty(),
      zkGroupServerPublicParams
    )
  }

  private data class HostConfig(val baseUrl: String, val host: String, val connectionSpec: ConnectionSpec)
}
