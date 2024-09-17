package org.thoughtcrime.securesms.calls.log

import android.content.res.ColorStateList
import android.text.style.TextAppearanceSpan
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.bumptech.glide.Glide
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.databinding.CallLogAdapterItemBinding
import org.thoughtcrime.securesms.databinding.CallLogCreateCallLinkItemBinding
import org.thoughtcrime.securesms.databinding.ConversationListItemClearFilterBinding
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SearchUtil
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

  companion object {
    private const val PAYLOAD_SELECTION_STATE = "PAYLOAD_SELECTION_STATE"
    private const val PAYLOAD_TIMESTAMP = "PAYLOAD_TIMESTAMP"
  }

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

    registerFactory(
      CallLinkModel::class.java,
      BindingFactory(
        creator = { CallLinkModelViewHolder(it, callbacks::onCallLinkClicked, callbacks::onCallLinkLongClicked, callbacks::onStartVideoCallClicked) },
        inflater = CallLogAdapterItemBinding::inflate
      )
    )
  }

  fun onTimestampTick() {
    notifyItemRangeChanged(0, itemCount, PAYLOAD_TIMESTAMP)
  }

  fun submitCallRows(
    rows: List<CallLogRow?>,
    selectionState: CallLogSelectionState,
    localCallRecipientId: RecipientId,
    onCommit: () -> Unit
  ): Int {
    val filteredRows = rows
      .filterNotNull()
      .map {
        when (it) {
          is CallLogRow.Call -> CallModel(it, selectionState, itemCount, it.peer.id == localCallRecipientId)
          is CallLogRow.CallLink -> CallLinkModel(it, selectionState, itemCount, it.recipient.id == localCallRecipientId)
          is CallLogRow.ClearFilter -> ClearFilterModel()
          is CallLogRow.CreateCallLink -> CreateCallLinkModel()
        }
      }

    submitList(filteredRows, onCommit)

    return filteredRows.size
  }

  private class CallModel(
    val call: CallLogRow.Call,
    val selectionState: CallLogSelectionState,
    val itemCount: Int,
    val isLocalDeviceInCall: Boolean
  ) : MappingModel<CallModel> {

    override fun areItemsTheSame(newItem: CallModel): Boolean = call.id == newItem.call.id
    override fun areContentsTheSame(newItem: CallModel): Boolean {
      return call == newItem.call &&
        isSelectionStateTheSame(newItem) &&
        isItemCountTheSame(newItem) &&
        isLocalDeviceInCall == newItem.isLocalDeviceInCall
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

  private class CallLinkModel(
    val callLink: CallLogRow.CallLink,
    val selectionState: CallLogSelectionState,
    val itemCount: Int,
    val isLocalDeviceInCall: Boolean
  ) : MappingModel<CallLinkModel> {

    override fun areItemsTheSame(newItem: CallLinkModel): Boolean {
      return callLink.record.roomId == newItem.callLink.record.roomId
    }

    override fun areContentsTheSame(newItem: CallLinkModel): Boolean {
      return callLink == newItem.callLink &&
        isSelectionStateTheSame(newItem) &&
        isItemCountTheSame(newItem) &&
        isLocalDeviceInCall == newItem.isLocalDeviceInCall
    }

    override fun getChangePayload(newItem: CallLinkModel): Any? {
      return if (callLink == newItem.callLink && (!isSelectionStateTheSame(newItem) || !isItemCountTheSame(newItem))) {
        PAYLOAD_SELECTION_STATE
      } else {
        null
      }
    }

    private fun isSelectionStateTheSame(newItem: CallLinkModel): Boolean {
      return selectionState.contains(callLink.id) == newItem.selectionState.contains(newItem.callLink.id) &&
        selectionState.isNotEmpty(itemCount) == newItem.selectionState.isNotEmpty(newItem.itemCount)
    }

    private fun isItemCountTheSame(newItem: CallLinkModel): Boolean {
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

  private class CallLinkModelViewHolder(
    binding: CallLogAdapterItemBinding,
    private val onCallLinkClicked: (CallLogRow.CallLink) -> Unit,
    private val onCallLinkLongClicked: (View, CallLogRow.CallLink) -> Boolean,
    private val onStartVideoCallClicked: (Recipient, Boolean) -> Unit
  ) : BindingViewHolder<CallLinkModel, CallLogAdapterItemBinding>(binding) {
    override fun bind(model: CallLinkModel) {
      if (payload.size == 1 && payload.contains(PAYLOAD_TIMESTAMP)) {
        return
      }

      itemView.setOnClickListener {
        onCallLinkClicked(model.callLink)
      }

      itemView.setOnLongClickListener {
        onCallLinkLongClicked(itemView, model.callLink)
      }

      itemView.isSelected = model.selectionState.contains(model.callLink.id)
      binding.callSelected.isChecked = model.selectionState.contains(model.callLink.id)
      binding.callSelected.visible = model.selectionState.isNotEmpty(model.itemCount)

      if (payload.isNotEmpty()) {
        return
      }

      binding.callRecipientAvatar.setAvatar(model.callLink.recipient)

      val callLinkName = model.callLink.record.state.name.takeIf { it.isNotEmpty() }
        ?: context.getString(R.string.WebRtcCallView__signal_call)

      binding.callRecipientName.text = SearchUtil.getHighlightedSpan(
        Locale.getDefault(),
        { arrayOf(TextAppearanceSpan(context, R.style.Signal_Text_TitleSmall)) },
        callLinkName,
        model.callLink.searchQuery,
        SearchUtil.MATCH_ALL
      )

      binding.callInfo.setRelativeDrawables(start = R.drawable.symbol_link_compact_16)
      binding.callInfo.setText(R.string.CallLogAdapter__call_link)

      TextViewCompat.setCompoundDrawableTintList(
        binding.callInfo,
        ColorStateList.valueOf(
          ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant)
        )
      )

      if (model.callLink.callLinkPeekInfo?.isActive == true) {
        binding.groupCallButton.setText(
          if (model.callLink.callLinkPeekInfo.isJoined && model.isLocalDeviceInCall) {
            R.string.CallLogAdapter__return
          } else {
            R.string.CallLogAdapter__join
          }
        )
        binding.groupCallButton.setOnClickListener {
          onStartVideoCallClicked(model.callLink.recipient, true)
        }
        binding.callType.visible = false
        binding.groupCallButton.visible = true
      } else {
        binding.callType.setImageResource(R.drawable.symbol_video_24)
        binding.callType.contentDescription = context.getString(R.string.CallLogAdapter__start_a_video_call)
        binding.callType.setOnClickListener {
          onStartVideoCallClicked(model.callLink.recipient, true)
        }
        binding.callType.visible = true
        binding.groupCallButton.visible = false
      }
    }
  }

  private class CallModelViewHolder(
    binding: CallLogAdapterItemBinding,
    private val onCallClicked: (CallLogRow.Call) -> Unit,
    private val onCallLongClicked: (View, CallLogRow.Call) -> Boolean,
    private val onStartAudioCallClicked: (Recipient) -> Unit,
    private val onStartVideoCallClicked: (Recipient, Boolean) -> Unit
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

      if (payload.contains(PAYLOAD_TIMESTAMP)) {
        presentCallInfo(model.call, model.call.date)
      }

      if (payload.isNotEmpty()) {
        return
      }

      presentRecipientDetails(model.call.peer, model.call.searchQuery)
      presentCallInfo(model.call, model.call.date)
      presentCallType(model)
    }

    private fun presentRecipientDetails(recipient: Recipient, searchQuery: String?) {
      binding.callRecipientAvatar.setAvatar(Glide.with(binding.callRecipientAvatar), recipient, true)
      binding.callRecipientBadge.setBadgeFromRecipient(recipient)
      binding.callRecipientName.text = if (searchQuery != null) {
        SearchUtil.getHighlightedSpan(
          Locale.getDefault(),
          { arrayOf(TextAppearanceSpan(context, R.style.Signal_Text_TitleSmall)) },
          recipient.getDisplayName(context),
          searchQuery,
          SearchUtil.MATCH_ALL
        )
      } else {
        recipient.getDisplayName(context)
      }
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
        if (call.record.isDisplayedAsMissedCallInUi) {
          R.color.signal_colorError
        } else {
          R.color.signal_colorOnSurfaceVariant
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
          binding.callType.contentDescription = context.getString(R.string.CallLogAdapter__start_a_voice_call)
          binding.callType.setOnClickListener { onStartAudioCallClicked(model.call.peer) }
          binding.callType.visible = true
          binding.groupCallButton.visible = false
        }

        CallTable.Type.VIDEO_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_video_24)
          binding.callType.contentDescription = context.getString(R.string.CallLogAdapter__start_a_video_call)
          binding.callType.setOnClickListener { onStartVideoCallClicked(model.call.peer, true) }
          binding.callType.visible = true
          binding.groupCallButton.visible = false
        }

        CallTable.Type.AD_HOC_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_video_24)
          binding.callType.contentDescription = context.getString(R.string.CallLogAdapter__start_a_video_call)
          binding.callType.setOnClickListener { onStartVideoCallClicked(model.call.peer, model.call.canUserBeginCall) }
          binding.groupCallButton.setOnClickListener { onStartVideoCallClicked(model.call.peer, model.call.canUserBeginCall) }

          if (model.call.callLinkPeekInfo?.isActive == true) {
            binding.callType.visible = false
            binding.groupCallButton.visible = true

            binding.groupCallButton.setText(
              if (model.call.callLinkPeekInfo.isJoined && model.isLocalDeviceInCall) {
                R.string.CallLogAdapter__return
              } else {
                R.string.CallLogAdapter__join
              }
            )
          } else {
            binding.callType.visible = true
            binding.groupCallButton.visible = false
          }
        }

        CallTable.Type.GROUP_CALL -> {
          binding.callType.setImageResource(R.drawable.symbol_video_24)
          binding.callType.contentDescription = context.getString(R.string.CallLogAdapter__start_a_video_call)
          binding.callType.setOnClickListener { onStartVideoCallClicked(model.call.peer, model.call.canUserBeginCall) }
          binding.groupCallButton.setOnClickListener { onStartVideoCallClicked(model.call.peer, model.call.canUserBeginCall) }

          when (model.call.groupCallState) {
            CallLogRow.GroupCallState.NONE, CallLogRow.GroupCallState.FULL -> {
              binding.callType.visible = true
              binding.groupCallButton.visible = false
            }

            CallLogRow.GroupCallState.ACTIVE, CallLogRow.GroupCallState.LOCAL_USER_JOINED -> {
              binding.callType.visible = false
              binding.groupCallButton.visible = true

              binding.groupCallButton.setText(
                if (model.call.groupCallState == CallLogRow.GroupCallState.LOCAL_USER_JOINED && model.isLocalDeviceInCall) {
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
        MessageTypes.INCOMING_AUDIO_CALL_TYPE, MessageTypes.INCOMING_VIDEO_CALL_TYPE -> if (call.isDisplayedAsMissedCallInUi) R.drawable.symbol_missed_incoming_compact_16 else R.drawable.symbol_arrow_downleft_compact_16
        MessageTypes.OUTGOING_AUDIO_CALL_TYPE, MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> R.drawable.symbol_arrow_upright_compact_16
        MessageTypes.GROUP_CALL_TYPE -> when {
          call.type == CallTable.Type.AD_HOC_CALL -> R.drawable.symbol_link_compact_16
          call.isDisplayedAsMissedCallInUi -> R.drawable.symbol_missed_incoming_compact_16
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
        MessageTypes.MISSED_VIDEO_CALL_TYPE, MessageTypes.MISSED_AUDIO_CALL_TYPE -> if (call.event == CallTable.Event.MISSED) R.string.CallLogAdapter__missed else R.string.CallLogAdapter__missed_notification_profile
        MessageTypes.OUTGOING_AUDIO_CALL_TYPE -> R.string.CallLogAdapter__outgoing
        MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> R.string.CallLogAdapter__outgoing
        MessageTypes.GROUP_CALL_TYPE -> when {
          call.type == CallTable.Type.AD_HOC_CALL -> R.string.CallLogAdapter__call_link
          call.event == CallTable.Event.MISSED_NOTIFICATION_PROFILE -> R.string.CallLogAdapter__missed_notification_profile
          call.isDisplayedAsMissedCallInUi -> R.string.CallLogAdapter__missed
          call.event == CallTable.Event.GENERIC_GROUP_CALL || call.event == CallTable.Event.JOINED -> R.string.CallPreference__group_call
          call.direction == CallTable.Direction.INCOMING -> R.string.CallLogAdapter__incoming
          call.direction == CallTable.Direction.OUTGOING -> R.string.CallLogAdapter__outgoing
          else -> throw AssertionError()
        }

        else -> if (call.isDisplayedAsMissedCallInUi) R.string.CallLogAdapter__missed else R.string.CallLogAdapter__incoming
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
     * Invoked when a call link row is clicked
     */
    fun onCallLinkClicked(callLogRow: CallLogRow.CallLink)

    /**
     * Invoked when a call row is long-clicked
     */
    fun onCallLongClicked(itemView: View, callLogRow: CallLogRow.Call): Boolean

    /**
     * Invoked when a call link row is long-clicked
     */
    fun onCallLinkLongClicked(itemView: View, callLinkLogRow: CallLogRow.CallLink): Boolean

    /**
     * Invoked when the clear filter button is pressed
     */
    fun onClearFilterClicked()

    /**
     * Invoked when user presses the audio icon
     */
    fun onStartAudioCallClicked(recipient: Recipient)

    /**
     * Invoked when user presses the video icon
     */
    fun onStartVideoCallClicked(recipient: Recipient, canUserBeginCall: Boolean)
  }
}
