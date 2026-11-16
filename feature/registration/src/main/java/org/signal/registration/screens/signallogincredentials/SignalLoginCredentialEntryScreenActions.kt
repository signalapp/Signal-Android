/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

sealed interface SignalLoginCredentialEntryScreenActions {
  companion object {
    /** The article explaining where to find your Signal Login. */
    const val NEED_HELP_ARTICLE_URL = "https://support.signal.org/hc/articles/11197884108826"
  }

  /** Open the article explaining where to find your Signal Login. */
  data object OpenNeedHelpArticle : SignalLoginCredentialEntryScreenActions
}
