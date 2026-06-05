package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

/**
 * Bottom sheet shown in message request state that explains that profile names are unverified
 */
class UnverifiedProfileNameBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.75f

  companion object {
    private const val FOR_GROUP_ARG = "for_group"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, forGroup: Boolean) {
      UnverifiedProfileNameBottomSheet()
        .apply {
          arguments = bundleOf(
            FOR_GROUP_ARG to forGroup
          )
        }
        .show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    ProfileNameSheet(
      forGroup = requireArguments().getBoolean(FOR_GROUP_ARG, false)
    )
  }
}

@Composable
private fun ProfileNameSheet(forGroup: Boolean = false) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    BottomSheets.Handle()

    val (imageVector, placeholder, text) =
      if (forGroup) {
        Triple(
          R.drawable.symbol_group_questionmark_bold_40,
          stringResource(R.string.ConversationFragment_group_names),
          stringResource(id = R.string.ProfileNameBottomSheet__group_names_on_signal, stringResource(R.string.ConversationFragment_group_names))
        )
      } else {
        Triple(
          R.drawable.symbol_person_questionmark_bold_40,
          stringResource(R.string.ConversationFragment_profile_names),
          stringResource(id = R.string.ProfileNameBottomSheet__profile_names_on_signal, stringResource(R.string.ConversationFragment_profile_names))
        )
      }

    Icon(
      imageVector = ImageVector.vectorResource(imageVector),
      contentDescription = null,
      tint = SignalTheme.colors.colorOnWarning,
      modifier = Modifier
        .padding(top = 30.dp, bottom = 20.dp)
        .clip(RoundedCornerShape(50))
        .background(SignalTheme.colors.colorWarning)
        .padding(horizontal = 18.dp, vertical = 8.dp)
        .size(32.dp)
    )

    val annotatedText = remember(text, placeholder) {
      buildAnnotatedString {
        val start = text.indexOf(placeholder)
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
          append(text.substring(start, start + placeholder.length))
        }
        append(text.substring(start + placeholder.length))
      }
    }

    Text(
      text = annotatedText,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(bottom = 20.dp)
    )

    BulletRow(stringResource(R.string.ProfileNameBottomSheet__signal_cant_verify))
    BulletRow(stringResource(R.string.ProfileNameBottomSheet__signal_will_never_contact))

    if (forGroup) {
      BulletRow(stringResource(R.string.ProfileNameBottomSheet__be_cautious_of_groups))
    } else {
      BulletRow(stringResource(R.string.ProfileNameBottomSheet__be_cautious_of_accounts))
    }

    BulletRow(stringResource(R.string.ProfileNameBottomSheet__dont_share_personal))

    Spacer(Modifier.size(55.dp))
  }
}

@Composable
private fun BulletRow(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "\u2022",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(end = 8.dp)
    )

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@DayNightPreviews
@Composable
private fun ProfileNameSheetPreview() {
  Previews.BottomSheetContentPreview {
    ProfileNameSheet()
  }
}

@DayNightPreviews
@Composable
private fun ProfileNameSheetGroupPreview() {
  Previews.BottomSheetContentPreview {
    ProfileNameSheet(forGroup = true)
  }
}
