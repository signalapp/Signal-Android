package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.ExpirationUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment that allows user to turn on screen lock and set a timer to lock
 */
class ScreenLockSettingsFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(ScreenLockSettingsFragment::class)
  }

  private val viewModel: ScreenLockSettingsViewModel by activityViewModels()

  private lateinit var biometricAuth: BiometricDeviceAuthentication
  private lateinit var biometricDeviceLockLauncher: ActivityResultLauncher<String>
  private lateinit var disableLockPromptInfo: BiometricPrompt.PromptInfo
  private lateinit var enableLockPromptInfo: BiometricPrompt.PromptInfo

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    biometricDeviceLockLauncher = registerForActivityResult(BiometricDeviceLockContract()) { result: Int ->
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        toggleScreenLock()
      }
    }

    enableLockPromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(requireContext().getString(R.string.ScreenLockSettingsFragment__use_signal_screen_lock))
      .setConfirmationRequired(true)
      .build()

    disableLockPromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(requireContext().getString(R.string.ScreenLockSettingsFragment__turn_off_signal_lock))
      .setConfirmationRequired(true)
      .build()

    biometricAuth = BiometricDeviceAuthentication(
      BiometricManager.from(requireActivity()),
      BiometricPrompt(requireActivity(), BiometricAuthenticationListener()),
      enableLockPromptInfo
    )
  }

  override fun onPause() {
    super.onPause()
    biometricAuth.cancelAuthentication()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val navController: NavController by remember { mutableStateOf(findNavController()) }

    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences_app_protection__screen_lock),
      onNavigationClick = { navController.popBackStack() },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      ScreenLockScreen(
        state = state,
        onChecked = { checked ->
          if (biometricAuth.canAuthenticate(requireContext()) && !checked) {
            biometricAuth.updatePromptInfo(disableLockPromptInfo)
            biometricAuth.authenticate(requireContext(), true) {
              biometricDeviceLockLauncher.launch(getString(R.string.ScreenLockSettingsFragment__turn_off_signal_lock))
            }
          } else if (biometricAuth.canAuthenticate(requireContext()) && checked) {
            biometricAuth.updatePromptInfo(enableLockPromptInfo)
            biometricAuth.authenticate(requireContext(), true) {
              biometricDeviceLockLauncher.launch(getString(R.string.ScreenLockSettingsFragment__use_screen_lock))
            }
          }
        },
        onTimeClicked = viewModel::setScreenLockTimeout,
        onCustomTimeClicked = { navController.safeNavigate(R.id.action_screenLockSettingsFragment_to_customScreenLockTimerSelectDialog) },
        modifier = Modifier.padding(contentPadding)
      )
    }
  }

  private fun toggleScreenLock() {
    viewModel.toggleScreenLock()

    val intent = Intent(requireContext(), KeyCachingService::class.java)
    intent.action = KeyCachingService.LOCK_TOGGLED_EVENT
    requireContext().startService(intent)

    ConversationUtil.refreshRecipientShortcuts()
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Authentication error: $errorCode")
      onAuthenticationFailed()
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Authentication succeeded")
      toggleScreenLock()
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Unable to authenticate")
    }
  }
}

@Composable
fun ScreenLockScreen(
  state: ScreenLockSettingsState,
  onChecked: (Boolean) -> Unit,
  onTimeClicked: (Long) -> Unit,
  onCustomTimeClicked: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.verticalScroll(rememberScrollState())) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        painter = painterResource(R.drawable.ic_screen_lock),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
      )
      Text(
        text = stringResource(id = R.string.ScreenLockSettingsFragment__your_android_device),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 40.dp, end = 40.dp, bottom = 24.dp)
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .padding(horizontal = 24.dp)
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(24.dp))
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 12.dp)
      ) {
        Text(stringResource(id = R.string.ScreenLockSettingsFragment__use_screen_lock))
        Spacer(Modifier.weight(1f))
        Switch(checked = state.screenLock, onCheckedChange = onChecked)
      }
    }

    if (state.screenLock) {
      val labels: List<String> = LocalContext.current.resources.getStringArray(R.array.ScreenLockSettingsFragment__labels).toList()
      val values: List<Long> = LocalContext.current.resources.getIntArray(R.array.ScreenLockSettingsFragment__values).map { it.toLong() }

      Text(
        stringResource(id = R.string.ScreenLockSettingsFragment__start_screen_lock),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 16.dp, start = 24.dp)
      )
      Column(Modifier.selectableGroup()) {
        var isCustomTime = true
        labels.zip(values).forEach { (label, seconds) ->
          val isSelected = seconds == state.screenLockActivityTimeout
          Row(
            Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 56.dp)
              .selectable(
                selected = isSelected,
                onClick = { onTimeClicked(seconds) },
                role = Role.RadioButton
              )
              .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(selected = isSelected, onClick = null)
            Text(
              text = label,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 16.dp)
            )
            isCustomTime = isCustomTime && !isSelected
          }
        }

        Row(
          Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
              selected = isCustomTime,
              onClick = onCustomTimeClicked,
              role = Role.RadioButton
            )
            .padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(selected = isCustomTime, onClick = null)
          Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
              text = stringResource(id = R.string.ScreenLockSettingsFragment__custom_time),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (isCustomTime && state.screenLockActivityTimeout > 0) {
              Text(
                text = ExpirationUtil.getExpirationDisplayValue(LocalContext.current, state.screenLockActivityTimeout.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@SignalPreview
@Composable
fun ScreenLockScreenPreview() {
  Previews.Preview {
    ScreenLockScreen(
      state = ScreenLockSettingsState(true, 60),
      onChecked = {},
      onTimeClicked = {},
      onCustomTimeClicked = {}
    )
  }
}
