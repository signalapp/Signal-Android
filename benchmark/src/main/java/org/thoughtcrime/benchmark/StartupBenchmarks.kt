package org.thoughtcrime.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark benchmarks for app startup performance.
 *
 * WARNING! THIS WILL WIPE YOUR SIGNAL INSTALL
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun coldStartNone() {
    measureStartup(5, CompilationMode.None())
  }

  @Test
  fun coldStartBaselineProfile() {
    measureStartup(5, CompilationMode.Partial())
  }

  @OptIn(ExperimentalMetricApi::class)
  private fun measureStartup(iterations: Int, compilationMode: CompilationMode) {
    var setup = false
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms",
      metrics = listOf(StartupTimingMetric(), TraceSectionMetric("ConversationListDataSource#load")),
      iterations = iterations,
      startupMode = StartupMode.COLD,
      compilationMode = compilationMode,
      setupBlock = {
        if (!setup) {
          BenchmarkSetup.setup("cold-start", device)

          killProcess()
          dropKernelPageCache()
          setup = true
        }
      }
    ) {
      pressHome()
      startActivityAndWait()
    }
  }
}
