package org.thoughtcrime.securesms.components

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Convenience class for wrapping Fragments in full-screen dialogs. Due to how fragments work, they
 * must be public static classes. Therefore, this class should be subclassed as its own entity, rather
 * than via `object : WrapperDialogFragment`.
 *
 * Example usage:
 *
 * ```
 * class Dialog : WrapperDialogFragment() {
 *   override fun getWrappedFragment(): Fragment {
 *     return NavHostFragment.create(R.navigation.private_story_settings, requireArguments())
 *   }
 * }
 *
 * companion object {
 *   fun createAsDialog(distributionListId: DistributionListId): DialogFragment {
 *     return Dialog().apply {
 *       arguments = PrivateStorySettingsFragmentArgs.Builder(distributionListId).build().toBundle()
 *     }
 *   }
 * }
 * ```
 */
abstract class WrapperDialogFragment : DialogFragment(R.layout.fragment_container) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          onHandleBackPressed()
        }
      }
    )

    if (savedInstanceState == null) {
      childFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, getWrappedFragment())
        .commitAllowingStateLoss()
    }
  }

  open fun onHandleBackPressed() {
    dismissAllowingStateLoss()
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    findListener<WrapperDialogFragmentCallback>()?.onWrapperDialogFragmentDismissed()
  }

  abstract fun getWrappedFragment(): Fragment

  interface WrapperDialogFragmentCallback {
    fun onWrapperDialogFragmentDismissed()
  }
}
