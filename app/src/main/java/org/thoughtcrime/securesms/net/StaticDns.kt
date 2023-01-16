package org.thoughtcrime.securesms.net

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * A super simple [Dns] implementation that maps hostnames to a static IP addresses.
 */
class StaticDns(private val hostnameMap: Map<String, Set<String>>) : Dns {

  @Throws(UnknownHostException::class)
  override fun lookup(hostname: String): List<InetAddress> {
    val ips = hostnameMap[hostname]

    return if (ips != null && ips.isNotEmpty()) {
      listOf(InetAddress.getByName(ips.random()))
    } else {
      throw UnknownHostException(hostname)
    }
  }
}
