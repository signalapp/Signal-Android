package org.thoughtcrime.securesms.components.settings.conversation.preferences

import androidx.annotation.DrawableRes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.databinding.ConversationSettingsCallPreferenceItemBinding
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import java.util.Locale

/**
 * Renders a single call preference row when displaying call info.
 */
object CallPreference {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, ConversationSettingsCallPreferenceItemBinding::inflate))
  }

  class Model(
    val record: MessageRecord
  ) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = record.id == newItem.record.id

    override fun areContentsTheSame(newItem: Model): Boolean {
      return record.type == newItem.record.type &&
        record.isOutgoing == newItem.record.isOutgoing &&
        record.timestamp == newItem.record.timestamp &&
        record.id == newItem.record.id
    }
  }

  private class ViewHolder(binding: ConversationSettingsCallPreferenceItemBinding) : BindingViewHolder<Model, ConversationSettingsCallPreferenceItemBinding>(binding) {
    override fun bind(model: Model) {
      binding.callIcon.setImageResource(getCallIcon(model.record))
      binding.callType.text = getCallType(model.record)
      binding.callTime.text = getCallTime(model.record)
    }

    @DrawableRes
    private fun getCallIcon(messageRecord: MessageRecord): Int {
      return when (messageRecord.type) {
        MessageTypes.MISSED_VIDEO_CALL_TYPE, MessageTypes.MISSED_AUDIO_CALL_TYPE -> R.drawable.symbol_missed_incoming_24
        MessageTypes.INCOMING_AUDIO_CALL_TYPE, MessageTypes.INCOMING_VIDEO_CALL_TYPE -> R.drawable.symbol_arrow_downleft_24
        MessageTypes.OUTGOING_AUDIO_CALL_TYPE, MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> R.drawable.symbol_arrow_upright_24
        else -> error("Unexpected type ${messageRecord.type}")
      }
    }

    private fun getCallType(messageRecord: MessageRecord): String {
      val id = when (messageRecord.type) {
        MessageTypes.MISSED_VIDEO_CALL_TYPE -> R.string.MessageRecord_missed_voice_call
        MessageTypes.MISSED_AUDIO_CALL_TYPE -> R.string.MessageRecord_missed_video_call
        MessageTypes.INCOMING_AUDIO_CALL_TYPE -> R.string.MessageRecord_incoming_voice_call
        MessageTypes.INCOMING_VIDEO_CALL_TYPE -> R.string.MessageRecord_incoming_video_call
        MessageTypes.OUTGOING_AUDIO_CALL_TYPE -> R.string.MessageRecord_outgoing_voice_call
        MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> R.string.MessageRecord_outgoing_video_call
        else -> error("Unexpected type ${messageRecord.type}")
      }

      return context.getString(id)
    }

    private fun getCallTime(messageRecord: MessageRecord): String {
      return DateUtils.getOnlyTimeString(context, Locale.getDefault(), messageRecord.timestamp)
    }
  }
}
