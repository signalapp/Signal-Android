package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import org.thoughtcrime.securesms.compose.ComposeFragment

@OptIn(ExperimentalMaterial3Api::class)
class UsernameLinkSettingsFragment : ComposeFragment() {

  val viewModel: UsernameLinkSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val scope: CoroutineScope = rememberCoroutineScope()
    val navController: NavController by remember { mutableStateOf(findNavController()) }

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { contentPadding ->
      UsernameLinkShareScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        scope = scope,
        contentPadding = contentPadding,
        navController = navController
      )
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.onResume()
  }

  @Preview
  @Composable
  fun PreviewAll() {
    FragmentContent()
  }
}
