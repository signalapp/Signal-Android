package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents an individual credential for an archive operation. Note that is isn't the final
 * credential you will actually use -- that's [org.signal.libsignal.zkgroup.backups.BackupAuthCredential].
 * But you use these to make those.
 */
class ArchiveServiceCredential(
  @JsonProperty
  val credential: ByteArray,
  @JsonProperty
  val redemptionTime: Long
)
