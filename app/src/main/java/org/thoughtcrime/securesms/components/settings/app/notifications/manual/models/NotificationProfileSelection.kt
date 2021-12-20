package org.thoughtcrime.securesms.components.settings.app.notifications.manual.models

import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import com.airbnb.lottie.SimpleColorFilter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.visible
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Notification Profile selection preference.
 */
object NotificationProfileSelection {

  private const val TOGGLE_EXPANSION = 0
  private const val UPDATE_TIMESLOT = 1

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(New::class.java, LayoutFactory(::NewViewHolder, R.layout.new_notification_profile_pref))
    adapter.registerFactory(Entry::class.java, LayoutFactory(::EntryViewHolder, R.layout.notification_profile_entry_pref))
  }

  class Entry(
    val isOn: Boolean,
    override val summary: DSLSettingsText,
    val notificationProfile: NotificationProfile,
    val isExpanded: Boolean,
    val timeSlotB: LocalDateTime,
    val onRowClick: (NotificationProfile) -> Unit,
    val onTimeSlotAClick: (NotificationProfile) -> Unit,
    val onTimeSlotBClick: (NotificationProfile, LocalDateTime) -> Unit,
    val onViewSettingsClick: (NotificationProfile) -> Unit,
    val onToggleClick: (NotificationProfile) -> Unit
  ) : PreferenceModel<Entry>() {

    override fun areItemsTheSame(newItem: Entry): Boolean {
      return notificationProfile.id == newItem.notificationProfile.id
    }

    override fun areContentsTheSame(newItem: Entry): Boolean {
      return super.areContentsTheSame(newItem) &&
        isOn == newItem.isOn &&
        notificationProfile == newItem.notificationProfile &&
        isExpanded == newItem.isExpanded &&
        timeSlotB == newItem.timeSlotB
    }

    override fun getChangePayload(newItem: Entry): Any? {
      return if (notificationProfile == newItem.notificationProfile && isExpanded != newItem.isExpanded) {
        TOGGLE_EXPANSION
      } else if (notificationProfile == newItem.notificationProfile && timeSlotB != newItem.timeSlotB) {
        UPDATE_TIMESLOT
      } else {
        null
      }
    }
  }

  class EntryViewHolder(itemView: View) : MappingViewHolder<Entry>(itemView) {

    private val image: EmojiImageView = findViewById(R.id.notification_preference_image)
    private val chevron: View = findViewById(R.id.notification_preference_chevron)
    private val name: TextView = findViewById(R.id.notification_preference_name)
    private val status: TextView = findViewById(R.id.notification_preference_status)
    private val expansion: Group = findViewById(R.id.notification_preference_expanded)
    private val timeSlotA: TextView = findViewById(R.id.notification_preference_1hr)
    private val timeSlotB: TextView = findViewById(R.id.notification_preference_6pm)
    private val viewSettings: View = findViewById(R.id.notification_preference_view_settings)

    override fun bind(model: Entry) {
      itemView.setOnClickListener { model.onRowClick(model.notificationProfile) }
      chevron.setOnClickListener { model.onToggleClick(model.notificationProfile) }
      chevron.rotation = if (model.isExpanded) 180f else 0f
      timeSlotA.setOnClickListener { model.onTimeSlotAClick(model.notificationProfile) }
      timeSlotB.setOnClickListener { model.onTimeSlotBClick(model.notificationProfile, model.timeSlotB) }
      viewSettings.setOnClickListener { model.onViewSettingsClick(model.notificationProfile) }

      expansion.visible = model.isExpanded
      timeSlotB.text = context.getString(
        R.string.NotificationProfileSelection__until_s,
        LocalTime.from(model.timeSlotB).formatHours(context)
      )

      if (TOGGLE_EXPANSION in payload || UPDATE_TIMESLOT in payload) {
        return
      }

      image.background.colorFilter = SimpleColorFilter(model.notificationProfile.color.colorInt())
      if (model.notificationProfile.emoji.isNotEmpty()) {
        image.setImageEmoji(model.notificationProfile.emoji)
      } else {
        image.setImageResource(R.drawable.ic_moon_24)
      }

      name.text = model.notificationProfile.name

      presentStatus(model)

      timeSlotB.text = context.getString(
        R.string.NotificationProfileSelection__until_s,
        LocalTime.from(model.timeSlotB).formatHours(context)
      )

      itemView.isSelected = model.isOn
    }

    private fun presentStatus(model: Entry) {
      status.isEnabled = model.isOn
      status.text = model.summary.resolve(context)
    }
  }

  class New(val onClick: () -> Unit) : PreferenceModel<New>() {
    override fun areItemsTheSame(newItem: New): Boolean {
      return true
    }
  }

  class NewViewHolder(itemView: View) : MappingViewHolder<New>(itemView) {
    override fun bind(model: New) {
      itemView.setOnClickListener { model.onClick() }
    }
  }
}
