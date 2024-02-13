package org.whispersystems.signalservice.internal.configuration

import okhttp3.Dns
import okhttp3.Interceptor
import java.util.Optional

/**
 * Defines all network configuration needed to connect to the Signal service.
 */
class SignalServiceConfiguration(
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
)
