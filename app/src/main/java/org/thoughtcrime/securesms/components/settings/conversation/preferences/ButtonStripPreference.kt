package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

/**
 * Renders a configurable strip of buttons
 */
object ButtonStripPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.conversation_settings_button_strip))
  }

  class Model(
    val state: State,
    val background: DSLSettingsIcon? = null,
    val onMessageClick: () -> Unit = {},
    val onVideoClick: () -> Unit = {},
    val onAudioClick: () -> Unit = {},
    val onMuteClick: () -> Unit = {},
    val onSearchClick: () -> Unit = {}
  ) : PreferenceModel<Model>() {
    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && state == newItem.state
    }

    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val message: View = itemView.findViewById(R.id.message)
    private val messageLabel: View = itemView.findViewById(R.id.message_label)
    private val videoCall: View = itemView.findViewById(R.id.start_video)
    private val videoLabel: View = itemView.findViewById(R.id.start_video_label)
    private val audioCall: ImageView = itemView.findViewById(R.id.start_audio)
    private val audioLabel: TextView = itemView.findViewById(R.id.start_audio_label)
    private val mute: ImageView = itemView.findViewById(R.id.mute)
    private val muteLabel: TextView = itemView.findViewById(R.id.mute_label)
    private val search: View = itemView.findViewById(R.id.search)
    private val searchLabel: View = itemView.findViewById(R.id.search_label)

    override fun bind(model: Model) {
      message.visible = model.state.isMessageAvailable
      messageLabel.visible = model.state.isMessageAvailable
      videoCall.visible = model.state.isVideoAvailable
      videoLabel.visible = model.state.isVideoAvailable
      audioCall.visible = model.state.isAudioAvailable
      audioLabel.visible = model.state.isAudioAvailable
      mute.visible = model.state.isMuteAvailable
      muteLabel.visible = model.state.isMuteAvailable
      search.visible = model.state.isSearchAvailable
      searchLabel.visible = model.state.isSearchAvailable

      if (model.state.isAudioSecure) {
        audioLabel.setText(R.string.ConversationSettingsFragment__audio)
        audioCall.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_phone_right_24))
      } else {
        audioLabel.setText(R.string.ConversationSettingsFragment__call)
        audioCall.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_phone_right_unlock_primary_accent_24))
      }

      if (model.state.isMuted) {
        mute.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_bell_disabled_24))
        muteLabel.setText(R.string.ConversationSettingsFragment__muted)
      } else {
        mute.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_bell_24))
        muteLabel.setText(R.string.ConversationSettingsFragment__mute)
      }

      if (model.background != null) {
        listOf(message, videoCall, audioCall, mute, search).forEach {
          it.background = model.background.resolve(context)
        }
      }

      message.setOnClickListener { model.onMessageClick() }
      videoCall.setOnClickListener { model.onVideoClick() }
      audioCall.setOnClickListener { model.onAudioClick() }
      mute.setOnClickListener { model.onMuteClick() }
      search.setOnClickListener { model.onSearchClick() }
    }
  }

  data class State(
    val isMessageAvailable: Boolean = false,
    val isVideoAvailable: Boolean = false,
    val isAudioAvailable: Boolean = false,
    val isMuteAvailable: Boolean = false,
    val isSearchAvailable: Boolean = false,
    val isAudioSecure: Boolean = false,
    val isMuted: Boolean = false,
  )
}
