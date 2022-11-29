package org.thoughtcrime.securesms.recipients.ui.bottomsheet;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.view.AvatarView;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.badges.view.ViewBadgeBottomSheetDialogFragment;
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon;
import org.thoughtcrime.securesms.components.settings.conversation.preferences.ButtonStripPreference;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackPhoto80dp;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.Objects;

import kotlin.Unit;

/**
 * A bottom sheet that shows some simple recipient details, as well as some actions (like calling,
 * adding to contacts, etc).
 */
public final class RecipientBottomSheetDialogFragment extends BottomSheetDialogFragment {

  public static final String TAG = Log.tag(RecipientBottomSheetDialogFragment.class);

  public static final int REQUEST_CODE_SYSTEM_CONTACT_SHEET = 1111;

  private static final String ARGS_RECIPIENT_ID = "RECIPIENT_ID";
  private static final String ARGS_GROUP_ID     = "GROUP_ID";

  private RecipientDialogViewModel viewModel;
  private AvatarView               avatar;
  private TextView                 fullName;
  private TextView                 about;
  private TextView                 usernameNumber;
  private TextView                 blockButton;
  private TextView                 unblockButton;
  private TextView                 addContactButton;
  private TextView                 contactDetailsButton;
  private TextView                 addToGroupButton;
  private TextView                 viewSafetyNumberButton;
  private TextView                 makeGroupAdminButton;
  private TextView                 removeAdminButton;
  private TextView                 removeFromGroupButton;
  private ProgressBar              adminActionBusy;
  private View                     noteToSelfDescription;
  private View                     buttonStrip;
  private View                     interactionsContainer;
  private BadgeImageView           badgeImageView;
  private Callback                 callback;

