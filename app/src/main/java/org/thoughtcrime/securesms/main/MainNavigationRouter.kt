/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

interface MainNavigationRouter {
  fun goTo(location: MainNavigationDetailLocation)

  fun goTo(location: MainNavigationListLocation)
}
