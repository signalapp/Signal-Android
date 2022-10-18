package org.signal.smsexporter

/**
 * Expresses the current progress of SMS exporting.
 */
sealed class SmsExportProgress {
  /**
   * Have not started yet.
   */
  object Init : SmsExportProgress()

  /**
   * Starting up and about to start processing messages
   */
  object Starting : SmsExportProgress()

  /**
   * Processing messages
   */
  data class InProgress(
    val progress: Int,
    val errorCount: Int,
    val total: Int
  ) : SmsExportProgress()

  /**
   * All done.
   */
  data class Done(val errorCount: Int, val total: Int) : SmsExportProgress()
}
