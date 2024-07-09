package org.thoughtcrime.securesms.registration.fragments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheets
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

/**
 * Helpful bottom sheet dialog displayed during registration when the user enters the wrong verification code too many times.
 */
class ContactSupportBottomSheetFragment : ComposeBottomSheetDialogFragment() {

  @Preview
  @Composable
  override fun SheetContent() {
    val annotatedText = buildClickableString()

    return Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.Center)
        .padding(16.dp)
    ) {
      BottomSheets.Handle()
      Text(
        text = buildAnnotatedString {
          withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
            append(stringResource(R.string.RegistrationActivity_support_bottom_sheet_title))
          }
        },
        modifier = Modifier.padding(8.dp)
      )
      Text(
        text = stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_suggestions),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp)
      )
      ClickableText(
        text = annotatedText,
        onClick = { offset ->
          annotatedText.getStringAnnotations(
            tag = "URL",
            start = offset,
            end = offset
          )
            .firstOrNull()?.let { annotation ->
              when (annotation.item) {
                TROUBLESHOOTING_STEPS_KEY -> openTroubleshootingSteps()
                CONTACT_SUPPORT_KEY -> sendEmailToSupport()
              }
            }
        },
        modifier = Modifier.padding(8.dp)
      )
    }
  }

  @Composable
  private fun buildClickableString(): AnnotatedString {
    val troubleshootingStepsString = stringResource(R.string.RegistrationActivity_support_bottom_sheet_cta_troubleshooting_steps_substring)
    val contactSupportString = stringResource(R.string.RegistrationActivity_support_bottom_sheet_cta_contact_support_substring)
    val completeString = stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_call_to_action, troubleshootingStepsString, contactSupportString)

    val troubleshootingStartIndex = completeString.indexOf(troubleshootingStepsString)
    val troubleshootingEndIndex = troubleshootingStartIndex + troubleshootingStepsString.length

    val contactSupportStartIndex = completeString.indexOf(contactSupportString)
    val contactSupportEndIndex = contactSupportStartIndex + contactSupportString.length

    val doesStringEndWithContactSupport = contactSupportEndIndex >= completeString.lastIndex

    return buildAnnotatedString {
      withStyle(
        style = SpanStyle(
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Normal
        )
      ) {
        append(completeString.substring(0, troubleshootingStartIndex))
      }
      pushStringAnnotation(
        tag = "URL",
        annotation = TROUBLESHOOTING_STEPS_KEY
      )
      withStyle(
        style = SpanStyle(
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )
      ) {
        append(troubleshootingStepsString)
      }
      pop()
      withStyle(
        style = SpanStyle(
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Normal
        )
      ) {
        append(completeString.substring(troubleshootingEndIndex, contactSupportStartIndex))
      }
      pushStringAnnotation(
        tag = "URL",
        annotation = CONTACT_SUPPORT_KEY
      )
      withStyle(
        style = SpanStyle(
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )
      ) {
        append(contactSupportString)
      }
      pop()
      if (!doesStringEndWithContactSupport) {
        append(completeString.substring(contactSupportEndIndex, completeString.lastIndex))
      }
    }
  }

  private fun openTroubleshootingSteps() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.support_center_url))
  }

  private fun sendEmailToSupport() {
    val body = SupportEmailUtil.generateSupportEmailBody(
      requireContext(),
      R.string.RegistrationActivity_code_support_subject,
      null,
      null
    )
    CommunicationActions.openEmail(
      requireContext(),
      SupportEmailUtil.getSupportEmailAddress(requireContext()),
      getString(R.string.RegistrationActivity_code_support_subject),
      body
    )
  }

  fun showSafely(fm: FragmentManager, tag: String) {
    if (!isAdded && !fm.isStateSaved) {
      show(fm, tag)
    }
  }

  companion object {
    private const val TROUBLESHOOTING_STEPS_KEY = "troubleshooting"
    private const val CONTACT_SUPPORT_KEY = "contact_support"
  }
}
