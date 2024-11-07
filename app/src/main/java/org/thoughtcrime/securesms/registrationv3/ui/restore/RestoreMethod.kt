/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import org.thoughtcrime.securesms.R

/**
 * Restore methods for various spots in restore flow.
 */
enum class RestoreMethod(val iconRes: Int, val titleRes: Int, val subtitleRes: Int) {
  FROM_SIGNAL_BACKUPS(
    iconRes = R.drawable.symbol_signal_backups_24,
    titleRes = R.string.SelectRestoreMethodFragment__from_signal_backups,
    subtitleRes = R.string.SelectRestoreMethodFragment__your_free_or_paid_signal_backup_plan
  ),
  FROM_LOCAL_BACKUP_V1(
    iconRes = R.drawable.symbol_file_24,
    titleRes = R.string.SelectRestoreMethodFragment__from_a_backup_file,
    subtitleRes = R.string.SelectRestoreMethodFragment__choose_a_backup_youve_saved
  ),
  FROM_LOCAL_BACKUP_V2(
    iconRes = R.drawable.symbol_folder_24,
    titleRes = R.string.SelectRestoreMethodFragment__from_a_backup_folder,
    subtitleRes = R.string.SelectRestoreMethodFragment__choose_a_backup_youve_saved
  ),
  FROM_OLD_DEVICE(
    iconRes = R.drawable.symbol_transfer_24,
    titleRes = R.string.SelectRestoreMethodFragment__from_your_old_phone,
    subtitleRes = R.string.SelectRestoreMethodFragment__transfer_directly_from_old
  )
}
