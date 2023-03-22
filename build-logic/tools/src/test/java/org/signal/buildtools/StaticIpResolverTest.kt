package org.signal.buildtools

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import java.net.Inet4Address

class StaticIpResolverTest {

  companion object {
    const val SIGNAL_DOT_ORG = "www.signal.org"
    val SIGNAL_IP = byteArrayOf(123, 45, 67, 89)
    val STRINGIFIED_IP = SIGNAL_IP.joinToString(".")
  }

  @Test
  fun `Given a hostname with records, when I resolveToBuildConfig, then I expect a matching IP`() {
    val staticIpResolver = StaticIpResolver(
      FakeRecordFetcher(
        mapOf(
          SIGNAL_DOT_ORG to arrayOf(
            ARecord(
              Name.fromString("www."),
              DClass.ANY,
              0L,
              mockk<Inet4Address> {
                every { address } returns SIGNAL_IP
                every { hostAddress } returns STRINGIFIED_IP
              }
            )
          )
        )
      )
    )
    val actual = staticIpResolver.resolveToBuildConfig(SIGNAL_DOT_ORG)
    val expected = """
      new String[]{"$STRINGIFIED_IP"}
    """.trimIndent()

    assertEquals(expected, actual)
  }

  @Test(expected = IllegalStateException::class)
  fun `Given a hostname without records, when I resolveToBuildConfig, then I expect`() {
    val staticIpResolver = StaticIpResolver(FakeRecordFetcher(emptyMap()))
    staticIpResolver.resolveToBuildConfig(SIGNAL_DOT_ORG)
  }

  private class FakeRecordFetcher(private val recordMap: Map<String, Array<Record>?>) : StaticIpResolver.RecordFetcher {
    override fun fetchRecords(hostName: String): Array<Record>? {
      return recordMap[hostName]
    }
  }
}
