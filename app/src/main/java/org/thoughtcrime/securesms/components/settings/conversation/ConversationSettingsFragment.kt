/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntRect
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.logging.Log
import org.signal.core.util.requireParcelableCompat
import org.signal.donations.InAppPaymentType
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PushContactSelectionActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.view.ViewBadgeBottomSheetDialogFragment
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity
import org.thoughtcrime.securesms.components.settings.conversation.group.GroupSettingsEvent
import org.thoughtcrime.securesms.components.settings.conversation.group.GroupSettingsScreen
import org.thoughtcrime.securesms.components.settings.conversation.group.GroupSettingsViewModel
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsEvent
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsScreen
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsViewModel
import org.thoughtcrime.securesms.components.settings.conversation.individual.NoteToSelfSettingsScreen
import org.thoughtcrime.securesms.components.settings.conversation.individual.ReleaseNotesSettingsScreen
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelEducationSheet
import org.thoughtcrime.securesms.groups.ui.EndGroupDialog
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog
import org.thoughtcrime.securesms.groups.ui.MemberSearchFragment
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersActivity
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsActivity
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupsLearnMoreBottomSheetDialogFragment
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.main.MainNavigationChatDetailRouter
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediapreview.MediaPreviewCache
import org.thoughtcrime.securesms.nicknames.NicknameActivity
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.about.AboutSheet
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.starred.StarredMessagesActivity
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.stories.viewer.AddToGroupStoryDelegate
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity

private val TAG = Log.tag(ConversationSettingsFragment::class)

private const val REQUEST_CODE_VIEW_CONTACT = 1
private const val REQUEST_CODE_ADD_CONTACT = 2
private const val REQUEST_CODE_ADD_MEMBERS_TO_GROUP = 3
private const val REQUEST_CODE_RETURN_FROM_MEDIA = 4

/**
 * Entry point for conversation settings.
 *
 * Hands off to the screen matching the conversation type, and carries out the [ConversationSettingsAction]s that need an
 * Activity, FragmentManager, or the legacy nav graph.
 *
 * Hosts that want shared element enter transitions should implement [TransitionCallback].
 */
class ConversationSettingsFragment : ComposeFragment() {

  private val args: ConversationSettingsFragmentArgs by navArgs()

  private val callMessageIds: LongArray get() = args.callMessageIds ?: longArrayOf()

  private val repository: ConversationSettingsRepository by lazy { ConversationSettingsRepository(requireContext()) }

  // These are lazy, only one gets built
  private val individualViewModel: IndividualSettingsViewModel by viewModels(
    factoryProducer = { IndividualSettingsViewModel.Factory(requireNotNull(args.recipientId), args.kind, callMessageIds, repository) }
  )

  private val groupViewModel: GroupSettingsViewModel by viewModels(
    factoryProducer = { GroupSettingsViewModel.Factory(requireNotNull(args.groupId), callMessageIds, repository) }
  )

  private var transitionCallback: TransitionCallback? = null
  private var chatRouter: MainNavigationChatDetailRouter? = null

  /** The avatar view owns the shared element transition out of this screen. */
  private var avatarView: View? = null

  private lateinit var addToGroupStoryDelegate: AddToGroupStoryDelegate
  private lateinit var nicknameLauncher: ActivityResultLauncher<NicknameActivity.Args>

  private val navController get() = Navigation.findNavController(requireView())

  override fun onAttach(context: Context) {
    super.onAttach(context)
    transitionCallback = context as? TransitionCallback
    chatRouter = context as? MainNavigationChatDetailRouter
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    nicknameLauncher = registerForActivityResult(NicknameActivity.Contract()) {
      // No result to handle -- the nickname is saved to the database, and the recipient observer picks it up
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    addToGroupStoryDelegate = AddToGroupStoryDelegate(this)

    parentFragmentManager.setFragmentResultListener(MemberLabelEducationSheet.RESULT_EDIT_MEMBER_LABEL, viewLifecycleOwner) { _, bundle ->
      val groupId = bundle.requireParcelableCompat(MemberLabelEducationSheet.KEY_GROUP_ID, GroupId.V2::class.java)
      navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToMemberLabelFragment(groupId))
    }

    parentFragmentManager.setFragmentResultListener(AboutSheet.RESULT_EDIT_MEMBER_LABEL, viewLifecycleOwner) { _, bundle ->
      val groupId = bundle.requireParcelableCompat(AboutSheet.RESULT_GROUP_ID, GroupId.V2::class.java)
      navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToMemberLabelFragment(groupId))
    }

