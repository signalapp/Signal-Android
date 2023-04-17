package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shown at the end of the profile create flow.
 */
class NotificationProfileCreatedFragment : LoggingFragment(R.layout.fragment_notification_profile_created) {

  private val lifecycleDisposable = LifecycleDisposable()
  private val profileId: Long by lazy { NotificationProfileCreatedFragmentArgs.fromBundle(requireArguments()).profileId }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val topIcon: ImageView = view.findViewById(R.id.notification_profile_created_top_image)
    val topText: TextView = view.findViewById(R.id.notification_profile_created_top_text)
    val bottomIcon: ImageView = view.findViewById(R.id.notification_profile_created_bottom_image)
    val bottomText: TextView = view.findViewById(R.id.notification_profile_created_bottom_text)

    view.findViewById<View>(R.id.notification_profile_created_done).setOnClickListener {
      findNavController().safeNavigate(NotificationProfileCreatedFragmentDirections.actionNotificationProfileCreatedFragmentToNotificationProfileDetailsFragment(profileId))
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)

    val repository = NotificationProfilesRepository()
    lifecycleDisposable += repository.getProfile(profileId)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onNext = { profile ->
          if (profile.schedule.enabled) {
            topIcon.setImageResource(R.drawable.ic_recent_20)
            topText.setText(R.string.NotificationProfileCreated__your_profile_will_turn_on_and_off_automatically_according_to_your_schedule)

            bottomIcon.setImageResource(R.drawable.ic_more_vert_24)
            bottomText.setText(R.string.NotificationProfileCreated__you_can_turn_your_profile_on_or_off_manually_via_the_menu_on_the_chat_list)
          } else {
            topIcon.setImageResource(R.drawable.ic_more_vert_24)
            topText.setText(R.string.NotificationProfileCreated__you_can_turn_your_profile_on_or_off_manually_via_the_menu_on_the_chat_list)

            bottomIcon.setImageResource(R.drawable.ic_recent_20)
            bottomText.setText(R.string.NotificationProfileCreated__add_a_schedule_in_settings_to_automate_your_profile)
          }
        }
      )
  }
}
