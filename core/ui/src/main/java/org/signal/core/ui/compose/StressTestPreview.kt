/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.ui.tooling.preview.Preview

/**
 * Every config a layout stress test checks by default, as previews, so a failing one can be opened by name.
 *
 * The pseudo-languages ("qaa" and "qab") are generated into debug builds only, so the previews that use them render
 * English in any other build. StressTestPreviewTest in :screener fails when these drift from the stress tests.
 */
@Preview(name = "phone", device = "spec:width=360dp,height=780dp", group = "stress test")
@Preview(name = "phone + font 2x", device = "spec:width=360dp,height=780dp", fontScale = 2f, group = "stress test")
@Preview(name = "landscape + display 1.3x + RTL + pseudo-expansive", device = "spec:width=600dp,height=277dp", locale = "b+qaa+Arab", group = "stress test")
@Preview(name = "tablet + font 2x + RTL + pseudo-compound", device = "spec:width=1280dp,height=800dp", fontScale = 2f, locale = "b+qab+Arab", group = "stress test")
@Preview(name = "landscape + display 1.3x + font 2x + pseudo-compound", device = "spec:width=600dp,height=277dp", fontScale = 2f, locale = "b+qab", group = "stress test")
@Preview(name = "tablet + pseudo-expansive", device = "spec:width=1280dp,height=800dp", locale = "b+qaa", group = "stress test")
@Preview(name = "phone + display 1.3x + RTL", device = "spec:width=277dp,height=600dp", locale = "b+en+Arab", group = "stress test")
@Preview(name = "phone + pseudo-compound", device = "spec:width=360dp,height=780dp", locale = "b+qab", group = "stress test")
@Preview(name = "phone + font 2x + pseudo-expansive", device = "spec:width=360dp,height=780dp", fontScale = 2f, locale = "b+qaa", group = "stress test")
@Preview(name = "landscape", device = "spec:width=780dp,height=360dp", group = "stress test")
@Preview(name = "tablet + display 1.3x", device = "spec:width=985dp,height=615dp", group = "stress test")
annotation class StressTestPreview
