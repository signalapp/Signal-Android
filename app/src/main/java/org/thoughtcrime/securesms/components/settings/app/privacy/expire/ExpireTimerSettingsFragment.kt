package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import com.dd.CircularProgressButton
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.util.ExpirationUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.livedata.ProcessState
import org.thoughtcrime.securesms.util.livedata.distinctUntilChanged
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Depending on the arguments, can be used to set the universal expire timer, set expire timer
 * for a individual or group recipient, or select a value and return it via result.
 */
class ExpireTimerSettingsFragment : DSLSettingsFragment(
  titleId = R.string.PrivacySettingsFragment__disappearing_messages,
  layoutId = R.layout.expire_timer_settings_fragment
) {

  private lateinit var save: CircularProgressButton
  private lateinit var viewModel: ExpireTimerSettingsViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    save = view.findViewById(R.id.timer_select_fragment_save)
    save.setOnClickListener { viewModel.save() }
    adjustListPaddingForSaveButton(view)
  }

  private fun adjustListPaddingForSaveButton(view: View) {
    val recycler: RecyclerView = view.findViewById(R.id.recycler)
    recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, ViewUtil.dpToPx(80))
    recycler.clipToPadding = false
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    val provider = ViewModelProvider(
      NavHostFragment.findNavController(this).getViewModelStoreOwner(R.id.app_settings_expire_timer),
      ExpireTimerSettingsViewModel.Factory(requireContext(), arguments.toConfig())
    )
    viewModel = provider.get(ExpireTimerSettingsViewModel::class.java)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    viewModel.state.distinctUntilChanged(ExpireTimerSettingsState::saveState).observe(viewLifecycleOwner) { state ->
      when (val saveState: ProcessState<Int> = state.saveState) {
        is ProcessState.Working -> {
          save.isClickable = false
          save.isIndeterminateProgressMode = true
          save.progress = 50
        }
        is ProcessState.Success -> {
          if (state.isGroupCreate) {
            requireActivity().setResult(Activity.RESULT_OK, Intent().putExtra(FOR_RESULT_VALUE, saveState.result))
          }
          save.isClickable = false
          requireActivity().onNavigateUp()
        }
        is ProcessState.Failure -> {
          val groupChangeFailureReason: GroupChangeFailureReason = saveState.throwable?.let(GroupChangeFailureReason::fromException) ?: GroupChangeFailureReason.OTHER
          Toast.makeText(context, GroupErrors.getUserDisplayMessage(groupChangeFailureReason), Toast.LENGTH_LONG).show()
          viewModel.resetError()
        }
        else -> {
          save.isClickable = true
          save.isIndeterminateProgressMode = false
          save.progress = 0
        }
      }
    }
  }

  private fun getConfiguration(state: ExpireTimerSettingsState): DSLConfiguration {
    return configure {
      textPref(
        summary = DSLSettingsText.from(
          if (state.isForRecipient) {
            R.string.ExpireTimerSettingsFragment__when_enabled_new_messages_sent_and_received_in_this_chat_will_disappear_after_they_have_been_seen
          } else {
            R.string.ExpireTimerSettingsFragment__when_enabled_new_messages_sent_and_received_in_new_chats_started_by_you_will_disappear_after_they_have_been_seen
          }
        )
      )

      val labels: Array<String> = resources.getStringArray(R.array.ExpireTimerSettingsFragment__labels)
      val values: Array<Int> = resources.getIntArray(R.array.ExpireTimerSettingsFragment__values).toTypedArray()

      var hasCustomValue = true
      labels.zip(values).forEach { (label, seconds) ->
        radioPref(
          title = DSLSettingsText.from(label),
          isChecked = state.currentTimer == seconds,
          onClick = { viewModel.select(seconds) }
        )
        hasCustomValue = hasCustomValue && state.currentTimer != seconds
      }

      radioPref(
        title = DSLSettingsText.from(R.string.ExpireTimerSettingsFragment__custom_time),
        summary = if (hasCustomValue) DSLSettingsText.from(ExpirationUtil.getExpirationDisplayValue(requireContext(), state.currentTimer)) else null,
        isChecked = hasCustomValue,
        onClick = { NavHostFragment.findNavController(this@ExpireTimerSettingsFragment).safeNavigate(R.id.action_expireTimerSettingsFragment_to_customExpireTimerSelectDialog) }
      )
    }
  }

  companion object {
    const val FOR_RESULT_VALUE = "for_result_value"
  }
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
