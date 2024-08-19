/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Only generates a dark preview. Useful for screens that
 * are only ever rendered in dark mode (like calling)
 */
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class DarkPreview()
