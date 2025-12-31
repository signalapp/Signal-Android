package org.signal.devicetransfer

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class WifiDirectTest {
  @Test
  fun instanceName_withExtraInfo() {
    val instanceName = WifiDirect.buildServiceInstanceName("knownothing")

    assertEquals("_devicetransfer._knownothing._signal.org", instanceName)

    val extractedExtraInfo = WifiDirect.isInstanceNameMatching(instanceName)
    assertEquals(extractedExtraInfo, "knownothing")
  }

  @Test
  fun instanceName_matchingWithoutExtraInfo() {
    val instanceName = WifiDirect.buildServiceInstanceName("")

    assertEquals("_devicetransfer._signal.org", instanceName)

    val extractedExtraInfo = WifiDirect.isInstanceNameMatching(instanceName)
    assertEquals(extractedExtraInfo, "")
  }

  @Test
  fun instanceName_notMatching() {
    val extractedExtraInfo = WifiDirect.isInstanceNameMatching("_whoknows._what.org")
    assertNull(extractedExtraInfo)
  }
}
