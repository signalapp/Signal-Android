package org.thoughtcrime.securesms

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.compose.ComposeFragment

/**
 * Fragment when inviting someone to use Signal
 */
class InviteFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    Scaffolds.Settings(
      title = stringResource(id = R.string.AndroidManifest__invite_friends),
      onNavigationClick = { requireActivity().onNavigateUp() },
      navigationIcon = ImageVector.vectorResource(id = R.drawable.symbol_arrow_start_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      InviteScreen(
        onShare = { inviteText -> onShare(inviteText) },
        modifier = Modifier.padding(contentPadding)
      )
    }
  }

  private fun onShare(inviteText: String) {
    val sendIntent = Intent()
      .setAction(Intent.ACTION_SEND)
      .putExtra(Intent.EXTRA_TEXT, inviteText)
      .setType("text/plain")
    if (sendIntent.resolveActivity(requireContext().packageManager) != null) {
      startActivity(Intent.createChooser(sendIntent, getString(R.string.InviteActivity_invite_to_signal)))
    } else {
      Toast.makeText(requireContext(), R.string.InviteActivity_no_app_to_share_to, Toast.LENGTH_LONG).show()
    }
  }
}

@Composable
fun InviteScreen(
  onShare: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val default = stringResource(R.string.InviteActivity_lets_switch_to_signal, stringResource(R.string.install_url))
  var inviteText by remember { mutableStateOf(TextFieldValue(default, TextRange(default.length))) }

  Column(
    modifier = modifier.padding(16.dp).fillMaxHeight()
  ) {
    TextField(
      value = inviteText,
      onValueChange = { inviteText = it },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      shape = RoundedCornerShape(12.dp)
    )

    Row(
      modifier = Modifier
        .clickable(onClick = { onShare(inviteText.text) })
        .fillMaxWidth()
        .padding(vertical = 16.dp)
    ) {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_share_android_24),
        contentDescription = stringResource(R.string.InviteActivity_share)
      )

      Text(
        text = stringResource(id = R.string.InviteActivity_share),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp)
      )
    }
  }
}

@SignalPreview
@Composable
private fun InviteScreenPreview() {
  Previews.Preview {
    InviteScreen()
  }
}
