/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.core.util.LinkActions
import org.signal.registration.R
import org.signal.registration.RegistrationDependencies

/**
 * Bottom sheet shown during registration when the user is having trouble entering their verification code. Offers
 * troubleshooting steps and a way to contact support, mirroring the old app-module ContactSupportBottomSheetFragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSupportBottomSheet(onDismiss: () -> Unit) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val supportEmailSubject = stringResource(R.string.VerificationCodeScreen__contact_support_email_subject)
  val supportCenterUrl = stringResource(R.string.VerificationCodeScreen__support_center_url)

  BottomSheets.BottomSheet(
    onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = onDismiss) },
    sheetState = sheetState
  ) {
    ContactSupportBottomSheetContent(
      onTroubleshootingStepsClick = {
        LinkActions.openUrl(context, supportCenterUrl) {
          Toast.makeText(context, R.string.LinkActions_error_no_browser_found, Toast.LENGTH_SHORT).show()
        }
      },
      onContactSupportClick = {
        RegistrationDependencies.get().contactSupportCallback?.invoke(context, supportEmailSubject)
      }
    )
  }
}

@Composable
private fun ContactSupportBottomSheetContent(
  onTroubleshootingStepsClick: () -> Unit,
  onContactSupportClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp)
  ) {
    Text(
      text = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
          append(stringResource(R.string.VerificationCodeScreen__support_bottom_sheet_title))
        }
      },
      modifier = Modifier.padding(8.dp)
    )
    Text(
      text = stringResource(R.string.VerificationCodeScreen__support_bottom_sheet_body_suggestions),
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(8.dp)
    )
    Text(
      text = buildCallToActionString(onTroubleshootingStepsClick, onContactSupportClick),
      modifier = Modifier.padding(8.dp)
    )
  }
}

@Composable
private fun buildCallToActionString(
  onTroubleshootingStepsClick: () -> Unit,
  onContactSupportClick: () -> Unit
) = buildAnnotatedString {
  val troubleshootingStepsString = stringResource(R.string.VerificationCodeScreen__support_bottom_sheet_cta_troubleshooting_steps_substring)
  val contactSupportString = stringResource(R.string.VerificationCodeScreen__support_bottom_sheet_cta_contact_support_substring)
  val completeString = stringResource(R.string.VerificationCodeScreen__support_bottom_sheet_body_call_to_action, troubleshootingStepsString, contactSupportString)

  val troubleshootingStartIndex = completeString.indexOf(troubleshootingStepsString)
  val troubleshootingEndIndex = troubleshootingStartIndex + troubleshootingStepsString.length
  val contactSupportStartIndex = completeString.indexOf(contactSupportString)
  val contactSupportEndIndex = contactSupportStartIndex + contactSupportString.length

  val bodyStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Normal)
  val linkStyles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))

  withStyle(bodyStyle) {
    append(completeString.substring(0, troubleshootingStartIndex))
  }
  withLink(LinkAnnotation.Clickable(tag = "troubleshooting", styles = linkStyles) { onTroubleshootingStepsClick() }) {
    append(troubleshootingStepsString)
  }
  withStyle(bodyStyle) {
    append(completeString.substring(troubleshootingEndIndex, contactSupportStartIndex))
  }
  withLink(LinkAnnotation.Clickable(tag = "contact_support", styles = linkStyles) { onContactSupportClick() }) {
    append(contactSupportString)
  }
  withStyle(bodyStyle) {
    append(completeString.substring(contactSupportEndIndex))
  }
}

@Preview
@Composable
private fun ContactSupportBottomSheetPreview() {
  Previews.BottomSheetPreview {
    ContactSupportBottomSheetContent(
      onTroubleshootingStepsClick = {},
      onContactSupportClick = {}
    )
  }
}
