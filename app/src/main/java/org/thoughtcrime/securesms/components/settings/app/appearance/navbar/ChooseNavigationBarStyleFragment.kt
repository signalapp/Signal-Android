package org.thoughtcrime.securesms.components.settings.app.appearance.navbar

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.ChooseNavigationBarStyleFragmentBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Allows the user to choose between a compact and full-sized navigation bar.
 */
class ChooseNavigationBarStyleFragment : DialogFragment(R.layout.choose_navigation_bar_style_fragment) {
  private val binding by ViewBinderDelegate(ChooseNavigationBarStyleFragmentBinding::bind)

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

    return dialog
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    presentToggleState(SignalStore.settings.useCompactNavigationBar)

    binding.toggle.addOnButtonCheckedListener { group, checkedId, isChecked ->
      if (isChecked) {
        presentToggleState(checkedId == R.id.compact)
      }
    }

    binding.ok.setOnClickListener {
      val isCompact = binding.toggle.checkedButtonId == R.id.compact
      SignalStore.settings.useCompactNavigationBar = isCompact
      dismissAllowingStateLoss()
      setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to true))
    }
  }

  private fun presentToggleState(isCompact: Boolean) {
    binding.toggle.check(if (isCompact) R.id.compact else R.id.normal)
    binding.image.setImageResource(PreviewImages.getImageResourceId(isCompact))
    binding.normal.setIconResource(if (isCompact) 0 else R.drawable.ic_check_20)
    binding.compact.setIconResource(if (isCompact) R.drawable.ic_check_20 else 0)
  }

  private sealed class PreviewImages(
    @DrawableRes private val compact: Int,
    @DrawableRes private val normal: Int
  ) {

    @DrawableRes
    fun getImageResource(isCompact: Boolean): Int {
      return if (isCompact) compact else normal
    }

    private object ThreeButtons : PreviewImages(
      compact = R.drawable.navbar_compact,
      normal = R.drawable.navbar_normal
    )

    private object TwoButtons : PreviewImages(
      compact = R.drawable.navbar_compact_2,
      normal = R.drawable.navbar_normal_2
    )

    companion object {
      @DrawableRes
      fun getImageResourceId(isCompact: Boolean): Int {
        return ThreeButtons.getImageResource(isCompact)
      }
    }
  }

  companion object {
    const val REQUEST_KEY = "ChooseNavigationBarStyle"
  }
}
