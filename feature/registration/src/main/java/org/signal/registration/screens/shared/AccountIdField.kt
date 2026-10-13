/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import org.signal.registration.R
import org.signal.registration.fonts.MonoTypeface

/** How an account ID is rendered wherever it can be typed, so the field looks the same on every screen that offers one. */
@Composable
internal fun accountIdTextStyle(): TextStyle {
  return MaterialTheme.typography.bodyLarge.copy(
    fontFamily = MonoTypeface.fontFamily(),
    fontSize = 18.sp,
    letterSpacing = 1.44.sp
  )
}

/** The message shown beneath an account ID field explaining why what was typed can't be used. */
@Composable
internal fun AccountIdErrorText(error: AccountIdError) {
  when (error) {
    is AccountIdError.Invalid -> Text(stringResource(R.string.AccountIdField__invalid_account_id))
  }
}

/**
 * Renders an account ID the way an ACI is normally written: uppercased and split into 8-4-4-4-12 groups by dashes.
 * The dashes are display-only, so what the view model sees is always the unformatted ID.
 */
internal object AccountIdVisualTransformation : VisualTransformation {

  override fun filter(text: AnnotatedString): TransformedText {
    return TransformedText(
      text = AnnotatedString(AccountIdFormat.dashed(text.text)),
      offsetMapping = AccountIdOffsetMapping(text.length)
    )
  }

  private class AccountIdOffsetMapping(private val inputLength: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = offset + AccountIdFormat.dashesBeforeRawOffset(offset, inputLength)

    override fun transformedToOriginal(offset: Int): Int = offset - AccountIdFormat.dashesBeforeDashedOffset(offset, inputLength)
  }
}
