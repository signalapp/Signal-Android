package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeBadge
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.compose.ComposeFragment

/**
 * Gives the user the ability to change the color of their shareable username QR code with a live preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
class UsernameLinkQrColorPickerFragment : ComposeFragment() {

  val viewModel: UsernameLinkQrColorPickerViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state: UsernameLinkQrColorPickerState by viewModel.state
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        TopAppBarContent(
          scrollBehavior = scrollBehavior,
          onBackClicked = { navController.popBackStack() }
        )
      }
    ) { contentPadding ->
      Column(
        modifier = Modifier
          .padding(contentPadding)
          .verticalScroll(rememberScrollState())
          .fillMaxWidth()
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        QrCodeBadge(
          data = state.qrCodeData,
          colorScheme = state.selectedColorScheme,
          username = state.username,
          modifier = Modifier.padding(horizontal = 58.dp, vertical = 24.dp)
        )

        ColorPicker(
          colors = state.colorSchemes,
          selected = state.selectedColorScheme,
          onSelectionChanged = { color -> viewModel.onColorSelected(color) }
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(end = 24.dp, bottom = 24.dp),
          horizontalArrangement = Arrangement.End
        ) {
          Buttons.MediumTonal(onClick = { navController.popBackStack() }) {
            Text(stringResource(R.string.UsernameLinkSettings_done_button_label))
          }
        }
      }
    }
  }

  @Composable
  private fun TopAppBarContent(
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    onBackClicked: () -> Unit
  ) {
    TopAppBar(
      title = {
        Text(stringResource(R.string.UsernameLinkSettings_color_picker_app_bar_title))
      },
      navigationIcon = {
        IconButton(onClick = onBackClicked) {
          Icon(
            painter = painterResource(R.drawable.symbol_arrow_left_24),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = null
          )
        }
      },
      scrollBehavior = scrollBehavior
    )
  }

  @Composable
  private fun ColorPicker(colors: ImmutableList<UsernameQrCodeColorScheme>, selected: UsernameQrCodeColorScheme, onSelectionChanged: (UsernameQrCodeColorScheme) -> Unit) {
    LazyVerticalGrid(
      modifier = Modifier.padding(horizontal = 30.dp).heightIn(max = 880.dp),
      columns = GridCells.Adaptive(minSize = 88.dp)
    ) {
      colors.forEach { color ->
        item(key = color.serialize()) {
          ColorPickerItem(
            color = color,
            selected = color == selected,
            onClick = {
              onSelectionChanged(color)
            }
          )
        }
      }
    }
  }

  @Composable
  private fun ColorPickerItem(color: UsernameQrCodeColorScheme, selected: Boolean, onClick: () -> Unit) {
    val outerBorderColor by animateColorAsState(targetValue = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
    val colorCircleSize by animateFloatAsState(targetValue = if (selected) 44f else 56f)

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Column(
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 13.dp)
          .border(width = 2.dp, color = outerBorderColor, shape = CircleShape)
          .size(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Surface(
          onClick = onClick,
          modifier = Modifier
            .border(width = 2.dp, color = Color.Black.copy(alpha = 0.12f), shape = CircleShape)
            .size(colorCircleSize.dp),
          shape = CircleShape,
          color = color.borderColor,
          content = {}
        )
      }
    }
  }

  @Preview
  @Composable
  private fun PreviewColorPickerItem() {
    SignalTheme(isDarkMode = false) {
      Surface {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ColorPickerItem(color = UsernameQrCodeColorScheme.Blue, selected = false, onClick = {})
          ColorPickerItem(color = UsernameQrCodeColorScheme.Blue, selected = true, onClick = {})
        }
      }
    }
  }

  @Preview
  @Composable
  private fun PreviewColorPicker() {
    SignalTheme(isDarkMode = false) {
      Surface {
        ColorPicker(
          colors = UsernameQrCodeColorScheme.entries.toImmutableList(),
          selected = UsernameQrCodeColorScheme.Blue,
          onSelectionChanged = {}
        )
      }
    }
  }
}
