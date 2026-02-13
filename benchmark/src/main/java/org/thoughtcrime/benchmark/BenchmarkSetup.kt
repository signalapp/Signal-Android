package org.thoughtcrime.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

object BenchmarkSetup {
  private const val TARGET_PACKAGE = "org.thoughtcrime.securesms.benchmark"
  private const val RECEIVER = "org.signal.benchmark.BenchmarkCommandReceiver"

  fun setup(type: String, device: UiDevice) {
    device.executeShellCommand("pm clear $TARGET_PACKAGE")
    device.executeShellCommand("am start -W -n $TARGET_PACKAGE/org.signal.benchmark.BenchmarkSetupActivity --es setup-type $type")
    device.wait(Until.hasObject(By.textContains("done")), 25_000L)
  }

  fun setupIndividualSend(device: UiDevice) {
    device.benchmarkCommandBroadcast("individual-send")
  }

  fun setupGroupSend(device: UiDevice) {
    device.benchmarkCommandBroadcast("group-send")
  }

  fun releaseMessages(device: UiDevice) {
    device.benchmarkCommandBroadcast("release-messages")
  }

  private fun UiDevice.benchmarkCommandBroadcast(command: String) {
    executeShellCommand("am broadcast -a org.signal.benchmark.action.COMMAND -e command $command -n $TARGET_PACKAGE/$RECEIVER")
  }
}
