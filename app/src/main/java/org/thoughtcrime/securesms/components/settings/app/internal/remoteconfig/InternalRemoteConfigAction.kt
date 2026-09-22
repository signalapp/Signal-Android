/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

sealed interface InternalRemoteConfigAction {
  data object Exit : InternalRemoteConfigAction
  data object RestartApp : InternalRemoteConfigAction
}
