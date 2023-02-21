package org.thoughtcrime.securesms.registration.fragments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Helpful bottom sheet dialog displayed during registration when the user enters the wrong verification code too many times.
 */
class ContactSupportBottomSheetFragment(private val troubleshootingStepsListener: Runnable, private val contactSupportListener: Runnable) : ComposeBottomSheetDialogFragment() {

  @Composable
  override fun SheetContent() {
    val annotatedText = buildAnnotatedString {
      withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
        append(stringResource(R.string.RegistrationActivity_support_bottom_sheet_title))
      }
      append(stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_part_1))
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
        append(stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_part_2))
      }
      pop()
      append(stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_part_3))
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
        append(stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_part_4))
      }
      pop()
    }

    return Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.Center)
    ) {
      Handle()
      ClickableText(
        text = annotatedText,
        onClick = { offset ->
          // We check if there is an *URL* annotation attached to the text
          // at the clicked position
          annotatedText.getStringAnnotations(
            tag = "URL",
            start = offset,
            end = offset
          )
            .firstOrNull()?.let { annotation ->
              when (annotation.item) {
                TROUBLESHOOTING_STEPS_KEY -> troubleshootingStepsListener.run()
                CONTACT_SUPPORT_KEY -> contactSupportListener.run()
              }
            }
        },
        modifier = Modifier.padding(16.dp)
      )
    }
  }
  companion object {
    private const val TROUBLESHOOTING_STEPS_KEY = "troubleshooting"
    private const val CONTACT_SUPPORT_KEY = "contact_support"
  }
}