    parentFragmentManager.setFragmentResultListener(MemberSearchFragment.RESULT_ADD_MEMBERS, viewLifecycleOwner) { _, _ ->
      groupViewModel.onEvent(GroupSettingsEvent.AddMembersClicked)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    avatarView = null
  }

  /**
   * Runs whichever of these applies to the conversation we're showing. Used to forward the things that happen to the
   * fragment -- activity results, dialog confirmations -- into the live view model's own events.
   */
  private fun dispatch(
    individual: (IndividualSettingsViewModel) -> Unit = {},
    group: (GroupSettingsViewModel) -> Unit = {}
  ) {
    if (args.kind == ConversationSettingsKind.GROUP) {
      group(groupViewModel)
    } else {
      individual(individualViewModel)
    }
  }

  @Composable
  override fun FragmentContent() {
    when (args.kind) {
      ConversationSettingsKind.INDIVIDUAL -> IndividualContent()
      ConversationSettingsKind.NOTE_TO_SELF -> NoteToSelfContent()
      ConversationSettingsKind.RELEASE_NOTES -> ReleaseNotesContent()
      ConversationSettingsKind.GROUP -> GroupContent()
    }
  }

  @Composable
  private fun IndividualContent() {
    val state by individualViewModel.state.collectAsStateWithLifecycle()

    CollectActions(individualViewModel.actions) { action -> handleAction(action) }
    NotifyWhenLoaded(state.isLoaded)

    IndividualSettingsScreen(
      state = state,
      onEvent = individualViewModel::onEvent,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      onAvatarViewCreated = { avatarView = it }
    )
  }

  @Composable
  private fun NoteToSelfContent() {
    val state by individualViewModel.state.collectAsStateWithLifecycle()

    CollectActions(individualViewModel.actions) { action -> handleAction(action) }
    NotifyWhenLoaded(state.isLoaded)

    NoteToSelfSettingsScreen(
      state = state,
      onEvent = individualViewModel::onEvent,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      onAvatarViewCreated = { avatarView = it }
    )
  }

  @Composable
  private fun ReleaseNotesContent() {
    val state by individualViewModel.state.collectAsStateWithLifecycle()

    CollectActions(individualViewModel.actions) { action -> handleAction(action) }
    NotifyWhenLoaded(state.isLoaded)

    ReleaseNotesSettingsScreen(
      state = state,
      onEvent = individualViewModel::onEvent,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      onAvatarViewCreated = { avatarView = it }
    )
  }

  @Composable
  private fun GroupContent() {
    val state by groupViewModel.state.collectAsStateWithLifecycle()

    CollectActions(groupViewModel.actions) { action -> handleAction(action) }
    NotifyWhenLoaded(state.isLoaded)

    GroupSettingsScreen(
      state = state,
      onEvent = groupViewModel::onEvent,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      onAvatarViewCreated = { avatarView = it }
    )
  }

  /**
   * Animates the media viewer up out of the rail item the user tapped. [bounds] arrive in window coordinates, since the
   * rail is Compose and has no view of its own to hand over, so they have to be moved into our root view's space first.
   */
  private fun scaleUpFromRail(bounds: IntRect): ActivityOptions? {
    if (bounds.isEmpty) {
      return null
    }

    val root = view ?: return null
    val rootLocation = IntArray(2).also { root.getLocationInWindow(it) }

    return ActivityOptions.makeScaleUpAnimation(root, bounds.left - rootLocation[0], bounds.top - rootLocation[1], bounds.width, bounds.height)
  }

