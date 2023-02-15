package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.annimon.stream.OptionalLong
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls.FoldableState
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipant.Companion.createLocal
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.thoughtcrime.securesms.service.webrtc.collections.ParticipantCollection
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState
import java.util.concurrent.TimeUnit

/**
 * Represents the state of all participants, remote and local, combined with view state
 * needed to properly render the participants. The view state primarily consists of
 * if we are in System PIP mode and if we should show our video for an outgoing call.
 */
data class CallParticipantsState(
  val callState: WebRtcViewModel.State = WebRtcViewModel.State.CALL_DISCONNECTED,
  val groupCallState: WebRtcViewModel.GroupCallState = WebRtcViewModel.GroupCallState.IDLE,
  private val remoteParticipants: ParticipantCollection = ParticipantCollection(SMALL_GROUP_MAX),
  val localParticipant: CallParticipant = createLocal(CameraState.UNKNOWN, BroadcastVideoSink(), false),
  val focusedParticipant: CallParticipant = CallParticipant.EMPTY,
  val localRenderState: WebRtcLocalRenderState = WebRtcLocalRenderState.GONE,
  val isInPipMode: Boolean = false,
  private val showVideoForOutgoing: Boolean = false,
  val isViewingFocusedParticipant: Boolean = false,
  val remoteDevicesCount: OptionalLong = OptionalLong.empty(),
  private val foldableState: FoldableState = FoldableState.flat(),
  val isInOutgoingRingingMode: Boolean = false,
  val ringGroup: Boolean = false,
  val ringerRecipient: Recipient = Recipient.UNKNOWN,
  val groupMembers: List<GroupMemberEntry.FullMember> = emptyList()
) {

  val allRemoteParticipants: List<CallParticipant> = remoteParticipants.allParticipants
  val isFolded: Boolean = foldableState.isFolded
  val isLargeVideoGroup: Boolean = allRemoteParticipants.size > SMALL_GROUP_MAX
  val isIncomingRing: Boolean = callState == WebRtcViewModel.State.CALL_INCOMING

  val gridParticipants: List<CallParticipant>
    get() {
      return remoteParticipants.gridParticipants
    }

  val listParticipants: List<CallParticipant>
    get() {
      val listParticipants: MutableList<CallParticipant> = mutableListOf()
      if (isViewingFocusedParticipant && allRemoteParticipants.size > 1) {
        listParticipants.addAll(allRemoteParticipants)
        listParticipants.remove(focusedParticipant)
      } else {
        listParticipants.addAll(remoteParticipants.listParticipants)
      }
      if (foldableState.isFlat) {
        listParticipants.add(CallParticipant.EMPTY)
      }
      listParticipants.reverse()
      return listParticipants
    }

  val participantCount: OptionalLong
    get() {
      val includeSelf = groupCallState == WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED
      return remoteDevicesCount.map { l: Long -> l + if (includeSelf) 1L else 0L }
        .or { if (includeSelf) OptionalLong.of(1L) else OptionalLong.empty() }
    }

  fun getPreJoinGroupDescription(context: Context): String? {
    if (callState != WebRtcViewModel.State.CALL_PRE_JOIN || groupCallState.isIdle) {
      return null
    }

    return if (remoteParticipants.isEmpty) {
      describeGroupMembers(
        context = context,
        oneParticipant = if (ringGroup) R.string.WebRtcCallView__signal_will_ring_s else R.string.WebRtcCallView__s_will_be_notified,
        twoParticipants = if (ringGroup) R.string.WebRtcCallView__signal_will_ring_s_and_s else R.string.WebRtcCallView__s_and_s_will_be_notified,
        multipleParticipants = if (ringGroup) R.plurals.WebRtcCallView__signal_will_ring_s_s_and_d_others else R.plurals.WebRtcCallView__s_s_and_d_others_will_be_notified,
        members = groupMembers
      )
    } else {
      when (remoteParticipants.size()) {
        0 -> context.getString(R.string.WebRtcCallView__no_one_else_is_here)
        1 -> context.getString(if (remoteParticipants[0].isSelf) R.string.WebRtcCallView__s_are_in_this_call else R.string.WebRtcCallView__s_is_in_this_call, remoteParticipants[0].getShortRecipientDisplayName(context))
        2 -> context.getString(
          R.string.WebRtcCallView__s_and_s_are_in_this_call,
          remoteParticipants[0].getShortRecipientDisplayName(context),
          remoteParticipants[1].getShortRecipientDisplayName(context)
        )
        else -> {
          val others = remoteParticipants.size() - 2
          context.resources.getQuantityString(
            R.plurals.WebRtcCallView__s_s_and_d_others_are_in_this_call,
            others,
            remoteParticipants[0].getShortRecipientDisplayName(context),
            remoteParticipants[1].getShortRecipientDisplayName(context),
            others
          )
        }
      }
    }
  }

  fun getOutgoingRingingGroupDescription(context: Context): String? {
    if (callState == WebRtcViewModel.State.CALL_CONNECTED &&
      groupCallState == WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED &&
      isInOutgoingRingingMode
    ) {
      return describeGroupMembers(
        context = context,
        oneParticipant = R.string.WebRtcCallView__ringing_s,
        twoParticipants = R.string.WebRtcCallView__ringing_s_and_s,
        multipleParticipants = R.plurals.WebRtcCallView__ringing_s_s_and_d_others,
        members = groupMembers
      )
    }

    return null
  }

  fun getIncomingRingingGroupDescription(context: Context): String? {
    if (callState == WebRtcViewModel.State.CALL_INCOMING &&
      groupCallState == WebRtcViewModel.GroupCallState.RINGING &&
      ringerRecipient.hasServiceId()
    ) {
      val ringerName = ringerRecipient.getShortDisplayName(context)
      val membersWithoutYouOrRinger: List<GroupMemberEntry.FullMember> = groupMembers.filterNot { it.member.isSelf || ringerRecipient.requireServiceId() == it.member.serviceId.orElse(null) }

      return when (membersWithoutYouOrRinger.size) {
        0 -> context.getString(R.string.WebRtcCallView__s_is_calling_you, ringerName)
        1 -> context.getString(
          R.string.WebRtcCallView__s_is_calling_you_and_s,
          ringerName,
          membersWithoutYouOrRinger[0].member.getShortDisplayName(context)
        )
        2 -> context.getString(
          R.string.WebRtcCallView__s_is_calling_you_s_and_s,
          ringerName,
          membersWithoutYouOrRinger[0].member.getShortDisplayName(context),
          membersWithoutYouOrRinger[1].member.getShortDisplayName(context)
        )
        else -> {
          val others = membersWithoutYouOrRinger.size - 2
          context.resources.getQuantityString(
            R.plurals.WebRtcCallView__s_is_calling_you_s_s_and_d_others,
            others,
            ringerName,
            membersWithoutYouOrRinger[0].member.getShortDisplayName(context),
            membersWithoutYouOrRinger[1].member.getShortDisplayName(context),
            others
          )
        }
      }
    }

    return null
  }

  fun needsNewRequestSizes(): Boolean {
    return if (groupCallState.isNotIdle) {
      allRemoteParticipants.any { it.videoSink.needsNewRequestingSize() }
    } else {
      false
    }
  }

  companion object {
    private const val SMALL_GROUP_MAX = 6

    @JvmField
    val MAX_OUTGOING_GROUP_RING_DURATION = TimeUnit.MINUTES.toMillis(1)

    @JvmField
    val STARTING_STATE = CallParticipantsState()

    @JvmStatic
    fun update(
      oldState: CallParticipantsState,
      webRtcViewModel: WebRtcViewModel,
      enableVideo: Boolean
    ): CallParticipantsState {
      var newShowVideoForOutgoing: Boolean = oldState.showVideoForOutgoing
      if (enableVideo) {
        newShowVideoForOutgoing = webRtcViewModel.state == WebRtcViewModel.State.CALL_OUTGOING
      } else if (webRtcViewModel.state != WebRtcViewModel.State.CALL_OUTGOING) {
        newShowVideoForOutgoing = false
      }

      val isInOutgoingRingingMode = if (oldState.isInOutgoingRingingMode) {
        webRtcViewModel.callConnectedTime + MAX_OUTGOING_GROUP_RING_DURATION > System.currentTimeMillis() && webRtcViewModel.remoteParticipants.size == 0
      } else {
        oldState.ringGroup &&
          webRtcViewModel.callConnectedTime + MAX_OUTGOING_GROUP_RING_DURATION > System.currentTimeMillis() &&
          webRtcViewModel.remoteParticipants.size == 0 &&
          oldState.callState == WebRtcViewModel.State.CALL_OUTGOING &&
          webRtcViewModel.state == WebRtcViewModel.State.CALL_CONNECTED
      }

      val localRenderState: WebRtcLocalRenderState = determineLocalRenderMode(
        oldState = oldState,
        localParticipant = webRtcViewModel.localParticipant,
        showVideoForOutgoing = newShowVideoForOutgoing,
        isNonIdleGroupCall = webRtcViewModel.groupState.isNotIdle,
        callState = webRtcViewModel.state,
        numberOfRemoteParticipants = webRtcViewModel.remoteParticipants.size
      )

      return oldState.copy(
        callState = webRtcViewModel.state,
        groupCallState = webRtcViewModel.groupState,
        remoteParticipants = oldState.remoteParticipants.getNext(webRtcViewModel.remoteParticipants),
        localParticipant = webRtcViewModel.localParticipant,
        focusedParticipant = getFocusedParticipant(webRtcViewModel.remoteParticipants),
        localRenderState = localRenderState,
        showVideoForOutgoing = newShowVideoForOutgoing,
        remoteDevicesCount = webRtcViewModel.remoteDevicesCount,
        ringGroup = webRtcViewModel.ringGroup,
        isInOutgoingRingingMode = isInOutgoingRingingMode,
        ringerRecipient = webRtcViewModel.ringerRecipient
      )
    }

    @JvmStatic
    fun update(oldState: CallParticipantsState, isInPip: Boolean): CallParticipantsState {
      val localRenderState: WebRtcLocalRenderState = determineLocalRenderMode(oldState = oldState, isInPip = isInPip)

      return oldState.copy(localRenderState = localRenderState, isInPipMode = isInPip)
    }

    @JvmStatic
    fun setExpanded(oldState: CallParticipantsState, expanded: Boolean): CallParticipantsState {
      val localRenderState: WebRtcLocalRenderState = determineLocalRenderMode(oldState = oldState, isExpanded = expanded)

      return oldState.copy(localRenderState = localRenderState)
    }

    @JvmStatic
    fun update(oldState: CallParticipantsState, selectedPage: SelectedPage): CallParticipantsState {
      val localRenderState: WebRtcLocalRenderState = determineLocalRenderMode(oldState = oldState, isViewingFocusedParticipant = selectedPage == SelectedPage.FOCUSED)

      return oldState.copy(localRenderState = localRenderState, isViewingFocusedParticipant = selectedPage == SelectedPage.FOCUSED)
    }

    @JvmStatic
    fun update(oldState: CallParticipantsState, foldableState: FoldableState): CallParticipantsState {
      val localRenderState: WebRtcLocalRenderState = determineLocalRenderMode(oldState = oldState)

      return oldState.copy(localRenderState = localRenderState, foldableState = foldableState)
    }

    @JvmStatic
    fun update(oldState: CallParticipantsState, groupMembers: List<GroupMemberEntry.FullMember>): CallParticipantsState {
      return oldState.copy(groupMembers = groupMembers)
    }

    @JvmStatic
    fun update(oldState: CallParticipantsState, ephemeralState: WebRtcEphemeralState): CallParticipantsState {
      return oldState.copy(
        remoteParticipants = oldState.remoteParticipants.map { p -> p.copy(audioLevel = ephemeralState.remoteAudioLevels[p.callParticipantId]) },
        localParticipant = oldState.localParticipant.copy(audioLevel = ephemeralState.localAudioLevel),
        focusedParticipant = oldState.focusedParticipant.copy(audioLevel = ephemeralState.remoteAudioLevels[oldState.focusedParticipant.callParticipantId])
      )
    }

    private fun determineLocalRenderMode(
      oldState: CallParticipantsState,
      localParticipant: CallParticipant = oldState.localParticipant,
      isInPip: Boolean = oldState.isInPipMode,
      showVideoForOutgoing: Boolean = oldState.showVideoForOutgoing,
      isNonIdleGroupCall: Boolean = oldState.groupCallState.isNotIdle,
      callState: WebRtcViewModel.State = oldState.callState,
      numberOfRemoteParticipants: Int = oldState.allRemoteParticipants.size,
      isViewingFocusedParticipant: Boolean = oldState.isViewingFocusedParticipant,
      isExpanded: Boolean = oldState.localRenderState == WebRtcLocalRenderState.EXPANDED
    ): WebRtcLocalRenderState {
      val displayLocal: Boolean = (numberOfRemoteParticipants == 0 || !isInPip) && (isNonIdleGroupCall || localParticipant.isVideoEnabled)
      var localRenderState: WebRtcLocalRenderState = WebRtcLocalRenderState.GONE

      if (!isInPip && isExpanded && (localParticipant.isVideoEnabled || isNonIdleGroupCall)) {
        return WebRtcLocalRenderState.EXPANDED
      } else if (displayLocal || showVideoForOutgoing) {
        if (callState == WebRtcViewModel.State.CALL_CONNECTED || callState == WebRtcViewModel.State.CALL_RECONNECTING) {
          localRenderState = if (isViewingFocusedParticipant || numberOfRemoteParticipants > 1) {
            WebRtcLocalRenderState.SMALLER_RECTANGLE
          } else if (numberOfRemoteParticipants == 1) {
            WebRtcLocalRenderState.SMALL_RECTANGLE
          } else {
            if (localParticipant.isVideoEnabled) WebRtcLocalRenderState.LARGE else WebRtcLocalRenderState.LARGE_NO_VIDEO
          }
        } else if (callState != WebRtcViewModel.State.CALL_INCOMING && callState != WebRtcViewModel.State.CALL_DISCONNECTED) {
          localRenderState = if (localParticipant.isVideoEnabled) WebRtcLocalRenderState.LARGE else WebRtcLocalRenderState.LARGE_NO_VIDEO
        }
      } else if (callState == WebRtcViewModel.State.CALL_PRE_JOIN) {
        localRenderState = WebRtcLocalRenderState.LARGE_NO_VIDEO
      }
      return localRenderState
    }

    private fun getFocusedParticipant(participants: List<CallParticipant>): CallParticipant {
      val participantsByLastSpoke: List<CallParticipant> = participants.sortedByDescending(CallParticipant::lastSpoke)

      return if (participantsByLastSpoke.isEmpty()) {
        CallParticipant.EMPTY
      } else {
        participantsByLastSpoke.firstOrNull(CallParticipant::isScreenSharing) ?: participantsByLastSpoke[0]
      }
    }

    private fun describeGroupMembers(
      context: Context,
      @StringRes oneParticipant: Int,
      @StringRes twoParticipants: Int,
      @PluralsRes multipleParticipants: Int,
      members: List<GroupMemberEntry.FullMember>
    ): String {
      val eligibleMembers: List<GroupMemberEntry.FullMember> = members.filterNot { it.member.isSelf || it.member.isBlocked }

      return when (eligibleMembers.size) {
        0 -> ""
        1 -> context.getString(
          oneParticipant,
          eligibleMembers[0].member.getShortDisplayName(context)
        )
        2 -> context.getString(
          twoParticipants,
          eligibleMembers[0].member.getShortDisplayName(context),
          eligibleMembers[1].member.getShortDisplayName(context)
        )
        else -> {
          val others = eligibleMembers.size - 2
          context.resources.getQuantityString(
            multipleParticipants,
            others,
            eligibleMembers[0].member.getShortDisplayName(context),
            eligibleMembers[1].member.getShortDisplayName(context),
            others
          )
        }
      }
    }
  }

  enum class SelectedPage {
    GRID, FOCUSED
  }
}
