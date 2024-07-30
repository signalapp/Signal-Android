package org.thoughtcrime.securesms.permissions

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

private const val PLACEHOLDER = "__RADIO_BUTTON_PLACEHOLDER__"

/**
 * Bottom sheet shown when a permission has been previously denied
 *
 * Displays rationale for the need of a permission and how to grant it
 */
class PermissionDeniedBottomSheet private constructor() : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.66f

  companion object {
    private const val ARG_TITLE = "argument.title_res"
    private const val ARG_SUBTITLE = "argument.subtitle_res"
    private const val ARG_USE_EXTENDED = "argument.use.extended"

    @JvmStatic
    fun showPermissionFragment(titleRes: Int, subtitleRes: Int, useExtended: Boolean = false): ComposeBottomSheetDialogFragment {
      return PermissionDeniedBottomSheet().apply {
        arguments = bundleOf(
          ARG_TITLE to titleRes,
          ARG_SUBTITLE to subtitleRes,
          ARG_USE_EXTENDED to useExtended
        )
      }
    }
  }

  @Composable
  override fun SheetContent() {
    PermissionDeniedSheetContent(
      titleRes = remember { requireArguments().getInt(ARG_TITLE) },
      subtitleRes = remember { requireArguments().getInt(ARG_SUBTITLE) },
      useExtended = remember { requireArguments().getBoolean(ARG_USE_EXTENDED) },
      onSettingsClicked = this::goToSettings
    )
  }

  private fun goToSettings() {
    requireContext().startActivity(Permissions.getApplicationSettingsIntent(requireContext()))
    dismissAllowingStateLoss()
  }
}

@SignalPreview
@Composable
private fun PermissionDeniedSheetContentPreview() {
  Previews.BottomSheetPreview {
    PermissionDeniedSheetContent(
      titleRes = R.string.AttachmentManager_signal_allow_access_location,
      subtitleRes = R.string.AttachmentManager_signal_to_send_location,
      onSettingsClicked = {}
    )
  }
}

@Composable
private fun PermissionDeniedSheetContent(
  titleRes: Int,
  subtitleRes: Int,
  useExtended: Boolean = false,
  onSettingsClicked: () -> Unit
) {
  Column(
    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp)
  ) {
    BottomSheets.Handle(
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    Text(
      text = stringResource(titleRes),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 12.dp, top = 20.dp)
    )

    Text(
      text = stringResource(subtitleRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 32.dp)
    )

    Text(
      text = stringResource(R.string.PermissionDeniedBottomSheet__1_tap_settings_below),
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    if (useExtended) {
      Text(
        text = stringResource(R.string.PermissionDeniedBottomSheet__2_tap_permissions),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 24.dp)
      )
    }

    val stringId = if (useExtended) R.string.PermissionDeniedBottomSheet__3_allow_permission else R.string.PermissionDeniedBottomSheet__2_allow_permission
    val stepString = stringResource(id = stringId, PLACEHOLDER)
    val (stepText, stepInlineContent) = remember(stepString) {
      val parts = stepString.split(PLACEHOLDER)
      val annotatedString = buildAnnotatedString {
        append(parts[0])
        appendInlineContent("radio")
        append(parts[1])
      }

      val inlineContentMap = mapOf(
        "radio" to InlineTextContent(Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.Center)) {
          Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_radio_button_checked),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
          )
        }
      )

      annotatedString to inlineContentMap
    }

    Text(
      text = stepText,
      inlineContent = stepInlineContent,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    Buttons.LargeTonal(
      onClick = onSettingsClicked,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .fillMaxWidth(1f)
    ) {
      Text(text = stringResource(id = R.string.PermissionDeniedBottomSheet__settings))
    }
  }
}