  @Composable
  private fun NotifyWhenLoaded(isLoaded: Boolean) {
    LaunchedEffect(isLoaded) {
      if (isLoaded) {
        (view?.parent as? ViewGroup)?.doOnPreDraw {
          transitionCallback?.onReadyForEnterTransition()
        }
      }
    }
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_ADD_MEMBERS_TO_GROUP -> {
        if (data != null) {
          val selected: List<RecipientId> = requireNotNull(data.getParcelableArrayListExtraCompat(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS, RecipientId::class.java))
          groupViewModel.onEvent(GroupSettingsEvent.AddMembersSelected(selected))
        }
      }
      REQUEST_CODE_RETURN_FROM_MEDIA -> {
        dispatch(
          individual = { it.onEvent(IndividualSettingsEvent.MediaRailEvent(RecentMediaRailEvents.RefreshRequested)) },
          group = { it.onEvent(GroupSettingsEvent.MediaRailEvent(RecentMediaRailEvents.RefreshRequested)) }
        )
      }
      REQUEST_CODE_ADD_CONTACT, REQUEST_CODE_VIEW_CONTACT -> {
        individualViewModel.onEvent(IndividualSettingsEvent.RecipientRefreshRequested)
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  @Suppress("DEPRECATION")
  private fun handleAction(action: ConversationSettingsAction) {
    Log.d(TAG, "[Action] $action")

    when (action) {
      is ConversationSettingsAction.ShowAvatarPreview -> {
        val intent = AvatarPreviewActivity.intentFromRecipientId(requireContext(), action.recipientId)
        val transitionBundle = avatarView?.let { AvatarPreviewActivity.createTransitionBundle(requireActivity(), it) }
        startActivity(intent, transitionBundle)
      }
      is ConversationSettingsAction.ShowStoryOrAvatarDialog -> {
        val viewAvatarIntent = AvatarPreviewActivity.intentFromRecipientId(requireContext(), action.recipientId)
        val transitionBundle = avatarView?.let { AvatarPreviewActivity.createTransitionBundle(requireActivity(), it) }
        val viewStoryIntent = StoryViewerActivity.createIntent(
          requireContext(),
          StoryViewerArgs(
            recipientId = action.recipientId,
            isInHiddenStoryMode = action.isInHiddenStoryMode,
            isFromQuote = true
          )
        )

        StoryDialogs.displayStoryOrProfileImage(
          context = requireContext(),
          onViewStory = { startActivity(viewStoryIntent) },
          onViewAvatar = { startActivity(viewAvatarIntent, transitionBundle) }
        )
      }
      is ConversationSettingsAction.ShowBadgeSheet -> {
        ViewBadgeBottomSheetDialogFragment.show(parentFragmentManager, action.recipientId, action.badge)
      }
      is ConversationSettingsAction.ShowAboutSheet -> {
        AboutSheet.create(action.recipient).show(parentFragmentManager, null)
      }
      is ConversationSettingsAction.EditGroupProfile -> {
        startActivity(CreateProfileActivity.getIntentForGroupProfile(requireActivity(), action.groupId))
      }
      is ConversationSettingsAction.EditGroupDescription -> {
        startActivity(CreateProfileActivity.getIntentForGroupProfileWithFocusedDescription(requireActivity(), action.groupId))
      }
      is ConversationSettingsAction.ShowGroupDescriptionDialog -> {
        GroupDescriptionDialog.show(childFragmentManager, action.groupId, null, action.shouldLinkify)
      }
      ConversationSettingsAction.ShowGroupsLearnMore -> {
        GroupsLearnMoreBottomSheetDialogFragment.show(parentFragmentManager)
      }
      ConversationSettingsAction.ShowInviteFriends -> {
        startActivity(AppSettingsActivity.invite(requireContext()))
      }
      is ConversationSettingsAction.NavigateToInternalDetails -> {
        navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToInternalDetailsSettingsFragment(action.recipientId))
      }
      is ConversationSettingsAction.OpenConversation -> {
        val builder = ConversationIntents.createBuilderSync(requireContext(), action.recipientId, action.threadId)
        startActivity(builder.withSearchOpen(action.withSearchOpen).build())

        if (action.withSearchOpen && requireActivity() !is MainNavigationChatDetailRouter) {
          requireActivity().finish()
        }
      }
      is ConversationSettingsAction.AddToGroupStory -> {
        addToGroupStoryDelegate.addToStory(action.recipientId)
      }
      is ConversationSettingsAction.StartVideoCall -> {
        CommunicationActions.startVideoCall(requireActivity(), action.recipient) {
          YouAreAlreadyInACallSnackbar.show(requireView())
        }
      }
      is ConversationSettingsAction.StartAudioCall -> {
        CommunicationActions.startVoiceCall(requireActivity(), action.recipient) {
          YouAreAlreadyInACallSnackbar.show(requireView())
        }
      }
      ConversationSettingsAction.ShowMuteUntilTimePicker -> {
        childFragmentManager.setFragmentResultListener(MuteUntilTimePickerBottomSheet.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
          val muteUntil = bundle.getLong(MuteUntilTimePickerBottomSheet.RESULT_TIMESTAMP)
          dispatch(
            individual = { it.onEvent(IndividualSettingsEvent.MuteDurationSelected(muteUntil)) },
            group = { it.onEvent(GroupSettingsEvent.MuteDurationSelected(muteUntil)) }
          )
        }
        MuteUntilTimePickerBottomSheet.show(childFragmentManager)
      }
      is ConversationSettingsAction.NavigateToDisappearingMessages -> {
        navController.safeNavigate(
          ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToAppSettingsExpireTimer()
            .setInitialValue(action.initialValue)
            .setRecipientId(action.recipientId)
            .setForResultMode(false)
        )
      }
      is ConversationSettingsAction.EditNickname -> {
        nicknameLauncher.launch(NicknameActivity.Args(action.recipientId, false))
      }
      is ConversationSettingsAction.OpenChatWallpaper -> {
        startActivity(ChatWallpaperActivity.createIntent(requireContext(), action.recipientId))
      }
      is ConversationSettingsAction.NavigateToSoundsAndNotifications -> {
        val directions = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToSoundsAndNotificationsSettingsFragment(action.recipientId)
        navController.safeNavigate(directions)
      }
      is ConversationSettingsAction.OpenStarredMessages -> {
        startActivity(StarredMessagesActivity.createIntent(requireContext(), action.threadId))
      }
      is ConversationSettingsAction.ViewContact -> {
        startActivityForResult(Intent(Intent.ACTION_VIEW, action.recipient.contactUri), REQUEST_CODE_VIEW_CONTACT)
      }
      is ConversationSettingsAction.AddContact -> {
        try {
          startActivityForResult(RecipientExporter.export(action.recipient).asAddContactIntent(), REQUEST_CODE_ADD_CONTACT)
        } catch (e: ActivityNotFoundException) {
          Toast.makeText(requireContext(), R.string.ConversationSettingsFragment__contacts_app_not_found, Toast.LENGTH_SHORT).show()
        }
      }
      is ConversationSettingsAction.ShowSafetyNumber -> {
        VerifyIdentityActivity.startOrShowExchangeMessagesDialog(requireActivity(), action.identityRecord)
      }
      is ConversationSettingsAction.ShowMediaPreview -> {
        // The rail is Compose and has no drawable to hand over, so make sure the viewer doesn't try to transition out of
        // whatever some other screen left behind.
        MediaPreviewCache.drawable = null

        startActivityForResult(
          MediaIntentFactory.intentFromMediaRecord(requireContext(), action.mediaRecord, action.isLtr, allMediaInRail = true),
          REQUEST_CODE_RETURN_FROM_MEDIA,
          scaleUpFromRail(action.bounds)?.toBundle()
        )
      }
      is ConversationSettingsAction.DownloadMedia -> {
        action.mediaRecord.attachment?.let { AttachmentDownloadJob.downloadAttachmentIfNeeded(it) }
      }
      ConversationSettingsAction.ShowMediaNotSentYet -> {
        Toast.makeText(requireContext(), R.string.ConversationSettingsFragment__this_media_is_not_sent_yet, Toast.LENGTH_LONG).show()
      }
      is ConversationSettingsAction.ShowMediaOverview -> {
        startActivityForResult(MediaOverviewActivity.forThread(requireContext(), action.threadId), REQUEST_CODE_RETURN_FROM_MEDIA)
      }
      ConversationSettingsAction.OpenSupportCenter -> {
        CommunicationActions.openBrowserLink(requireContext(), getString(R.string.support_center_url))
      }
      ConversationSettingsAction.OpenContactUs -> {
        startActivity(AppSettingsActivity.help(requireContext()))
      }
      ConversationSettingsAction.OpenDonate -> {
        startActivity(CheckoutFlowActivity.createIntent(requireContext(), InAppPaymentType.ONE_TIME_DONATION))
      }
      is ConversationSettingsAction.AddToAGroup -> {
        startActivity(AddToGroupsActivity.createIntent(requireContext(), action.recipientId, action.groupMembership))
      }
      is ConversationSettingsAction.OpenGroupConversation -> {
        CommunicationActions.startConversation(requireActivity(), action.recipient, null)
        if (requireActivity() !is MainNavigationChatDetailRouter) {
          requireActivity().finish()
        }
      }
      is ConversationSettingsAction.NavigateToMemberSearch -> {
        navController.safeNavigate(
          ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToMemberSearchFragment(action.groupId, action.canAdd, action.hasGroupLink)
        )
      }
      is ConversationSettingsAction.AddMembersToGroup -> {
        startActivityForResult(AddMembersActivity.createIntent(requireContext(), action), REQUEST_CODE_ADD_MEMBERS_TO_GROUP)
      }
      ConversationSettingsAction.ShowGroupHardLimitDialog -> {
        GroupLimitDialog.showHardLimitMessage(requireContext())
      }
      is ConversationSettingsAction.ShowRecipientBottomSheet -> {
        RecipientBottomSheetDialogFragment.show(parentFragmentManager, action.recipientId, action.groupId)
      }
      is ConversationSettingsAction.NavigateToMemberLabel -> {
        navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToMemberLabelFragment(action.groupId))
      }
      ConversationSettingsAction.ShowMemberLabelPermissionError -> {
        Snackbar.make(requireView(), R.string.GroupMemberLabel__error_no_edit_permission, Snackbar.LENGTH_SHORT).show()
      }
      is ConversationSettingsAction.NavigateToShareableGroupLink -> {
        navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToShareableGroupLinkFragment(action.groupId))
      }
      is ConversationSettingsAction.OpenRequestsAndInvites -> {
        startActivity(ManagePendingAndRequestingMembersActivity.newIntent(requireContext(), action.groupId))
      }
      is ConversationSettingsAction.NavigateToPermissions -> {
        navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToPermissionsSettingsFragment(action.groupId))
      }
      is ConversationSettingsAction.ShowLeaveGroupDialog -> {
        LeaveGroupDialog.handleLeavePushGroup(requireActivity(), action.groupId.requirePush(), null)
      }
      is ConversationSettingsAction.ShowEndGroupDialog -> {
        EndGroupDialog.show(requireActivity(), action.groupId, action.groupTitle)
      }
      is ConversationSettingsAction.ShowBlockDialog -> {
        BlockUnblockDialog.showBlockFor(requireContext(), action.recipient) {
          dispatch(
            individual = { it.onEvent(IndividualSettingsEvent.BlockConfirmed) },
            group = { it.onEvent(GroupSettingsEvent.BlockConfirmed) }
          )
        }
      }
      is ConversationSettingsAction.ShowUnblockDialog -> {
        BlockUnblockDialog.showUnblockFor(requireContext(), action.recipient) {
          dispatch(
            individual = { it.onEvent(IndividualSettingsEvent.UnblockConfirmed) },
            group = { it.onEvent(GroupSettingsEvent.UnblockConfirmed) }
          )
        }
      }
      is ConversationSettingsAction.ShowReportSpamDialog -> {
        BlockUnblockDialog.showReportSpamFor(
          requireContext(),
          action.recipient,
          {
            dispatch(
              individual = { it.onEvent(IndividualSettingsEvent.ReportSpamConfirmed) },
              group = { it.onEvent(GroupSettingsEvent.ReportSpamConfirmed) }
            )
          },
          if (action.canBlock) {
            Runnable {
              dispatch(
                individual = { it.onEvent(IndividualSettingsEvent.BlockAndReportSpamConfirmed) },
                group = { it.onEvent(GroupSettingsEvent.BlockAndReportSpamConfirmed) }
              )
            }
          } else {
            null
          }
        )
      }
      is ConversationSettingsAction.ShowBlockError -> {
        Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(action.failureReason), Toast.LENGTH_LONG).show()
      }
      ConversationSettingsAction.ShowSpamReported -> {
        Toast.makeText(requireContext(), R.string.ConversationFragment_reported_as_spam, Toast.LENGTH_SHORT).show()
      }
      ConversationSettingsAction.ShowSpamReportedAndBlocked -> {
        Toast.makeText(requireContext(), R.string.ConversationFragment_reported_as_spam_and_blocked, Toast.LENGTH_SHORT).show()
      }
      is ConversationSettingsAction.ShowAddMembersError -> {
        Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(action.failureReason), Toast.LENGTH_LONG).show()
      }
      is ConversationSettingsAction.ShowGroupInvitesSentDialog -> {
        if (action.invitesSentTo.isNotEmpty()) {
          GroupInviteSentDialog.show(childFragmentManager, action.invitesSentTo)
        }
      }
      is ConversationSettingsAction.ShowMembersAdded -> {
        val message = resources.getQuantityString(R.plurals.ManageGroupActivity_added, action.membersAddedCount, action.membersAddedCount)
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
      }
      ConversationSettingsAction.GoToConversationList -> {
        goToConversationList()
      }
    }
  }

  private fun goToConversationList() {
    if (chatRouter != null) {
      chatRouter?.exitDetailLocation()
    } else {
      startActivity(MainActivity.clearTopAndOpenDetail(requireContext(), MainNavigationDetailLocation.Empty))
    }
  }

  /**
   * Implemented by hosts that postpone enter transitions (for example, shared element flows).
   *
   * Called when this fragment has loaded enough UI state to safely run the postponed enter
   * transition.
   */
  interface TransitionCallback {
    fun onReadyForEnterTransition()
  }
}
