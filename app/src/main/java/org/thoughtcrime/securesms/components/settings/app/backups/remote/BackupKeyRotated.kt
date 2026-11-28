/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

/**
 * Sent through the [org.signal.core.ui.navigation.ResultEventBus] when the backup key screen finishes rotating the key,
 * for whichever screen sent the user there to do it.
 */
data object BackupKeyRotated
