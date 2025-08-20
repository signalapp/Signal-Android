/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Our very own preview that will generate light and dark previews for
 * composables
 */
@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class SignalPreview()