  public static BottomSheetDialogFragment create(@NonNull RecipientId recipientId,
                                                 @Nullable GroupId groupId)
  {
    Bundle                             args     = new Bundle();
    RecipientBottomSheetDialogFragment fragment = new RecipientBottomSheetDialogFragment();

    args.putString(ARGS_RECIPIENT_ID, recipientId.serialize());
    if (groupId != null) {
      args.putString(ARGS_GROUP_ID, groupId.toString());
    }

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL,
             ThemeUtil.isDarkTheme(requireContext()) ? R.style.Theme_Signal_RoundedBottomSheet
                                                     : R.style.Theme_Signal_RoundedBottomSheet_Light);

    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.recipient_bottom_sheet, container, false);

    avatar                 = view.findViewById(R.id.rbs_recipient_avatar);
    fullName               = view.findViewById(R.id.rbs_full_name);
    about                  = view.findViewById(R.id.rbs_about);
    usernameNumber         = view.findViewById(R.id.rbs_username_number);
    blockButton            = view.findViewById(R.id.rbs_block_button);
    unblockButton          = view.findViewById(R.id.rbs_unblock_button);
    addContactButton       = view.findViewById(R.id.rbs_add_contact_button);
    contactDetailsButton   = view.findViewById(R.id.rbs_contact_details_button);
    addToGroupButton       = view.findViewById(R.id.rbs_add_to_group_button);
    viewSafetyNumberButton = view.findViewById(R.id.rbs_view_safety_number_button);
    makeGroupAdminButton   = view.findViewById(R.id.rbs_make_group_admin_button);
    removeAdminButton      = view.findViewById(R.id.rbs_remove_group_admin_button);
    removeFromGroupButton  = view.findViewById(R.id.rbs_remove_from_group_button);
    adminActionBusy        = view.findViewById(R.id.rbs_admin_action_busy);
    noteToSelfDescription  = view.findViewById(R.id.rbs_note_to_self_description);
    buttonStrip            = view.findViewById(R.id.button_strip);
    interactionsContainer  = view.findViewById(R.id.interactions_container);
    badgeImageView         = view.findViewById(R.id.rbs_badge);

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View fragmentView, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(fragmentView, savedInstanceState);

    Bundle      arguments   = requireArguments();
    RecipientId recipientId = RecipientId.from(Objects.requireNonNull(arguments.getString(ARGS_RECIPIENT_ID)));
    GroupId     groupId     = GroupId.parseNullableOrThrow(arguments.getString(ARGS_GROUP_ID));

    RecipientDialogViewModel.Factory factory = new RecipientDialogViewModel.Factory(requireContext().getApplicationContext(), recipientId, groupId);

    viewModel = new ViewModelProvider(this, factory).get(RecipientDialogViewModel.class);

    viewModel.getStoryViewState().observe(getViewLifecycleOwner(), state -> {
      avatar.setStoryRingFromState(state);
    });

    viewModel.getRecipient().observe(getViewLifecycleOwner(), recipient -> {
      interactionsContainer.setVisibility(recipient.isSelf() ? View.GONE : View.VISIBLE);

      avatar.setFallbackPhotoProvider(new Recipient.FallbackPhotoProvider() {
        @Override
        public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
          return new FallbackPhoto80dp(R.drawable.ic_note_80, recipient.getAvatarColor());
        }
      });
      avatar.displayChatAvatar(recipient);

      if (!recipient.isSelf()) {
        badgeImageView.setBadgeFromRecipient(recipient);
      }

      if (recipient.isSelf()) {
        avatar.setOnClickListener(v -> {
          dismiss();
          viewModel.onNoteToSelfClicked(requireActivity());
        });
      }

      String name = recipient.isSelf() ? requireContext().getString(R.string.note_to_self)
                                       : recipient.getDisplayName(requireContext());
      fullName.setVisibility(TextUtils.isEmpty(name) ? View.GONE : View.VISIBLE);
      SpannableStringBuilder nameBuilder = new SpannableStringBuilder(name);
      if (recipient.isSystemContact() && !recipient.isSelf()) {
        Drawable systemContact = DrawableUtil.tint(ContextUtil.requireDrawable(requireContext(), R.drawable.ic_profile_circle_outline_16),
                                                   ContextCompat.getColor(requireContext(), R.color.signal_text_primary));
        SpanUtil.appendCenteredImageSpan(nameBuilder, systemContact, 16, 16);
      } else if (recipient.showVerified()) {
        SpanUtil.appendCenteredImageSpan(nameBuilder, ContextUtil.requireDrawable(requireContext(), R.drawable.ic_official_28), 28, 28);
      }
      fullName.setText(nameBuilder);

      String aboutText = recipient.getCombinedAboutAndEmoji();
      if (recipient.isReleaseNotes()) {
        aboutText = getString(R.string.ReleaseNotes__signal_release_notes_and_news);
      }

      if (!Util.isEmpty(aboutText)) {
        about.setText(aboutText);
        about.setVisibility(View.VISIBLE);
      } else {
        about.setVisibility(View.GONE);
      }

      String usernameNumberString = recipient.hasAUserSetDisplayName(requireContext()) && !recipient.isSelf()
                                    ? recipient.getSmsAddress().map(PhoneNumberFormatter::prettyPrint).orElse("").trim()
                                    : "";
      usernameNumber.setText(usernameNumberString);
      usernameNumber.setVisibility(TextUtils.isEmpty(usernameNumberString) ? View.GONE : View.VISIBLE);
      usernameNumber.setOnLongClickListener(v -> {
        Util.copyToClipboard(v.getContext(), usernameNumber.getText().toString());
        ServiceUtil.getVibrator(v.getContext()).vibrate(250);
        Toast.makeText(v.getContext(), R.string.RecipientBottomSheet_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        return true;
      });

      noteToSelfDescription.setVisibility(recipient.isSelf() ? View.VISIBLE : View.GONE);

      if (RecipientUtil.isBlockable(recipient)) {
        boolean blocked = recipient.isBlocked();

        blockButton  .setVisibility(recipient.isSelf() ||  blocked ? View.GONE : View.VISIBLE);
        unblockButton.setVisibility(recipient.isSelf() || !blocked ? View.GONE : View.VISIBLE);
      } else {
        blockButton  .setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);
      }

      boolean isAudioAvailable = (recipient.isRegistered() || SignalStore.misc().getSmsExportPhase().allowSmsFeatures()) &&
                                 !recipient.isGroup() &&
                                 !recipient.isBlocked() &&
                                 !recipient.isSelf() &&
                                 !recipient.isReleaseNotes();

      ButtonStripPreference.State  buttonStripState = new ButtonStripPreference.State(
          /* isMessageAvailable     = */ !recipient.isBlocked() && !recipient.isSelf() && !recipient.isReleaseNotes(),
          /* isVideoAvailable       = */ !recipient.isBlocked() && !recipient.isSelf() && recipient.isRegistered(),
          /* isAudioAvailable       = */ isAudioAvailable,
          /* isMuteAvailable        = */ false,
          /* isSearchAvailable      = */ false,
          /* isAudioSecure          = */ recipient.isRegistered(),
          /* isMuted                = */ false,
          /* isAddToStoryAvailable  = */ false
      );

      ButtonStripPreference.Model buttonStripModel = new ButtonStripPreference.Model(
          buttonStripState,
          DSLSettingsIcon.from(ContextUtil.requireDrawable(requireContext(), R.drawable.selectable_recipient_bottom_sheet_icon_button)),
          () -> Unit.INSTANCE,
          () -> {
            dismiss();
            viewModel.onMessageClicked(requireActivity());
            return Unit.INSTANCE;
          },
          () -> {
            viewModel.onSecureVideoCallClicked(requireActivity());
            return Unit.INSTANCE;
          },
          () -> {
            if (buttonStripState.isAudioSecure()) {
              viewModel.onSecureCallClicked(requireActivity());
            } else {
              viewModel.onInsecureCallClicked(requireActivity());
            }
            return Unit.INSTANCE;
          },
          () -> Unit.INSTANCE,
          () -> Unit.INSTANCE
      );

      new ButtonStripPreference.ViewHolder(buttonStrip).bind(buttonStripModel);

      if (recipient.isReleaseNotes()) {
        buttonStrip.setVisibility(View.GONE);
      }

      if (recipient.isSystemContact() || recipient.isGroup() || recipient.isSelf() || recipient.isBlocked() || recipient.isReleaseNotes()) {
        addContactButton.setVisibility(View.GONE);
      } else {
        addContactButton.setVisibility(View.VISIBLE);
        addContactButton.setOnClickListener(v -> {
          openSystemContactSheet(RecipientExporter.export(recipient).asAddContactIntent());
        });
      }

      if (recipient.isSystemContact() && !recipient.isGroup() && !recipient.isSelf()) {
        contactDetailsButton.setVisibility(View.VISIBLE);
        contactDetailsButton.setOnClickListener(v -> {
          openSystemContactSheet(new Intent(Intent.ACTION_VIEW, recipient.getContactUri()));
        });
      } else {
        contactDetailsButton.setVisibility(View.GONE);
      }
    });

    viewModel.getCanAddToAGroup().observe(getViewLifecycleOwner(), canAdd -> {
      addToGroupButton.setText(groupId == null ? R.string.RecipientBottomSheet_add_to_a_group : R.string.RecipientBottomSheet_add_to_another_group);
      addToGroupButton.setVisibility(canAdd ? View.VISIBLE : View.GONE);
    });

    viewModel.getAdminActionStatus().observe(getViewLifecycleOwner(), adminStatus -> {
      makeGroupAdminButton.setVisibility(adminStatus.isCanMakeAdmin() ? View.VISIBLE : View.GONE);
      removeAdminButton.setVisibility(adminStatus.isCanMakeNonAdmin() ? View.VISIBLE : View.GONE);
      removeFromGroupButton.setVisibility(adminStatus.isCanRemove() ? View.VISIBLE : View.GONE);

      if (adminStatus.isCanRemove()) {
        removeFromGroupButton.setOnClickListener(view -> viewModel.onRemoveFromGroupClicked(requireActivity(), adminStatus.isLinkActive(), this::dismiss));
      }
    });

    viewModel.getIdentity().observe(getViewLifecycleOwner(), identityRecord -> {
      viewSafetyNumberButton.setVisibility(identityRecord != null ? View.VISIBLE : View.GONE);

      if (identityRecord != null) {
        viewSafetyNumberButton.setOnClickListener(view -> {
          dismiss();
          viewModel.onViewSafetyNumberClicked(requireActivity(), identityRecord);
        });
      }
    });

    avatar.setOnClickListener(view -> {
      dismiss();
      viewModel.onAvatarClicked(requireActivity());
    });

    badgeImageView.setOnClickListener(view -> {
      dismiss();
      ViewBadgeBottomSheetDialogFragment.show(getParentFragmentManager(), recipientId, null);
    });

    blockButton.setOnClickListener(view -> viewModel.onBlockClicked(requireActivity()));
    unblockButton.setOnClickListener(view -> viewModel.onUnblockClicked(requireActivity()));

    makeGroupAdminButton.setOnClickListener(view -> viewModel.onMakeGroupAdminClicked(requireActivity()));
    removeAdminButton.setOnClickListener(view -> viewModel.onRemoveGroupAdminClicked(requireActivity()));

    addToGroupButton.setOnClickListener(view -> {
      dismiss();
      viewModel.onAddToGroupButton(requireActivity());
    });

    viewModel.getAdminActionBusy().observe(getViewLifecycleOwner(), busy -> {
      adminActionBusy.setVisibility(busy ? View.VISIBLE : View.GONE);

      makeGroupAdminButton.setEnabled(!busy);
      removeAdminButton.setEnabled(!busy);
      removeFromGroupButton.setEnabled(!busy);
    });

    callback = getParentFragment() != null && getParentFragment() instanceof Callback ? (Callback) getParentFragment() : null;
  }

  private void openSystemContactSheet(@NonNull Intent intent) {
    try {
      startActivityForResult(intent, REQUEST_CODE_SYSTEM_CONTACT_SHEET);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "No activity existed to open the contact.");
      Toast.makeText(requireContext(), R.string.RecipientBottomSheet_unable_to_open_contacts, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_SYSTEM_CONTACT_SHEET) {
      viewModel.refreshRecipient();
    }
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    if (callback != null) {
      callback.onRecipientBottomSheetDismissed();
    }
  }

  public interface Callback {
    void onRecipientBottomSheetDismissed();
  }
}
