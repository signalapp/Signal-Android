package org.thoughtcrime.securesms.profiles.manage

import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.SimpleColorFilter
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatars.getForegroundColor
import org.thoughtcrime.securesms.avatar.Avatars.getTextSizeForLength
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.self.none.BecomeASustainerFragment.Companion.show
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.databinding.EditProfileFragmentBinding
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.profiles.manage.EditProfileViewModel.AvatarState
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.util.NameUtil.getAbbreviation
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import java.util.Arrays
import java.util.Optional

/**
 * Fragment for editing your profile after you're already registered.
 */
class EditProfileFragment : LoggingFragment() {

  private var avatarProgress: AlertDialog? = null

  private lateinit var viewModel: EditProfileViewModel
  private lateinit var binding: EditProfileFragmentBinding
  private lateinit var disposables: LifecycleDisposable

  private val DISABLED_ALPHA = 0.4f

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    binding = EditProfileFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables = LifecycleDisposable()
    disposables.bindTo(viewLifecycleOwner)

    UsernameEditFragment.ResultContract().registerForResult(parentFragmentManager, viewLifecycleOwner) {
      Snackbar.make(view, R.string.ManageProfileFragment__username_created, Snackbar.LENGTH_SHORT).show()
    }

    UsernameShareBottomSheet.ResultContract.registerForResult(parentFragmentManager, viewLifecycleOwner) {
      Snackbar.make(view, R.string.ManageProfileFragment__username_copied, Snackbar.LENGTH_SHORT).show()
    }

    initializeViewModel()

    binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }

    binding.manageProfileEditPhoto.setOnClickListener {
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else {
        onEditAvatarClicked()
      }
    }

    binding.manageProfileNameContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageProfileName())
      }
    }

    binding.manageProfileUsernameContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else if (SignalStore.account.username != null) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Signal_MaterialAlertDialog_List)
          .setItems(R.array.username_edit_entries) { _: DialogInterface?, w: Int ->
            when (w) {
              0 -> findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageUsername())
              1 -> displayConfirmUsernameDeletionDialog()
              else -> throw IllegalStateException()
            }
          }
          .show()
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageUsername())
      }
    }

    binding.manageProfileAboutContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageAbout())
      }
    }

    parentFragmentManager.setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, viewLifecycleOwner) { _: String?, bundle: Bundle ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else if (bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
        viewModel.onAvatarSelected(requireContext(), null)
      } else {
        val result = bundle.getParcelable<Media>(AvatarPickerFragment.SELECT_AVATAR_MEDIA)
        viewModel.onAvatarSelected(requireContext(), result)
      }
    }

    val avatarInitials = binding.manageProfileAvatarInitials
    avatarInitials.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      if (avatarInitials.length() > 0) {
        updateInitials(avatarInitials.text.toString())
      }
    }

    binding.manageProfileBadgesContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else if (Recipient.self().badges.isEmpty()) {
        show(parentFragmentManager)
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageProfileFragmentToBadgeManageFragment())
      }
    }

    binding.manageProfileAvatar.setOnClickListener {
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else {
        startActivity(
          AvatarPreviewActivity.intentFromRecipientId(requireContext(), Recipient.self().id),
          AvatarPreviewActivity.createTransitionBundle(requireActivity(), binding.manageProfileAvatar)
        )
      }
    }
  }

  private fun initializeViewModel() {
    viewModel = ViewModelProvider(this, EditProfileViewModel.Factory()).get(EditProfileViewModel::class.java)

    LiveDataUtil
      .distinctUntilChanged(viewModel.avatar) { b1, b2 -> Arrays.equals(b1.avatar, b2.avatar) }
      .map { avatarState -> Optional.ofNullable(avatarState.avatar) }
      .observe(viewLifecycleOwner) { avatarData -> presentAvatarImage(avatarData) }

    viewModel.avatar.observe(viewLifecycleOwner) { presentAvatarPlaceholder(it) }
    viewModel.profileName.observe(viewLifecycleOwner) { presentProfileName(it) }
    viewModel.events.observe(viewLifecycleOwner) { presentEvent(it) }
    viewModel.about.observe(viewLifecycleOwner) { presentAbout(it) }
    viewModel.aboutEmoji.observe(viewLifecycleOwner) { presentAboutEmoji(it) }
    viewModel.badge.observe(viewLifecycleOwner) { presentBadge(it) }
    viewModel.username.observe(viewLifecycleOwner) { presentUsername(it) }
  }

  private fun presentAvatarImage(avatarData: Optional<ByteArray>) {
    if (avatarData.isPresent) {
      Glide.with(this)
        .load(avatarData.get())
        .circleCrop()
        .into(binding.manageProfileAvatar)
    } else {
      Glide.with(this).load(null as Drawable?).into(binding.manageProfileAvatar)
    }

    binding.manageProfileAvatar.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
    binding.manageProfileAvatarInitials.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
    binding.manageProfileEditPhoto.isEnabled = viewModel.isRegisteredAndUpToDate
  }

  private fun presentAvatarPlaceholder(avatarState: AvatarState) {
    if (avatarState.avatar == null) {
      val initials: CharSequence? = getAbbreviation(avatarState.self.getDisplayName(requireContext()))
      val foregroundColor = getForegroundColor(avatarState.self.avatarColor)

      binding.manageProfileAvatarBackground.colorFilter = SimpleColorFilter(avatarState.self.avatarColor.colorInt())
      binding.manageProfileAvatarPlaceholder.colorFilter = SimpleColorFilter(foregroundColor.colorInt)
      binding.manageProfileAvatarInitials.setTextColor(foregroundColor.colorInt)

      if (TextUtils.isEmpty(initials)) {
        binding.manageProfileAvatarPlaceholder.visibility = View.VISIBLE
        binding.manageProfileAvatarInitials.visibility = View.GONE
      } else {
        updateInitials(initials.toString())
        binding.manageProfileAvatarPlaceholder.visibility = View.GONE
        binding.manageProfileAvatarInitials.visibility = View.VISIBLE
      }
    } else {
      binding.manageProfileAvatarPlaceholder.visibility = View.GONE
      binding.manageProfileAvatarInitials.visibility = View.GONE
    }

    if (avatarProgress == null && avatarState.loadingState == EditProfileViewModel.LoadingState.LOADING) {
      avatarProgress = SimpleProgressDialog.show(requireContext())
    } else if (avatarProgress != null && avatarState.loadingState == EditProfileViewModel.LoadingState.LOADED) {
      avatarProgress!!.dismiss()
    }
  }

  private fun updateInitials(initials: String) {
    binding.manageProfileAvatarInitials.setTextSize(
      TypedValue.COMPLEX_UNIT_PX,
      getTextSizeForLength(
        context = requireContext(),
        text = initials,
        maxWidth = binding.manageProfileAvatarInitials.measuredWidth * 0.8f,
        maxSize = binding.manageProfileAvatarInitials.measuredWidth * 0.45f
      )
    )

    binding.manageProfileAvatarInitials.text = initials
  }

  private fun presentProfileName(profileName: ProfileName?) {
    if (profileName == null || profileName.isEmpty) {
      binding.manageProfileName.setText(R.string.ManageProfileFragment_profile_name)
    } else {
      binding.manageProfileName.text = profileName.toString()
    }

    binding.manageProfileName.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileNameIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
  }

  private fun presentUsername(username: String?) {
    if (username.isNullOrEmpty()) {
      binding.manageProfileUsername.setText(R.string.ManageProfileFragment_username)
    } else {
      binding.manageProfileUsername.text = username
    }

    if (SignalStore.account.usernameSyncState == AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED) {
      binding.usernameErrorIndicator.visibility = View.VISIBLE
    } else {
      binding.usernameErrorIndicator.visibility = View.GONE
    }

    if (SignalStore.account.username != null && SignalStore.account.usernameSyncState != AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED) {
      binding.usernameLinkContainer.setOnClickListener {
        findNavController().safeNavigate(EditProfileFragmentDirections.actionManageProfileFragmentToUsernameLinkFragment())
      }

      if (SignalStore.account.usernameSyncState == AccountValues.UsernameSyncState.LINK_CORRUPTED) {
        binding.linkErrorIndicator.visibility = View.VISIBLE
      } else {
        binding.linkErrorIndicator.visibility = View.GONE
      }

      if (SignalStore.tooltips.showProfileSettingsQrCodeTooltop()) {
        binding.usernameLinkTooltip.visibility = View.VISIBLE
        binding.linkTooltipCloseButton.setOnClickListener {
          binding.usernameLinkTooltip.visibility = View.GONE
          SignalStore.tooltips.markProfileSettingsQrCodeTooltipSeen()
        }
      }

      binding.usernameInfoText.setText(R.string.ManageProfileFragment__your_username)
    } else {
      binding.usernameLinkContainer.visibility = View.GONE
      binding.usernameInfoText.setText(R.string.ManageProfileFragment__username_footer_no_username)
    }

    binding.manageProfileUsername.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileUsernameIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
  }

  private fun presentAbout(about: String?) {
    if (about.isNullOrEmpty()) {
      binding.manageProfileAbout.setText(R.string.ManageProfileFragment_about)
    } else {
      binding.manageProfileAbout.text = about
    }

    binding.manageProfileAbout.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileAboutIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
  }

  private fun presentAboutEmoji(aboutEmoji: String?) {
    if (aboutEmoji.isNullOrEmpty()) {
      binding.manageProfileAboutIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.symbol_edit_24, null))
    } else {
      val emoji = EmojiUtil.convertToDrawable(requireContext(), aboutEmoji)
      if (emoji != null) {
        binding.manageProfileAboutIcon.setImageDrawable(emoji)
      } else {
        binding.manageProfileAboutIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.symbol_edit_24, null))
      }
    }
  }

  private fun presentBadge(badge: Optional<Badge>) {
    if (badge.isPresent && badge.get().visible && !badge.get().isExpired()) {
      binding.manageProfileBadge.setBadge(badge.orElse(null))
    } else {
      binding.manageProfileBadge.setBadge(null)
    }

    binding.manageProfileBadges.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileBadge.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
    binding.manageProfileBadgesIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA

    if (!viewModel.isRegisteredAndUpToDate) {
      binding.manageProfileBadge.setOnClickListener { onClickWhenUnregisteredOrDeprecated() }
    }
  }

  private fun presentEvent(event: EditProfileViewModel.Event) {
    when (event) {
      EditProfileViewModel.Event.AVATAR_DISK_FAILURE -> Toast.makeText(requireContext(), R.string.ManageProfileFragment_failed_to_set_avatar, Toast.LENGTH_LONG).show()
      EditProfileViewModel.Event.AVATAR_NETWORK_FAILURE -> Toast.makeText(requireContext(), R.string.EditProfileNameFragment_failed_to_save_due_to_network_issues_try_again_later, Toast.LENGTH_LONG).show()
    }
  }

  private fun onEditAvatarClicked() {
    findNavController(requireView()).safeNavigate(EditProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null))
  }

  private fun displayConfirmUsernameDeletionDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ManageProfileFragment__delete_username_dialog_title)
      .setMessage(requireContext().getString(R.string.ManageProfileFragment__delete_username_dialog_body, SignalStore.account.username))
      .setPositiveButton(R.string.delete) { _, _ -> onUserConfirmedUsernameDeletion() }
      .setNegativeButton(android.R.string.cancel) { d: DialogInterface?, w: Int -> }
      .show()
  }

  private fun onUserConfirmedUsernameDeletion() {
    binding.progressCard.visibility = View.VISIBLE

    disposables += viewModel
      .deleteUsername()
      .subscribe { result: UsernameDeleteResult ->
        binding.progressCard.visibility = View.GONE
        handleUsernameDeletionResult(result)
      }
  }

  private fun handleUsernameDeletionResult(usernameDeleteResult: UsernameDeleteResult) {
    when (usernameDeleteResult) {
      UsernameDeleteResult.SUCCESS -> {
        Snackbar.make(requireView(), R.string.ManageProfileFragment__username_deleted, Snackbar.LENGTH_SHORT).show()
        binding.usernameLinkContainer.visibility = View.GONE
      }

      UsernameDeleteResult.NETWORK_ERROR -> Snackbar.make(requireView(), R.string.ManageProfileFragment__couldnt_delete_username, Snackbar.LENGTH_SHORT).show()
    }
  }

  private fun onClickWhenUnregisteredOrDeprecated() {
    if (viewModel.isDeprecated) {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.EditProfileFragment_deprecated_dialog_title)
        .setMessage(R.string.EditProfileFragment_deprecated_dialog_body)
        .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
        .setPositiveButton(R.string.EditProfileFragment_deprecated_dialog_update_button) { d, _ ->
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
          d.dismiss()
        }
        .show()
    } else {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.EditProfileFragment_unregistered_dialog_title)
        .setMessage(R.string.EditProfileFragment_unregistered_dialog_body)
        .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
        .setPositiveButton(R.string.EditProfileFragment_unregistered_dialog_reregister_button) { d, _ ->
          startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
          d.dismiss()
        }
        .show()
    }
  }
}
