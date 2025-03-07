package org.thoughtcrime.securesms.recipients.ui.bottomsheet

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.view.AvatarView
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.view.ViewBadgeBottomSheetDialogFragment
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.conversation.preferences.ButtonStripPreference
import org.thoughtcrime.securesms.conversation.v2.data.AvatarDownloadStateCache
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.nicknames.NicknameActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.recipients.ui.about.AboutSheet
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.visible

/**
 * A bottom sheet that shows some simple recipient details, as well as some actions (like calling,
 * adding to contacts, etc).
 */
class RecipientBottomSheetDialogFragment : BottomSheetDialogFragment() {

  companion object {
    val TAG: String = Log.tag(RecipientBottomSheetDialogFragment::class.java)

    const val REQUEST_CODE_SYSTEM_CONTACT_SHEET: Int = 1111

    private const val ARGS_RECIPIENT_ID = "RECIPIENT_ID"
    private const val ARGS_GROUP_ID = "GROUP_ID"
    private const val LOADING_DELAY = 800L
    private const val FADE_DURATION = 150L

    @JvmStatic
    fun show(fragmentManager: FragmentManager, recipientId: RecipientId, groupId: GroupId?) {
      val recipient = Recipient.resolved(recipientId)
      if (recipient.isSelf) {
        AboutSheet.create(recipient).show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      } else {
        val args = Bundle()
        val fragment = RecipientBottomSheetDialogFragment()

        args.putString(ARGS_RECIPIENT_ID, recipientId.serialize())
        if (groupId != null) {
          args.putString(ARGS_GROUP_ID, groupId.toString())
        }

        fragment.setArguments(args)

        fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }

  private val viewModel: RecipientDialogViewModel by viewModels(factoryProducer = this::createFactory)
  private var callback: Callback? = null

  private fun createFactory(): RecipientDialogViewModel.Factory {
    val arguments: Bundle = requireArguments()
    val recipientId = RecipientId.from(arguments.getString(ARGS_RECIPIENT_ID)!!)
    val groupId: GroupId? = GroupId.parseNullableOrThrow(arguments.getString(ARGS_GROUP_ID))

    return RecipientDialogViewModel.Factory(requireContext(), recipientId, groupId)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    setStyle(
      DialogFragment.STYLE_NORMAL,
      if (ThemeUtil.isDarkTheme(requireContext())) R.style.Theme_Signal_RoundedBottomSheet else R.style.Theme_Signal_RoundedBottomSheet_Light
    )

    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.recipient_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val avatar: AvatarView = view.findViewById(R.id.rbs_recipient_avatar)
    val fullName: TextView = view.findViewById(R.id.rbs_full_name)
    val about: TextView = view.findViewById(R.id.rbs_about)
    val nickname: TextView = view.findViewById(R.id.rbs_nickname_button)
    val blockButton: TextView = view.findViewById(R.id.rbs_block_button)
    val unblockButton: TextView = view.findViewById(R.id.rbs_unblock_button)
    val addContactButton: TextView = view.findViewById(R.id.rbs_add_contact_button)
    val contactDetailsButton: TextView = view.findViewById(R.id.rbs_contact_details_button)
    val addToGroupButton: TextView = view.findViewById(R.id.rbs_add_to_group_button)
    val viewSafetyNumberButton: TextView = view.findViewById(R.id.rbs_view_safety_number_button)
    val makeGroupAdminButton: TextView = view.findViewById(R.id.rbs_make_group_admin_button)
    val removeAdminButton: TextView = view.findViewById(R.id.rbs_remove_group_admin_button)
    val removeFromGroupButton: TextView = view.findViewById(R.id.rbs_remove_from_group_button)
    val adminActionBusy: ProgressBar = view.findViewById(R.id.rbs_admin_action_busy)
    val noteToSelfDescription: View = view.findViewById(R.id.rbs_note_to_self_description)
    val buttonStrip: View = view.findViewById(R.id.button_strip)
    val interactionsContainer: View = view.findViewById(R.id.interactions_container)
    val badgeImageView: BadgeImageView = view.findViewById(R.id.rbs_badge)
    val tapToView: View = view.findViewById(R.id.rbs_tap_to_view)
    val progressBar: ProgressBar = view.findViewById(R.id.rbs_progress_bar)

    val buttonStripViewHolder = ButtonStripPreference.ViewHolder(buttonStrip)

    val nicknameLauncher = registerForActivityResult(NicknameActivity.Contract()) {}

    val arguments = requireArguments()
    val recipientId = RecipientId.from(arguments.getString(ARGS_RECIPIENT_ID)!!)
    val groupId = GroupId.parseNullableOrThrow(arguments.getString(ARGS_GROUP_ID))

    viewModel.storyViewState.observe(viewLifecycleOwner) { state ->
      avatar.setStoryRingFromState(state)
    }

    var inProgress = false

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val recipient = viewModel.recipient.value
        if (recipient != null) {
          AvatarDownloadStateCache.forRecipient(recipient.id).collect {
            when (it) {
              AvatarDownloadStateCache.DownloadState.NONE -> {}
              AvatarDownloadStateCache.DownloadState.IN_PROGRESS -> {
                if (inProgress) {
                  return@collect
                }
                inProgress = true
                animateAvatarLoading(recipient, avatar)
                tapToView.visible = false
                tapToView.setOnClickListener(null)
                delay(LOADING_DELAY)
                progressBar.visible = AvatarDownloadStateCache.getDownloadState(recipient) == AvatarDownloadStateCache.DownloadState.IN_PROGRESS
              }
              AvatarDownloadStateCache.DownloadState.FINISHED -> {
                AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.NONE)
                viewModel.refreshGroupId(groupId)
                inProgress = false
                progressBar.visible = false
              }
              AvatarDownloadStateCache.DownloadState.FAILED -> {
                AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.NONE)
                avatar.displayGradientBlur(recipient)
                viewModel.onResetBlurAvatar(recipient)
                inProgress = false
                progressBar.visible = false
                Snackbar.make(view, R.string.ConversationFragment_photo_failed, Snackbar.LENGTH_LONG).show()
              }
            }
          }
        }
      }
    }

    viewModel.recipient.observe(viewLifecycleOwner) { recipient ->
      interactionsContainer.visible = !recipient.isSelf
      if (AvatarDownloadStateCache.getDownloadState(recipient) != AvatarDownloadStateCache.DownloadState.IN_PROGRESS) {
        avatar.displayChatAvatar(recipient)
      }

      if (!recipient.isSelf) {
        badgeImageView.setBadgeFromRecipient(recipient)
      }

      if (recipient.isSelf) {
        avatar.setOnClickListener {
          dismiss()
          viewModel.onNoteToSelfClicked(requireActivity())
        }
      }

      if (recipient.shouldBlurAvatar && recipient.hasAvatar) {
        tapToView.visible = true
        tapToView.setOnClickListener {
          AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.IN_PROGRESS)
          viewModel.onTapToViewAvatar(recipient)
        }
      } else {
        tapToView.visible = false
        tapToView.setOnClickListener(null)
      }

      val name = if (recipient.isSelf) requireContext().getString(R.string.note_to_self) else recipient.getDisplayName(requireContext())

      fullName.visible = name.isNotEmpty()
      val nameBuilder = SpannableStringBuilder(name)
      if (recipient.showVerified) {
        SpanUtil.appendSpacer(nameBuilder, 8)
        SpanUtil.appendCenteredImageSpanWithoutSpace(nameBuilder, ContextUtil.requireDrawable(requireContext(), R.drawable.ic_official_28), 28, 28)
      } else if (recipient.isSystemContact) {
        val systemContactGlyph = SignalSymbols.getSpannedString(
          requireContext(),
          SignalSymbols.Weight.BOLD,
          SignalSymbols.Glyph.PERSON_CIRCLE
        )

        nameBuilder.append(" ")
        nameBuilder.append(SpanUtil.ofSize(systemContactGlyph, 20))
      }

      if (!recipient.isSelf && recipient.isIndividual) {
        val chevronGlyph = SignalSymbols.getSpannedString(
          requireContext(),
          SignalSymbols.Weight.BOLD,
          SignalSymbols.Glyph.CHEVRON_RIGHT
        )

        nameBuilder.append(" ")
        nameBuilder.append(
          SpanUtil.color(ContextCompat.getColor(requireContext(), R.color.signal_colorOutline), SpanUtil.ofSize(chevronGlyph, 24))
        )

        fullName.text = nameBuilder
        fullName.setOnClickListener {
          dismiss()
          AboutSheet.create(recipient).show(getParentFragmentManager(), null)
        }

        nickname.visible = true
        nickname.setOnClickListener {
          nicknameLauncher.launch(NicknameActivity.Args(recipientId, false))
        }
      } else if (recipient.isReleaseNotes) {
        fullName.text = name
      }

      var aboutText = recipient.combinedAboutAndEmoji
      if (recipient.isReleaseNotes) {
        aboutText = getString(R.string.ReleaseNotes__signal_release_notes_and_news)
      }

      if (!aboutText.isNullOrEmpty()) {
        about.text = aboutText
        about.visible = true
      } else {
        about.visible = false
      }

      noteToSelfDescription.visible = recipient.isSelf

      if (RecipientUtil.isBlockable(recipient)) {
        val blocked = recipient.isBlocked

        blockButton.visible = !recipient.isSelf && !blocked
        unblockButton.visible = !recipient.isSelf && blocked
      } else {
        blockButton.visible = false
        unblockButton.visible = false
      }

      val isAudioAvailable = recipient.isRegistered &&
        !recipient.isGroup &&
        !recipient.isBlocked &&
        !recipient.isSelf &&
        !recipient.isReleaseNotes

      val buttonStripState = ButtonStripPreference.State(
        isMessageAvailable = !recipient.isBlocked && !recipient.isSelf && !recipient.isReleaseNotes,
        isVideoAvailable = !recipient.isBlocked && !recipient.isSelf && recipient.isRegistered,
        isAudioAvailable = isAudioAvailable,
        isAudioSecure = recipient.isRegistered
      )

      val buttonStripModel = ButtonStripPreference.Model(
        state = buttonStripState,
        background = DSLSettingsIcon.from(ContextUtil.requireDrawable(requireContext(), R.drawable.selectable_recipient_bottom_sheet_icon_button)),
        enabled = !viewModel.isDeprecatedOrUnregistered,
        onMessageClick = {
          dismiss()
          viewModel.onMessageClicked(requireActivity())
        },
        onVideoClick = {
          viewModel.onSecureVideoCallClicked(requireActivity()) { YouAreAlreadyInACallSnackbar.show(requireView()) }
        },
        onAudioClick = {
          if (buttonStripState.isAudioSecure) {
            viewModel.onSecureCallClicked(requireActivity()) { YouAreAlreadyInACallSnackbar.show(requireView()) }
          } else {
            viewModel.onInsecureCallClicked(requireActivity())
          }
        }
      )

      buttonStripViewHolder.bind(buttonStripModel)

      if (recipient.isReleaseNotes) {
        buttonStrip.visible = false
      }

      if (recipient.isSystemContact || recipient.isGroup || recipient.isSelf || recipient.isBlocked || recipient.isReleaseNotes || !recipient.hasE164 || !recipient.shouldShowE164) {
        addContactButton.visible = false
      } else {
        addContactButton.visible = true
        addContactButton.setOnClickListener {
          openSystemContactSheet(RecipientExporter.export(recipient).asAddContactIntent())
        }
      }

      if (recipient.isSystemContact && !recipient.isGroup && !recipient.isSelf) {
        contactDetailsButton.visible = true
        contactDetailsButton.setOnClickListener {
          openSystemContactSheet(Intent(Intent.ACTION_VIEW, recipient.contactUri))
        }
      } else {
        contactDetailsButton.visible = false
      }
    }

    viewModel.canAddToAGroup.observe(getViewLifecycleOwner()) { canAdd: Boolean ->
      addToGroupButton.setText(if (groupId == null) R.string.RecipientBottomSheet_add_to_a_group else R.string.RecipientBottomSheet_add_to_another_group)
      addToGroupButton.visible = canAdd
    }

    viewModel.adminActionStatus.observe(viewLifecycleOwner) { adminStatus ->
      makeGroupAdminButton.visible = adminStatus.isCanMakeAdmin
      removeAdminButton.visible = adminStatus.isCanMakeNonAdmin
      removeFromGroupButton.visible = adminStatus.isCanRemove

      if (adminStatus.isCanRemove) {
        removeFromGroupButton.setOnClickListener { viewModel.onRemoveFromGroupClicked(requireActivity(), adminStatus.isLinkActive) { this.dismiss() } }
      }
    }

    viewModel.identity.observe(viewLifecycleOwner) { identityRecord ->
      if (identityRecord != null) {
        viewSafetyNumberButton.visible = true
        viewSafetyNumberButton.setOnClickListener {
          dismiss()
          viewModel.onViewSafetyNumberClicked(requireActivity(), identityRecord)
        }
      }
    }

    avatar.setOnClickListener {
      dismiss()
      viewModel.onAvatarClicked(requireActivity())
    }

    badgeImageView.setOnClickListener {
      dismiss()
      ViewBadgeBottomSheetDialogFragment.show(getParentFragmentManager(), recipientId, null)
    }

    blockButton.setOnClickListener { viewModel.onBlockClicked(requireActivity()) }
    unblockButton.setOnClickListener { viewModel.onUnblockClicked(requireActivity()) }

    makeGroupAdminButton.setOnClickListener { viewModel.onMakeGroupAdminClicked(requireActivity()) }
    removeAdminButton.setOnClickListener { viewModel.onRemoveGroupAdminClicked(requireActivity()) }

    addToGroupButton.setOnClickListener {
      dismiss()
      viewModel.onAddToGroupButton(requireActivity())
    }

    viewModel.adminActionBusy.observe(viewLifecycleOwner) { busy ->
      adminActionBusy.visible = busy

      val userLoggedOut = viewModel.isDeprecatedOrUnregistered
      makeGroupAdminButton.isEnabled = !busy && !userLoggedOut
      removeAdminButton.isEnabled = !busy && !userLoggedOut
      removeFromGroupButton.isEnabled = !busy && !userLoggedOut
    }

    callback = if (parentFragment != null && parentFragment is Callback) parentFragment as Callback else null

    if (viewModel.isDeprecatedOrUnregistered) {
      val viewsToDisable = listOf(blockButton, unblockButton, removeFromGroupButton, makeGroupAdminButton, removeAdminButton, addToGroupButton, viewSafetyNumberButton)
      for (textView in viewsToDisable) {
        textView.isEnabled = false
        textView.alpha = 0.5f
      }
    }
  }

  override fun onResume() {
    super.onResume()
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().window!!)
  }

  private fun openSystemContactSheet(intent: Intent) {
    try {
      startActivityForResult(intent, REQUEST_CODE_SYSTEM_CONTACT_SHEET)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity existed to open the contact.")
      Toast.makeText(requireContext(), R.string.RecipientBottomSheet_unable_to_open_contacts, Toast.LENGTH_LONG).show()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_SYSTEM_CONTACT_SHEET) {
      viewModel.refreshRecipient()
    }
  }

  override fun show(manager: FragmentManager, tag: String?) {
    BottomSheetUtil.show(manager, tag, this)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    if (callback != null) {
      callback!!.onRecipientBottomSheetDismissed()
    }
  }

  private fun animateAvatarLoading(recipient: Recipient, avatar: AvatarView) {
    val animator = ObjectAnimator.ofFloat(avatar, "alpha", 1f, 0f).setDuration(FADE_DURATION)
    animator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationEnd(animation: Animator) {
        if (AvatarDownloadStateCache.getDownloadState(recipient) == AvatarDownloadStateCache.DownloadState.IN_PROGRESS) {
          avatar.displayLoadingAvatar()
        }
        ObjectAnimator.ofFloat(avatar, "alpha", 0f, 1f).setDuration(FADE_DURATION).start()
      }
    })
    animator.start()
  }

  interface Callback {
    fun onRecipientBottomSheetDismissed()
  }
}
