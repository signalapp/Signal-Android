package org.thoughtcrime.securesms.stories.settings.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.google.android.material.radiobutton.MaterialRadioButton
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.WrapperDialogFragment
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.settings.connections.ViewAllSignalConnectionsFragment
import org.thoughtcrime.securesms.stories.settings.select.BaseStoryRecipientSelectionFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.visible

/**
 * Choose the initial settings for My Story when first sending to My Story.
 */
class ChooseInitialMyStoryMembershipBottomSheetDialogFragment :
  FixedRoundedCornerBottomSheetDialogFragment(),
  WrapperDialogFragment.WrapperDialogFragmentCallback,
  BaseStoryRecipientSelectionFragment.Callback {

  override val peekHeightPercentage: Float = 1f

  private val viewModel: ChooseInitialMyStoryMembershipViewModel by viewModels()

  private lateinit var lifecycleDisposable: LifecycleDisposable

  private lateinit var allRow: View
  private lateinit var allExceptRow: View
  private lateinit var onlyWitRow: View

  private lateinit var allRadio: MaterialRadioButton
  private lateinit var allExceptRadio: MaterialRadioButton
  private lateinit var onlyWitRadio: MaterialRadioButton

  private lateinit var allCount: TextView
  private lateinit var allExceptCount: TextView
  private lateinit var onlyWithCount: TextView

  private lateinit var allView: View

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.choose_initial_my_story_membership_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    allRow = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_row)
    allExceptRow = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_except_row)
    onlyWitRow = view.findViewById(R.id.choose_initial_my_story_only_share_with_row)

    allRadio = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_radio)
    allExceptRadio = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_except_radio)
    onlyWitRadio = view.findViewById(R.id.choose_initial_my_story_only_share_with_radio)

    allCount = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_count)
    allExceptCount = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_except_count)
    onlyWithCount = view.findViewById(R.id.choose_initial_my_story_only_share_with_count)

    allView = view.findViewById(R.id.choose_initial_my_story_all_signal_connnections_view)
    allView.setOnClickListener {
      ViewAllSignalConnectionsFragment.Dialog.show(parentFragmentManager)
    }

    val save = view.findViewById<View>(R.id.choose_initial_my_story_save).apply {
      isEnabled = false
    }

    lifecycleDisposable = LifecycleDisposable().apply { bindTo(viewLifecycleOwner) }

    lifecycleDisposable += viewModel.state
      .subscribe { state ->
        allRadio.isChecked = state.privacyState.privacyMode == DistributionListPrivacyMode.ALL && state.hasUserPerformedManualSelection
        allExceptRadio.isChecked = state.privacyState.privacyMode == DistributionListPrivacyMode.ALL_EXCEPT && state.hasUserPerformedManualSelection
        onlyWitRadio.isChecked = state.privacyState.privacyMode == DistributionListPrivacyMode.ONLY_WITH && state.hasUserPerformedManualSelection

        allExceptCount.visible = allExceptRadio.isChecked
        onlyWithCount.visible = onlyWitRadio.isChecked

        allCount.visible = state.allSignalConnectionsCount > 0
        allCount.text = resources.getQuantityString(R.plurals.MyStorySettingsFragment__viewers, state.allSignalConnectionsCount, state.allSignalConnectionsCount)

        when (state.privacyState.privacyMode) {
          DistributionListPrivacyMode.ALL_EXCEPT -> allExceptCount.text = resources.getQuantityString(R.plurals.MyStorySettingsFragment__d_people_excluded, state.privacyState.connectionCount, state.privacyState.connectionCount)
          DistributionListPrivacyMode.ONLY_WITH -> onlyWithCount.text = resources.getQuantityString(R.plurals.MyStorySettingsFragment__d_people, state.privacyState.connectionCount, state.privacyState.connectionCount)
          else -> Unit
        }

        save.isEnabled = state.recipientId != null && state.hasUserPerformedManualSelection
      }

    val clickListener = { v: View ->
      val selection = when (v) {
        allRow -> DistributionListPrivacyMode.ALL
        allExceptRow -> DistributionListPrivacyMode.ALL_EXCEPT
        onlyWitRow -> DistributionListPrivacyMode.ONLY_WITH
        else -> throw AssertionError()
      }

      viewModel
        .select(selection)
        .subscribe { confirmedSelection ->
          when (confirmedSelection) {
            DistributionListPrivacyMode.ALL_EXCEPT -> AllExceptFragment.createAsDialog().show(childFragmentManager, SELECTION_FRAGMENT)
            DistributionListPrivacyMode.ONLY_WITH -> OnlyShareWithFragment.createAsDialog().show(childFragmentManager, SELECTION_FRAGMENT)
            else -> Unit
          }
        }
    }

    listOf(allRow, allExceptRow, onlyWitRow).forEach { it.setOnClickListener { v -> clickListener(v) } }

    save.setOnClickListener {
      lifecycleDisposable += viewModel
        .save()
        .subscribe { recipientId ->
          dismissAllowingStateLoss()
          findListener<Callback>()?.onMyStoryConfigured(recipientId)
        }
    }
  }

  override fun exitFlow() {
    (childFragmentManager.findFragmentByTag(SELECTION_FRAGMENT) as? DialogFragment)?.dismissAllowingStateLoss()
  }

  override fun onWrapperDialogFragmentDismissed() = Unit

  companion object {
    private const val SELECTION_FRAGMENT = "selection_fragment"

    fun show(fragmentManager: FragmentManager) {
      val fragment = ChooseInitialMyStoryMembershipBottomSheetDialogFragment()
      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  interface Callback {
    fun onMyStoryConfigured(recipientId: RecipientId)
  }
}
