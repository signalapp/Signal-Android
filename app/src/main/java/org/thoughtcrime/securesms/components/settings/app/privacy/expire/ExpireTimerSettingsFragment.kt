package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.ExpirationUtil
import org.thoughtcrime.securesms.util.livedata.ProcessState
import org.thoughtcrime.securesms.util.livedata.distinctUntilChanged
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.time.Duration.Companion.seconds

/**
 * Depending on the arguments, can be used to set the universal expire timer, set expire timer
 * for a individual or group recipient, or select a value and return it via result.
 */
class ExpireTimerSettingsFragment : ComposeFragment() {

  private val viewModel: ExpireTimerSettingsViewModel by viewModels(
    ownerProducer = {
      NavHostFragment.findNavController(this).getViewModelStoreOwner(R.id.app_settings_expire_timer)
    },
    factoryProducer = {
      ExpireTimerSettingsViewModel.Factory(requireContext(), arguments.toConfig())
    }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewModel.state.distinctUntilChanged(ExpireTimerSettingsState::saveState).observe(viewLifecycleOwner) { state ->
      when (val saveState: ProcessState<Int> = state.saveState) {
        is ProcessState.Success -> {
          if (state.isGroupCreate) {
            requireActivity().setResult(Activity.RESULT_OK, Intent().putExtra(FOR_RESULT_VALUE, saveState.result))
          }
          requireActivity().onNavigateUp()
        }

        is ProcessState.Failure -> {
          val groupChangeFailureReason: GroupChangeFailureReason = saveState.throwable?.let(GroupChangeFailureReason::fromException) ?: GroupChangeFailureReason.OTHER
          Toast.makeText(context, GroupErrors.getUserDisplayMessage(groupChangeFailureReason), Toast.LENGTH_LONG).show()
          viewModel.resetError()
        }

        else -> Unit
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.observeAsState(ExpireTimerSettingsState())
    val callback = remember { DefaultExpireTimerSettingsScreenCallback(viewModel) }

    SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)) {
      ExpireTimerSettingsScreen(
        state = state,
        callback = callback
      )
    }
  }

  inner class DefaultExpireTimerSettingsScreenCallback(
    private val viewModel: ExpireTimerSettingsViewModel
  ) : ExpireTimerSettingsScreenCallback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onTimerSelected(seconds: Int) {
      viewModel.select(seconds)
    }

    override fun onCustomTimerClick() {
      NavHostFragment.findNavController(this@ExpireTimerSettingsFragment).safeNavigate(R.id.action_expireTimerSettingsFragment_to_customExpireTimerSelectDialog)
    }

    override fun onSaveClick() {
      viewModel.save()
    }
  }

  companion object {
    const val FOR_RESULT_VALUE = "for_result_value"
  }
}

@Composable
fun ExpireTimerSettingsScreen(
  state: ExpireTimerSettingsState,
  callback: ExpireTimerSettingsScreenCallback
) {
  val context = LocalContext.current
  val labels = context.resources.getStringArray(R.array.ExpireTimerSettingsFragment__labels)
  val values = context.resources.getIntArray(R.array.ExpireTimerSettingsFragment__values)

  Scaffolds.Settings(
    title = stringResource(R.string.PrivacySettingsFragment__disappearing_messages),
    onNavigationClick = callback::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      LazyColumn() {
        item {
          Rows.TextRow(
            label = stringResource(
              if (state.isForRecipient) {
                R.string.ExpireTimerSettingsFragment__when_enabled_new_messages_sent_and_received_in_this_chat_will_disappear_after_they_have_been_seen
              } else {
                R.string.ExpireTimerSettingsFragment__when_enabled_new_messages_sent_and_received_in_new_chats_started_by_you_will_disappear_after_they_have_been_seen
              }
            )
          )
        }

        items(labels.size) { index ->
          val label = labels[index]
          val seconds = values[index]

          Rows.RadioRow(
            selected = state.currentTimer == seconds,
            text = label,
            modifier = Modifier.clickable { callback.onTimerSelected(seconds) },
            enabled = true
          )
        }

        item {
          val hasCustomValue = values.none { it == state.currentTimer }
          val customSummary = if (hasCustomValue) {
            ExpirationUtil.getExpirationDisplayValue(context, state.currentTimer)
          } else {
            null
          }

          Rows.RadioRow(
            selected = hasCustomValue,
            text = stringResource(R.string.ExpireTimerSettingsFragment__custom_time),
            label = customSummary,
            modifier = Modifier.clickable { callback.onCustomTimerClick() },
            enabled = true
          )
        }
      }

      CircularProgressWrapper(
        isLoading = state.saveState is ProcessState.Working,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .horizontalGutters()
          .padding(bottom = 16.dp)
      ) {
        Buttons.LargeTonal(
          onClick = callback::onSaveClick,
          enabled = state.saveState is ProcessState.Idle
        ) {
          Text(text = stringResource(R.string.ExpireTimerSettingsFragment__save))
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun ExpireTimerSettingsScreenPreview() {
  var isLoading by remember {
    mutableStateOf(false)
  }

  LaunchedEffect(isLoading) {
    if (isLoading) {
      delay(3.seconds)
      isLoading = false
    }
  }

  val state = remember(isLoading) {
    ExpireTimerSettingsState(
      initialTimer = 0,
      userSetTimer = null,
      isForRecipient = false,
      isGroupCreate = false,
      saveState = if (isLoading) ProcessState.Working() else ProcessState.Idle()
    )
  }

  Previews.Preview {
    ExpireTimerSettingsScreen(
      state = state,
      callback = object : ExpireTimerSettingsScreenCallback {
        override fun onNavigationClick() = Unit
        override fun onTimerSelected(seconds: Int) = Unit
        override fun onCustomTimerClick() = Unit
        override fun onSaveClick() {
          isLoading = true
        }
      }
    )
  }
}

interface ExpireTimerSettingsScreenCallback {
  fun onNavigationClick()
  fun onTimerSelected(seconds: Int)
  fun onCustomTimerClick()
  fun onSaveClick()
}

private fun Bundle?.toConfig(): ExpireTimerSettingsViewModel.Config {
  if (this == null) {
    return ExpireTimerSettingsViewModel.Config()
  }

  val safeArguments: ExpireTimerSettingsFragmentArgs = ExpireTimerSettingsFragmentArgs.fromBundle(this)
  return ExpireTimerSettingsViewModel.Config(
    recipientId = safeArguments.recipientId,
    forResultMode = safeArguments.forResultMode,
    initialValue = safeArguments.initialValue
  )
}
