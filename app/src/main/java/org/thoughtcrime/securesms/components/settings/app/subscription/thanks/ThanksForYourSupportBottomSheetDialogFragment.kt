package org.thoughtcrime.securesms.components.settings.app.subscription.thanks

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment

class ThanksForYourSupportBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  private lateinit var displayOnProfileSwitch: SwitchMaterial
  private lateinit var heading: TextView

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.thanks_for_your_support_bottom_sheet_dialog_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val badgeView: BadgeImageView = view.findViewById(R.id.thanks_bottom_sheet_badge)
    val badgeName: TextView = view.findViewById(R.id.thanks_bottom_sheet_badge_name)
    val done: MaterialButton = view.findViewById(R.id.thanks_bottom_sheet_done)

    heading = view.findViewById(R.id.thanks_bottom_sheet_heading)
    displayOnProfileSwitch = view.findViewById(R.id.thanks_bottom_sheet_display_on_profile)

    val args = ThanksForYourSupportBottomSheetDialogFragmentArgs.fromBundle(requireArguments())

    badgeView.setBadge(args.badge)
    badgeName.text = args.badge.name
    displayOnProfileSwitch.isChecked = true

    if (args.isBoost) {
      presentBoostCopy()
    } else {
      presentSubscriptionCopy()
    }

    done.setOnClickListener { dismissAllowingStateLoss() }
  }

  override fun onDismiss(dialog: DialogInterface) {
    val isDisplayOnProfile = displayOnProfileSwitch.isChecked
    // TODO [alex] -- Not sure what state we're in with regards to submitting the token.
  }

  private fun presentBoostCopy() {
    heading.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__thanks_for_the_boost)
  }

  private fun presentSubscriptionCopy() {
    heading.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__thanks_for_your_support)
  }
}
