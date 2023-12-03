package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.view.View
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.views.LearnMoreTextView

object LegacyGroupPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.conversation_settings_legacy_group_preference))
  }

  class Model(
    val state: State,
    val onLearnMoreClick: () -> Unit,
    val onMmsWarningClick: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return state == newItem.state
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val groupInfoText: LearnMoreTextView = findViewById(R.id.manage_group_info_text)

    override fun bind(model: Model) {
      itemView.visibility = View.VISIBLE

      groupInfoText.setLinkColor(ContextCompat.getColor(context, R.color.signal_text_primary))

      when (model.state) {
        State.LEARN_MORE -> {
          groupInfoText.setText(R.string.ManageGroupActivity_legacy_group_learn_more)
          groupInfoText.setOnLinkClickListener { model.onLearnMoreClick() }
          groupInfoText.setLearnMoreVisible(true)
        }
        State.MMS_WARNING -> {
          groupInfoText.setText(R.string.ManageGroupActivity_this_is_an_insecure_mms_group)
          groupInfoText.setOnLinkClickListener { model.onMmsWarningClick() }
          groupInfoText.setLearnMoreVisible(true, R.string.ManageGroupActivity_invite_now)
        }
        State.NONE -> itemView.visibility = View.GONE
      }
    }
  }

  enum class State {
    LEARN_MORE,
    MMS_WARNING,
    NONE
  }
}
