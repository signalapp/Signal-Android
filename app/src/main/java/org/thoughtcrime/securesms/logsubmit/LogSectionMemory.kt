package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import org.signal.core.util.MemoryTracker
import org.signal.core.util.bytes
import org.signal.core.util.kibiBytes
import org.signal.core.util.roundedString

class LogSectionMemory : LogSection {
  override fun getTitle(): String = "MEMORY"

  override fun getContent(context: Context): CharSequence {
    val appMemory = MemoryTracker.getAppJvmHeapUsage()
    val nativeMemory = MemoryTracker.getSystemNativeMemoryUsage(context)
    var base = """
      -- App JVM Heap
      Used         : ${appMemory.usedBytes.byteDisplay()}
      Free         : ${appMemory.freeBytes.byteDisplay()}
      Current Total: ${appMemory.currentTotalBytes.byteDisplay()}
      Max Possible : ${appMemory.maxPossibleBytes.byteDisplay()}
      
      -- System Native Memory
      Used                : ${nativeMemory.usedBytes.byteDisplay()}
      Free                : ${nativeMemory.freeBytes.byteDisplay()}
      Total               : ${nativeMemory.totalBytes.byteDisplay()}
      Low Memory Threshold: ${nativeMemory.lowMemoryThreshold.byteDisplay()}
      Low Memory?         : ${nativeMemory.lowMemory}
    """.trimIndent()

    val detailedMemory = MemoryTracker.getDetailedMemoryStats()

    base += "\n\n"
    base += """
      -- Detailed Memory (API 23+)
      App JVM Heap Usage   : ${detailedMemory.appJavaHeapUsageKb?.kbDisplay()}
      App Native Heap Usage: ${detailedMemory.appNativeHeapUsageKb?.kbDisplay()}
      Code Usage           : ${detailedMemory.codeUsageKb?.kbDisplay()}
      Graphics Usage       : ${detailedMemory.graphicsUsageKb?.kbDisplay()}
      Stack Usage          : ${detailedMemory.stackUsageKb?.kbDisplay()}
      Other Usage          : ${detailedMemory.appOtherUsageKb?.kbDisplay()}
    """.trimIndent()

    return base
  }

  private fun Long.byteDisplay(): String {
    return "$this bytes (${bytes.inMebiBytes.roundedString(2)} MiB)"
  }

  private fun Long.kbDisplay(): String {
    return "$this KiB (${kibiBytes.inMebiBytes.roundedString(2)} MiB)"
  }
}
