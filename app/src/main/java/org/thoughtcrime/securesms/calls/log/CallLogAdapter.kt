package org.thoughtcrime.securesms.calls.log

import android.content.res.ColorStateList
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.databinding.CallLogAdapterItemBinding
import org.thoughtcrime.securesms.databinding.CallLogCreateCallLinkItemBinding
import org.thoughtcrime.securesms.databinding.ConversationListItemClearFilterBinding
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.thoughtcrime.securesms.util.setRelativeDrawables
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * RecyclerView Adapter for the Call Log screen
 */
class CallLogAdapter(
  callbacks: Callbacks
) : PagingMappingAdapter<CallLogRow.Id>() {

  init {
    registerFactory(
      CallModel::class.java,
      BindingFactory(
        creator = {
          CallModelViewHolder(
            it,
            callbacks::onCallClicked,
            callbacks::onCallLongClicked,
            callbacks::onStartAudioCallClicked,
            callbacks::onStartVideoCallClicked
          )
        },
        inflater = CallLogAdapterItemBinding::inflate
      )
    )
    registerFactory(
      ClearFilterModel::class.java,
      BindingFactory(
        creator = { ClearFilterViewHolder(it, callbacks::onClearFilterClicked) },
        inflater = ConversationListItemClearFilterBinding::inflate
      )
    )
    registerFactory(
      CreateCallLinkModel::class.java,
      BindingFactory(
        creator = { CreateCallLinkViewHolder(it, callbacks::onCreateACallLinkClicked) },
        inflater = CallLogCreateCallLinkItemBinding::inflate
      )
    )
  }

  fun submitCallRows(
    rows: List<CallLogRow?>,
    selectionState: CallLogSelectionState,
    stagedDeletion: CallLogStagedDeletion?
  ): Int {
    val filteredRows = rows
      .filterNotNull()
      .filterNot { stagedDeletion?.isStagedForDeletion(it.id) == true }
      .map {
        when (it) {
          is CallLogRow.Call -> CallModel(it, selectionState, itemCount)
          is CallLogRow.ClearFilter -> ClearFilterModel()
          is CallLogRow.CreateCallLink -> CreateCallLinkModel()
        }
      }

    submitList(filteredRows)

    return filteredRows.size
  }

  private class CallModel(
    val call: CallLogRow.Call,
    val selectionState: CallLogSelectionState,
    val itemCount: Int
  ) : MappingModel<CallModel> {
    companion object {
      const val PAYLOAD_SELECTION_STATE = "PAYLOAD_SELECTION_STATE"
    }

    override fun areItemsTheSame(newItem: CallModel): Boolean = call.id == newItem.call.id
    override fun areContentsTheSame(newItem: CallModel): Boolean {
      return call == newItem.call &&
        isSelectionStateTheSame(newItem) &&
        isItemCountTheSame(newItem)
    }

    override fun getChangePayload(newItem: CallModel): Any? {
      return if (call == newItem.call && (!isSelectionStateTheSame(newItem) || !isItemCountTheSame(newItem))) {
        PAYLOAD_SELECTION_STATE
      } else {
        null
      }
    }

    private fun isSelectionStateTheSame(newItem: CallModel): Boolean {
      return selectionState.contains(call.id) == newItem.selectionState.contains(newItem.call.id) &&
        selectionState.isNotEmpty(itemCount) == newItem.selectionState.isNotEmpty(newItem.itemCount)
    }

    private fun isItemCountTheSame(newItem: CallModel): Boolean {
      return itemCount == newItem.itemCount
    }
  }

  private class ClearFilterModel : MappingModel<ClearFilterModel> {
    override fun areItemsTheSame(newItem: ClearFilterModel): Boolean = true
    override fun areContentsTheSame(newItem: ClearFilterModel): Boolean = true
  }

  private class CreateCallLinkModel : MappingModel<CreateCallLinkModel> {
    override fun areItemsTheSame(newItem: CreateCallLinkModel): Boolean = true

    override fun areContentsTheSame(newItem: CreateCallLinkModel): Boolean = true
  }

  private class CallModelViewHolder(
    binding: CallLogAdapterItemBinding,
    private val onCallClicked: (CallLogRow.Call) -> Unit,
    private val onCallLongClicked: (View, CallLogRow.Call) -> Boolean,
    private val onStartAudioCallClicked: (Recipient) -> Unit,
    private val onStartVideoCallClicked: (Recipient) -> Unit
  ) : BindingViewHolder<CallModel, CallLogAdapterItemBinding>(binding) {
    override fun bind(model: CallModel) {
      itemView.setOnClickListener {
        onCallClicked(model.call)
      }

      itemView.setOnLongClickListener {
        onCallLongClicked(itemView, model.call)
      }

      itemView.isSelected = model.selectionState.contains(model.call.id)
      binding.callSelected.isChecked = model.selectionState.contains(model.call.id)
      binding.callSelected.visible = model.selectionState.isNotEmpty(model.itemCount)

      if (payload.contains(CallModel.PAYLOAD_SELECTION_STATE)) {
        return
      }

      binding.callRecipientAvatar.setAvatar(GlideApp.with(binding.callRecipientAvatar), model.call.peer, true)
      binding.callRecipientBadge.setBadgeFromRecipient(model.call.peer)
      binding.callRecipientName.text = model.call.peer.getDisplayName(context)
      presentCallInfo(model.call, model.call.date)
      presentCallType(model)
    }

    private fun presentCallInfo(call: CallLogRow.Call, date: Long) {
      val callState = context.getString(getCallStateStringRes(call.record))
      binding.callInfo.text = context.getString(
        R.string.CallLogAdapter__s_dot_s,
        if (call.children.size > 1) {
          context.getString(R.string.CallLogAdapter__d_s, call.children.size, callState)
        } else {
          callState
        },
        DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), date)
      )

      binding.callInfo.setRelativeDrawables(
        start = getCallStateDrawableRes(call.record)
      )

      val color = ContextCompat.getColor(
        context,
        if (call.record.event == CallTable.Event.MISSED) {
          R.color.signal_colorError
        } else {
          R.color.signal_colorOnSurface
        }
      )

      TextViewCompat.setCompoundDrawableTintList(
        binding.callInfo,
        ColorStateList.valueOf(color)
      )

      binding.callInfo.setTextColor(color)
    }

    private fun presentCallType(model: CallModel) {
      when (model.call.record.type) {
        CallTable.Type.AUDIO_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_phone_24)
          binding.callType.setOnClickListener { onStartAudioCallClicked(model.call.peer) }
          binding.callType.visible = true
          binding.groupCallButton.visible = false
        }

        CallTable.Type.VIDEO_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_video_24)
          binding.callType.setOnClickListener { onStartVideoCallClicked(model.call.peer) }
          binding.callType.visible = true
          binding.groupCallButton.visible = false
        }

        CallTable.Type.GROUP_CALL, CallTable.Type.AD_HOC_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_video_24)
          binding.callType.setOnClickListener { onStartVideoCallClicked(model.call.peer) }
          binding.groupCallButton.setOnClickListener { onStartVideoCallClicked(model.call.peer) }

          when (model.call.groupCallState) {
            CallLogRow.GroupCallState.NONE, CallLogRow.GroupCallState.FULL -> {
              binding.callType.visible = true
              binding.groupCallButton.visible = false
            }
            CallLogRow.GroupCallState.ACTIVE, CallLogRow.GroupCallState.LOCAL_USER_JOINED -> {
              binding.callType.visible = false
              binding.groupCallButton.visible = true

              binding.groupCallButton.setText(
                if (model.call.groupCallState == CallLogRow.GroupCallState.LOCAL_USER_JOINED) {
                  R.string.CallLogAdapter__return
                } else {
                  R.string.CallLogAdapter__join
                }
              )
            }
          }
        }
      }
    }

    @DrawableRes
    private fun getCallStateDrawableRes(call: CallTable.Call): Int {
      return when (call.messageType) {
        MessageTypes.MISSED_VIDEO_CALL_TYPE, MessageTypes.MISSED_AUDIO_CALL_TYPE -> R.drawable.symbol_missed_incoming_compact_16
        MessageTypes.INCOMING_AUDIO_CALL_TYPE, MessageTypes.INCOMING_VIDEO_CALL_TYPE -> R.drawable.symbol_arrow_downleft_compact_16
        MessageTypes.OUTGOING_AUDIO_CALL_TYPE, MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> R.drawable.symbol_arrow_upright_compact_16
        MessageTypes.GROUP_CALL_TYPE -> when {
          call.event == CallTable.Event.MISSED -> R.drawable.symbol_missed_incoming_24
          call.event == CallTable.Event.GENERIC_GROUP_CALL || call.event == CallTable.Event.JOINED -> R.drawable.symbol_group_compact_16
          call.direction == CallTable.Direction.INCOMING -> R.drawable.symbol_arrow_downleft_compact_16
          call.direction == CallTable.Direction.OUTGOING -> R.drawable.symbol_arrow_upright_compact_16
          else -> throw AssertionError()
        }
        else -> error("Unexpected type ${call.type}")
      }
    }

    @StringRes
    private fun getCallStateStringRes(call: CallTable.Call): Int {
      return when (call.messageType) {
        MessageTypes.MISSED_VIDEO_CALL_TYPE -> R.string.CallLogAdapter__missed
        MessageTypes.MISSED_AUDIO_CALL_TYPE -> R.string.CallLogAdapter__missed
        MessageTypes.INCOMING_AUDIO_CALL_TYPE -> R.string.CallLogAdapter__incoming
        MessageTypes.INCOMING_VIDEO_CALL_TYPE -> R.string.CallLogAdapter__incoming
        MessageTypes.OUTGOING_AUDIO_CALL_TYPE -> R.string.CallLogAdapter__outgoing
        MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> R.string.CallLogAdapter__outgoing
        MessageTypes.GROUP_CALL_TYPE -> when {
          call.event == CallTable.Event.MISSED -> R.string.CallLogAdapter__missed
          call.event == CallTable.Event.GENERIC_GROUP_CALL || call.event == CallTable.Event.JOINED -> R.string.CallPreference__group_call
          call.direction == CallTable.Direction.INCOMING -> R.string.CallLogAdapter__incoming
          call.direction == CallTable.Direction.OUTGOING -> R.string.CallLogAdapter__outgoing
          else -> throw AssertionError()
        }
        else -> error("Unexpected type ${call.messageType}")
      }
    }
  }

  private class ClearFilterViewHolder(
    binding: ConversationListItemClearFilterBinding,
    onClearFilterClicked: () -> Unit
  ) : BindingViewHolder<ClearFilterModel, ConversationListItemClearFilterBinding>(binding) {

    init {
      binding.clearFilter.setOnClickListener { onClearFilterClicked() }
    }

    override fun bind(model: ClearFilterModel) = Unit
  }

  private class CreateCallLinkViewHolder(
    binding: CallLogCreateCallLinkItemBinding,
    onClick: () -> Unit
  ) : BindingViewHolder<CreateCallLinkModel, CallLogCreateCallLinkItemBinding>(binding) {
    init {
      binding.root.setOnClickListener { onClick() }
    }

    override fun bind(model: CreateCallLinkModel) = Unit
  }

  interface Callbacks {
    /**
     * Invoked when 'Create a call link' is clicked
     */
    fun onCreateACallLinkClicked()

    /**
     * Invoked when a call row is clicked
     */
    fun onCallClicked(callLogRow: CallLogRow.Call)

    /**
     * Invoked when a call row is long-clicked
     */
    fun onCallLongClicked(itemView: View, callLogRow: CallLogRow.Call): Boolean

    /**
     * Invoked when the clear filter button is pressed
     */
    fun onClearFilterClicked()

    /**
     * Invoked when user presses the audio icon
     */
    fun onStartAudioCallClicked(peer: Recipient)

    /**
     * Invoked when user presses the video icon
     */
    fun onStartVideoCallClicked(peer: Recipient)
  }
}
