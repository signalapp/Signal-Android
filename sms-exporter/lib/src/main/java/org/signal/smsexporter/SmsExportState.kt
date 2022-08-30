package org.signal.smsexporter

/**
 * Describes the current "Export State" of a given message. This should be updated
 * by and persisted by the application whenever a state change occurs.
 */
data class SmsExportState(
  val messageId: Long = -1L,
  val startedRecipients: Set<String> = emptySet(),
  val completedRecipients: Set<String> = emptySet(),
  val startedAttachments: Set<String> = emptySet(),
  val completedAttachments: Set<String> = emptySet(),
  val progress: Progress = Progress.INIT
) {
  enum class Progress {
    INIT,
    STARTED,
    COMPLETED
  }
}
