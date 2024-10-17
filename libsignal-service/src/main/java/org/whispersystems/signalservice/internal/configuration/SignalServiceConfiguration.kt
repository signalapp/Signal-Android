package org.whispersystems.signalservice.internal.configuration

import okhttp3.Dns
import okhttp3.Interceptor
import java.util.Optional

/**
 * Defines all network configuration needed to connect to the Signal service.
 */
@Suppress("ArrayInDataClass") // Using data class for .copy(), don't care about equals/hashcode
data class SignalServiceConfiguration(
  val signalServiceUrls: Array<SignalServiceUrl>,
  val signalCdnUrlMap: Map<Int, Array<SignalCdnUrl>>,
  val signalStorageUrls: Array<SignalStorageUrl>,
  val signalCdsiUrls: Array<SignalCdsiUrl>,
  val signalSvr2Urls: Array<SignalSvr2Url>,
  val networkInterceptors: List<Interceptor>,
  val dns: Optional<Dns>,
  val signalProxy: Optional<SignalProxy>,
  val zkGroupServerPublicParams: ByteArray,
  val genericServerPublicParams: ByteArray,
  val backupServerPublicParams: ByteArray
) {

  /** Convenience operator overload for combining the URL lists. Does not add the other fields together, as those wouldn't make sense.  */
  operator fun plus(other: SignalServiceConfiguration): SignalServiceConfiguration {
    return this.copy(
      signalServiceUrls = signalServiceUrls + other.signalServiceUrls,
      signalCdnUrlMap = signalCdnUrlMap + other.signalCdnUrlMap,
      signalStorageUrls = signalStorageUrls + other.signalStorageUrls,
      signalCdsiUrls = signalCdsiUrls + other.signalCdsiUrls,
      signalSvr2Urls = signalSvr2Urls + other.signalSvr2Urls
    )
  }
}
