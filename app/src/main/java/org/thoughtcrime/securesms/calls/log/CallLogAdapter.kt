package org.thoughtcrime.securesms.calls.log

import android.content.res.ColorStateList
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.databinding.CallLogAdapterItemBinding
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

      val event = model.call.call.event
      val direction = model.call.call.direction
      val type = model.call.call.type

      binding.callRecipientAvatar.setAvatar(GlideApp.with(binding.callRecipientAvatar), model.call.peer, true)
      binding.callRecipientBadge.setBadgeFromRecipient(model.call.peer)
      binding.callRecipientName.text = model.call.peer.getDisplayName(context)
      presentCallInfo(event, direction, model.call.date)
      presentCallType(type, model.call.peer)
    }

    private fun presentCallInfo(event: CallTable.Event, direction: CallTable.Direction, date: Long) {
      binding.callInfo.text = context.getString(
        R.string.CallLogAdapter__s_dot_s,
        context.getString(getCallStateStringRes(event, direction)),
        DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), date)
      )

      binding.callInfo.setRelativeDrawables(
        start = getCallStateDrawableRes(event, direction)
      )

      val color = ContextCompat.getColor(
        context,
        if (event == CallTable.Event.MISSED) {
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

    private fun presentCallType(callType: CallTable.Type, peer: Recipient) {
      when (callType) {
        CallTable.Type.AUDIO_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_phone_24)
          binding.callType.setOnClickListener { onStartAudioCallClicked(peer) }
        }

        CallTable.Type.VIDEO_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_video_24)
          binding.callType.setOnClickListener { onStartVideoCallClicked(peer) }
        }
      }

      binding.callType.visible = true
    }

    @DrawableRes
    private fun getCallStateDrawableRes(callEvent: CallTable.Event, callDirection: CallTable.Direction): Int {
      if (callEvent == CallTable.Event.MISSED) {
        return R.drawable.symbol_missed_incoming_compact_16
      }

      return if (callDirection == CallTable.Direction.INCOMING) {
        R.drawable.symbol_arrow_downleft_compact_16
      } else {
        R.drawable.symbol_arrow_upright_compact_16
      }
    }

    @StringRes
    private fun getCallStateStringRes(callEvent: CallTable.Event, callDirection: CallTable.Direction): Int {
      if (callEvent == CallTable.Event.MISSED) {
        return R.string.CallLogAdapter__missed
      }

      return if (callDirection == CallTable.Direction.INCOMING) {
        R.string.CallLogAdapter__incoming
      } else {
        R.string.CallLogAdapter__outgoing
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

  interface Callbacks {
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
