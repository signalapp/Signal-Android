/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

import android.view.View
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.verify.SafetyNumberQrView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.util.CommunicationActions
import org.signal.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyDisplayScreen(
  state: VerifyDisplayScreenState,
  emitter: (VerifyDisplayScreenEvent) -> Unit,
  onQrViewInitialized: (View) -> Unit
) {
  state.recipient ?: return

  val context = LocalContext.current
  val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val displayName by rememberRecipientField(state.recipient) { getDisplayName(context) }
  val scrollState = rememberScrollState()

  Scaffolds.Default(
    title = stringResource(R.string.AndroidManifest__verify_safety_number),
    navigationIconRes = CoreUiR.drawable.symbol_arrow_start_24,
    onNavigationClick = { backPressedDispatcher?.onBackPressed() }
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(scrollState)
        .padding(it)
    ) {
      SafetyNumberQr(
        state = state,
        emitter = emitter,
        onQrViewInitialized = onQrViewInitialized,
        modifier = Modifier.padding(top = 24.dp)
      )

      Text(
        text = buildAnnotatedString {
          append(stringResource(R.string.verify_display_fragment__pnp_verify_safety_numbers_explanation_with_s, displayName))
          append(" ")
          withLink(
            link = LinkAnnotation.Clickable(
              tag = "verify-learn-more",
              styles = TextLinkStyles(
                style = SpanStyle(
                  color = MaterialTheme.colorScheme.primary
                )
              )
            ) {
              CommunicationActions.openBrowserLink(context, "https://signal.org/redirect/safety-numbers")
            }
          ) {
            append(stringResource(R.string.LearnMoreTextView_learn_more))
          }
        },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
          start = 32.dp,
          end = 32.dp,
          top = 24.dp,
          bottom = 20.dp
        )
      )

      if (state.isAutomaticVerificationVisible) {
        AutomaticKeyVerificationBlock(
          state = state,
          emitter = emitter
        )

        Text(
          text = buildAnnotatedString {
            append(stringResource(R.string.verify_display_fragment__auto_verify_not_available))
            append(" ")

            val url = stringResource(R.string.verify_display_fragment__link)
            withLink(
              link = LinkAnnotation.Clickable(
                tag = "auto-verify-learn-more",
                styles = TextLinkStyles(
                  style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary
                  )
                )
              ) {
                CommunicationActions.openBrowserLink(context, url)
              }
            ) {
              append(stringResource(R.string.LearnMoreTextView_learn_more))
            }
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
        )
      }
    }
  }

  if (state.shouldDisplayVerifyAutomaticallyEducationSheet) {
    ModalBottomSheet(
      dragHandle = null,
      onDismissRequest = {
        emitter(VerifyDisplayScreenEvent.EducationDismiss)
      }
    ) {
      VerifyEducationSheet(
        onVerify = {
          emitter(VerifyDisplayScreenEvent.EducationDismiss)
        },
        onLearnMore = {
          emitter(VerifyDisplayScreenEvent.EducationLearnMoreClick)
        }
      )
    }
  }
}

