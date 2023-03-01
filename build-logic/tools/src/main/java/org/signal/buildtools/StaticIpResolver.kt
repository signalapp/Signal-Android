package org.signal.buildtools

import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.Record
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.UnknownHostException
import kotlin.streams.toList

/**
 * A tool to resolve hostname to static IPs.
 * Feeds into our custom DNS resolver to provide a static IP fallback for our services.
 */
class StaticIpResolver @JvmOverloads constructor(
  private val recordFetcher: RecordFetcher = RealRecordFetcher
) {

  /**
   * Resolves a hostname to a list of IPs, represented as a Java array declaration. e.g.
   *
   * ```java
   * new String[]{"192.168.1.1", "192.168.1.2"}
   * ```
   *
   * This is intended to be injected as a BuildConfig.
   */
  fun resolveToBuildConfig(hostName: String): String {
    val ips: List<String> = resolve(hostName)
    val builder = StringBuilder()

    builder.append("new String[]{")

    ips.forEachIndexed { i, ip ->
      builder.append("\"").append(ip).append("\"")

      if (i < ips.size - 1) {
        builder.append(",")
      }
    }

    return builder.append("}").toString()
  }

  private fun resolve(hostname: String): List<String> {
    val ips: MutableSet<String> = mutableSetOf()

    // Run several resolves to mitigate DNS round robin
    for (i in 1..10) {
      ips.addAll(resolveOnce(hostname))
    }

    return ips.stream().sorted().toList()
  }

  private fun resolveOnce(hostName: String): List<String> {
    try {
      val records = recordFetcher.fetchRecords(hostName)
      if (records != null) {
        return records
          .filter { it.type == Type.A }
          .map { it as ARecord }
          .map { it.address }
          .map { it.hostAddress }
          .filterNotNull()
      } else {
        throw IllegalStateException("Failed to resolve host! Lookup did not return any records.. $hostName")
      }
    } catch (e: UnknownHostException) {
      throw IllegalStateException("Failed to resolve host! $hostName", e)
    }
  }

  interface RecordFetcher {
    fun fetchRecords(hostName: String): Array<Record>?
  }

  private object RealRecordFetcher : RecordFetcher {
    override fun fetchRecords(hostName: String): Array<Record>? {
      val resolver = SimpleResolver("1.1.1.1")
      val lookup: Lookup = doLookup(hostName)

      lookup.setResolver(resolver)

      return lookup.run()
    }

    @Throws(UnknownHostException::class)
    private fun doLookup(hostname: String): Lookup {
      try {
        return Lookup(hostname)
      } catch (e: Throwable) {
        throw UnknownHostException()
      }
    }
  }
}
