package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.video.exo.ExoPlayerPool

/**
 * Prints off the current exoplayer pool stats, including ownership info.
 */
class LogSectionExoPlayerPool : LogSection {
  override fun getTitle(): String = "EXOPLAYER POOL"

  override fun getContent(context: Context): CharSequence {
    val poolStats = AppDependencies.exoPlayerPool.getPoolStats()
    val owners: Map<String, List<ExoPlayerPool.OwnershipInfo>> = poolStats.owners.groupBy { it.tag }
    val output = StringBuilder()

    output.append("Total players created: ${poolStats.created}\n")
    output.append("Max allowed unreserved instances: ${poolStats.maxUnreserved}\n")
    output.append("Max allowed reserved instances: ${poolStats.maxReserved}\n")
    output.append("Available created unreserved instances: ${poolStats.unreservedAndAvailable}\n")
    output.append("Available created reserved instances: ${poolStats.reservedAndAvailable}\n")
    output.append("Total unreserved created: ${poolStats.unreserved}\n")
    output.append("Total reserved created: ${poolStats.reserved}\n\n")

    output.append("Ownership Info:\n")
    if (owners.isEmpty()) {
      output.append("  No ownership info to display.")
    } else {
      owners.forEach { (ownerTag, infoList) ->
        output.append("  Owner $ownerTag\n")
        output.append("    reserved: ${infoList.filter { it.isReserved }.size}\n")
        output.append("    unreserved: ${infoList.filterNot { it.isReserved }.size}\n")
      }
    }

    return output
  }
}
