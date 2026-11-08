/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import kotlinx.serialization.Serializable
import org.signal.core.ui.compose.split.ListNavKey

/**
 * The app settings home screen, which is the list pane every [AppSettingsRoute] is displayed beside.
 */
@Serializable
data object AppSettingsHomeRoute : ListNavKey
