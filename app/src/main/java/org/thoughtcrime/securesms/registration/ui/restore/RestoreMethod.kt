/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import org.thoughtcrime.securesms.R

/**
 * Restore methods for various spots in restore flow.
 */
enum class RestoreMethod(val iconRes: Int, val titleRes: Int, val subtitleRes: Int) {
  FROM_SIGNAL_BACKUPS(
    iconRes = R.drawable.symbol_signal_backups_24,
    titleRes = R.string.SelectRestoreMethodFragment__restore_signal_backup,
    subtitleRes = R.string.SelectRestoreMethodFragment__restore_your_text_messages_and_media_from
  ),
  FROM_LOCAL_BACKUP_V1(
    iconRes = R.drawable.symbol_file_24,
    titleRes = R.string.SelectRestoreMethodFragment__restore_on_device_backup,
    subtitleRes = R.string.SelectRestoreMethodFragment__restore_your_messages_from
  ),
  FROM_LOCAL_BACKUP_V2(
    iconRes = R.drawable.symbol_folder_24,
    titleRes = R.string.SelectRestoreMethodFragment__restore_on_device_backup,
    subtitleRes = R.string.SelectRestoreMethodFragment__restore_your_messages_from
  ),
  FROM_OLD_DEVICE(
    iconRes = R.drawable.symbol_transfer_24,
    titleRes = R.string.SelectRestoreMethodFragment__from_your_old_phone,
    subtitleRes = R.string.SelectRestoreMethodFragment__transfer_directly_from_old
  )
}
