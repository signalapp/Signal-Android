/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Only generates a dark preview. Useful for screens that are only ever rendered in dark mode (like calling).
 */
@Preview(name = "night mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class NightPreview()

@Preview(name = "day mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@NightPreview
annotation class DayNightPreviews

@Preview(name = "phone portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=360dp,height=640dp,orientation=portrait")
@Preview(name = "phone portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=360dp,height=640dp,orientation=portrait")
@Preview(name = "phone landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=640dp,height=360dp,orientation=landscape")
annotation class PhonePreviews

@Preview(name = "foldable portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=600dp,height=1024dp,orientation=portrait")
@Preview(name = "foldable landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=1024dp,height=600dp,orientation=landscape")
annotation class FoldablePreviews

@Preview(name = "tablet portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=840dp,height=1280dp,orientation=portrait")
@Preview(name = "tablet landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=1280dp,height=840dp,orientation=landscape")
annotation class TabletPreviews

@PhonePreviews
@FoldablePreviews
@TabletPreviews
annotation class AllDevicePreviews
