package org.thoughtcrime.benchmark

import android.Manifest
import android.os.Build
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @OptIn(ExperimentalMetricApi::class)
  @Test
  fun simpleConversationOpen() {
    var setup = false
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms",
      metrics = listOf(
        TraceSectionMetric("6-ConversationOpen"),
        TraceSectionMetric("1-ConversationOpen-ViewModel-Init"),
        TraceSectionMetric("2-ConversationOpen-Metadata-Loaded"),
        TraceSectionMetric("3-ConversationOpen-Data-Loaded"),
        TraceSectionMetric("4-ConversationOpen-Data-Posted"),
        TraceSectionMetric("5-ConversationOpen-Render"),
      ),
      iterations = 10,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        if (!setup) {
          BenchmarkSetup.setup("conversation-open", device)
          setup = true
        }
        killProcess()
        if (Build.VERSION.SDK_INT >= 33) {
          device.executeShellCommand("pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
        }
        startActivityAndWait()
        device.waitForIdle()
      }) {
      device.findObject(By.textContains("Buddy")).click()
      device.wait(Until.hasObject(By.textContains("Signal message")), 10_000L)
      device.wait(Until.hasObject(By.textContains("Test")), 5_000L)
    }
  }
}