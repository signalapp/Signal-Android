/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R

/**
 * Title-less top app bar holding only a back arrow, matching the header of the registration screens that can be backed
 * out of.
 *
 * @param scrollBehavior Hoisted by the caller so it can attach [scrollBehavior]'s nested-scroll connection to its
 *   scrolling content, which is what lets the bar collapse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
  scrollBehavior: TopAppBarScrollBehavior,
  onBackClick: () -> Unit
) {
  Scaffolds.DefaultTopAppBar(
    title = "",
    titleContent = { _, _ -> },
    onNavigationClick = onBackClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.RegistrationScreen__back),
    scrollBehavior = scrollBehavior
  )
}
