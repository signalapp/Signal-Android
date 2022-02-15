package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models

import android.view.View
import android.widget.ImageView
import com.airbnb.lottie.SimpleColorFilter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * DSL custom preference for showing no profiles/empty state.
 */
object NoNotificationProfiles {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.notification_profiles_empty))
  }

  class Model(val onClick: () -> Unit) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean = true
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val icon: ImageView = findViewById(R.id.notification_profiles_empty_icon)
    private val button: View = findViewById(R.id.notification_profiles_empty_create_profile)

    override fun bind(model: Model) {
      icon.background.colorFilter = SimpleColorFilter(AvatarColor.A100.colorInt())
      button.setOnClickListener { model.onClick() }
    }
  }
}