@Composable
private fun SafetyNumberQr(
  state: VerifyDisplayScreenState,
  onQrViewInitialized: (View) -> Unit,
  emitter: (VerifyDisplayScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val fingerprint = (state.fingerprintHolder as? FingerprintHolder.Initialised)?.fingerprint
  var animateSuccess by remember { mutableStateOf(false) }
  var animateFailure by remember { mutableStateOf(false) }
  var initialLoad by remember { mutableStateOf(true) }

  AndroidView(
    factory = { SafetyNumberQrView(it) },
    modifier = modifier
  ) {
    // TODO - qrCodeContainer has a context menu.
    // TODO - animateVerifiedSuccess // animateVerifiedFailure

    if (fingerprint != null) {
      it.setFingerprintViews(fingerprint.fingerprint, initialLoad) // TODO - animateCodeChanges
      initialLoad = false
    }

    if (animateSuccess) {
      animateSuccess = false
      it.animateVerifiedSuccess()
    }

    if (animateFailure) {
      animateFailure = false
      it.animateVerifiedFailure()
    }

    it.verifyButton.setOnClickListener {
      emitter(VerifyDisplayScreenEvent.VerifyButtonClick(!state.isSafetyNumberVerified))
    }

    it.shareButton.setOnClickListener {
      emitter(VerifyDisplayScreenEvent.ShareClick)
    }

    it.qrCodeContainer.setOnClickListener {
      emitter(VerifyDisplayScreenEvent.QrClick)
    }

    onQrViewInitialized(it.numbersContainer)

    if (state.isSafetyNumberVerified) {
      it.verifyButton.setText(R.string.verify_display_fragment__clear_verification)
    } else {
      it.verifyButton.setText(R.string.verify_display_fragment__mark_as_verified)
    }
  }

  if (state.fingerprintHolder == FingerprintHolder.NoFingerprintAvailable) {
    YouMustFirstExchangeMessagesDialog(state, emitter)
  }

  var alertMessage by remember { mutableStateOf(-1) }

  LaunchedEffect(state.clipComparisonResult?.submissionTime, state.scanComparisonResult?.submissionTime) {
    when (state.clipComparisonResult) {
      is VerifyDisplayScreenState.ClipComparisonResult.Failure -> animateFailure = true
      is VerifyDisplayScreenState.ClipComparisonResult.NoDataInClipboard -> {
        alertMessage = R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard
      }
      is VerifyDisplayScreenState.ClipComparisonResult.NoSafetyNumberInClipboard -> {
        alertMessage = R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard
      }
      is VerifyDisplayScreenState.ClipComparisonResult.Success -> animateSuccess = true
      null -> Unit
    }

    when (state.scanComparisonResult) {
      is VerifyDisplayScreenState.ScanComparisonResult.Failure -> animateFailure = true
      is VerifyDisplayScreenState.ScanComparisonResult.IncorrectFormat -> {
        alertMessage = R.string.VerifyIdentityActivity_the_scanned_qr_code_is_not_a_correctly_formatted_safety_number
      }
      is VerifyDisplayScreenState.ScanComparisonResult.Success -> animateSuccess = true
      null -> Unit
    }
  }

  if (alertMessage > 0) {
    Dialogs.SimpleMessageDialog(
      message = stringResource(alertMessage),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = {
        alertMessage = -1
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomaticKeyVerificationBlock(
  state: VerifyDisplayScreenState,
  emitter: (VerifyDisplayScreenEvent) -> Unit
) {
  var displayEncryptedSheet by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    Text(
      text = stringResource(R.string.verify_display_fragment__automatic),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(vertical = 12.dp)
    )

    AnimatedContent(
      targetState = state.automaticVerificationStatus,
      modifier = Modifier
        .fillMaxWidth()
        .background(color = SignalTheme.colors.colorSurface1, shape = RoundedCornerShape(38.dp))
        .padding(horizontal = 16.dp)
    ) { targetStatus ->
      val hasInfoSheet = targetStatus != AutomaticVerificationStatus.NONE && targetStatus != AutomaticVerificationStatus.VERIFYING

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(
          enabled = targetStatus == AutomaticVerificationStatus.NONE || hasInfoSheet,
          onClick = {
            if (targetStatus == AutomaticVerificationStatus.NONE) {
              emitter(VerifyDisplayScreenEvent.VerifyAutomaticallyClick)
            } else {
              displayEncryptedSheet = true
            }
          },
          role = Role.Button
        )
      ) {
        if (targetStatus == AutomaticVerificationStatus.VERIFYING) {
          CircularProgressIndicator(
            modifier = Modifier
              .padding(vertical = 16.dp)
              .size(24.dp),
            color = MaterialTheme.colorScheme.outline,
            strokeCap = StrokeCap.Square,
            strokeWidth = 2.dp
          )
        } else {
          StatusIcon(targetStatus)
        }

        Text(
          text = stringResource(getStatusText(targetStatus)),
          modifier = Modifier
            .padding(vertical = 16.dp)
            .padding(start = 12.dp)
            .weight(1f, fill = false)
        )

        if (hasInfoSheet) {
          Icon(
            imageVector = SignalIcons.ChevronRight.imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
              .padding(start = 2.dp)
              .size(16.dp)
          )
        }
      }
    }
  }

  if (displayEncryptedSheet) {
    state.recipient ?: return
    val context = LocalContext.current
    val name by rememberRecipientField(state.recipient) { getDisplayName(context) }

    ModalBottomSheet(
      dragHandle = null,
      onDismissRequest = { displayEncryptedSheet = false }
    ) {
      VerifiedSheet(
        verifiedStatus = state.automaticVerificationStatus,
        name = name,
        onClick = {
          displayEncryptedSheet = false
        }
      )
    }
  }
}

@Composable
private fun YouMustFirstExchangeMessagesDialog(
  state: VerifyDisplayScreenState,
  emitter: (VerifyDisplayScreenEvent) -> Unit
) {
  state.recipient ?: return

  val context = LocalContext.current
  val recipientName by rememberRecipientField(state.recipient) { getDisplayName(context) }

  Dialogs.SimpleMessageDialog(
    message = stringResource(R.string.VerifyIdentityActivity_you_must_first_exchange_messages_in_order_to_view, recipientName),
    dismiss = stringResource(android.R.string.ok),
    onDismiss = {
      emitter(VerifyDisplayScreenEvent.YouMustFirstExchangeMessagesDialogDismiss)
    }
  )
}

@StringRes
private fun getStatusText(status: AutomaticVerificationStatus): Int {
  return when (status) {
    AutomaticVerificationStatus.NONE -> R.string.verify_display_fragment__verify_automatic
    AutomaticVerificationStatus.VERIFYING -> R.string.verify_display_fragment__verifying
    AutomaticVerificationStatus.UNAVAILABLE_PERMANENT -> R.string.verify_display_fragment__encryption_unavailable
    AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY -> R.string.verify_display_fragment__encryption_unavailable
    AutomaticVerificationStatus.VERIFIED -> R.string.verify_display_fragment__encryption_verified
  }
}

@Composable
private fun StatusIcon(status: AutomaticVerificationStatus) {
  if (status == AutomaticVerificationStatus.VERIFYING) {
    return
  }

  val icon = when (status) {
    AutomaticVerificationStatus.NONE -> ImageVector.vectorResource(R.drawable.symbol_key_24)
    AutomaticVerificationStatus.VERIFYING -> error("None.")
    AutomaticVerificationStatus.UNAVAILABLE_PERMANENT -> SignalIcons.Info.imageVector
    AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY -> SignalIcons.Info.imageVector
    AutomaticVerificationStatus.VERIFIED -> ImageVector.vectorResource(R.drawable.symbol_check_filled_circle_24)
  }

  val tint = when (status) {
    AutomaticVerificationStatus.NONE -> MaterialTheme.colorScheme.onSurface
    AutomaticVerificationStatus.VERIFYING -> error("None.")
    AutomaticVerificationStatus.UNAVAILABLE_PERMANENT -> MaterialTheme.colorScheme.onSurfaceVariant
    AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY -> MaterialTheme.colorScheme.onSurfaceVariant
    AutomaticVerificationStatus.VERIFIED -> Color(0xFF4CAF50)
  }

  Icon(
    imageVector = icon,
    tint = tint,
    contentDescription = null
  )
}

@DayNightPreviews
@Composable
private fun VerifyDisplayScreenPreview() {
  Previews.Preview {
    var state by remember {
      mutableStateOf(
        VerifyDisplayScreenState(
          isSafetyNumberVerified = false,
          isAutomaticVerificationVisible = true,
          shouldDisplayVerifyAutomaticallyEducationSheet = false,
          recipient = Recipient(
            isResolving = false,
            systemContactName = "Miles Morales"
          )
        )
      )
    }

    VerifyDisplayScreen(
      state = state,
      emitter = {
        state = when (it) {
          VerifyDisplayScreenEvent.VerifyAutomaticallyClick -> {
            state.copy(automaticVerificationStatus = AutomaticVerificationStatus.VERIFYING)
          }

          is VerifyDisplayScreenEvent.VerifyButtonClick -> {
            state.copy(isSafetyNumberVerified = it.isVerified)
          }

          else -> state
        }
      },
      onQrViewInitialized = {}
    )
  }
}
