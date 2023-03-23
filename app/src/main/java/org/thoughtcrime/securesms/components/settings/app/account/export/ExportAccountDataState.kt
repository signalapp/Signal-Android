package org.thoughtcrime.securesms.components.settings.app.account.export

data class ExportAccountDataState(
  val reportDownloaded: Boolean,
  val downloadInProgress: Boolean,
  val exportAsJson: Boolean,
  val showDownloadFailedDialog: Boolean = false,
  val showDeleteDialog: Boolean = false,
  val showExportDialog: Boolean = false
)
