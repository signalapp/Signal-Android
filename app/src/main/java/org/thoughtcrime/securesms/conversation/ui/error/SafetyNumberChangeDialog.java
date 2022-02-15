package org.thoughtcrime.securesms.conversation.ui.error;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Collection;
import java.util.List;

public final class SafetyNumberChangeDialog extends DialogFragment implements SafetyNumberChangeAdapter.Callbacks {

  private static final String TAG = Log.tag(SafetyNumberChangeDialog.class);

  public static final String SAFETY_NUMBER_DIALOG = "SAFETY_NUMBER";

  private static final String RECIPIENT_IDS_EXTRA          = "recipient_ids";
  private static final String MESSAGE_ID_EXTRA             = "message_id";
  private static final String MESSAGE_TYPE_EXTRA           = "message_type";
  private static final String CONTINUE_TEXT_RESOURCE_EXTRA = "continue_text_resource";
  private static final String CANCEL_TEXT_RESOURCE_EXTRA   = "cancel_text_resource";
  private static final String SKIP_CALLBACKS_EXTRA         = "skip_callbacks_extra";

  private SafetyNumberChangeViewModel viewModel;
  private SafetyNumberChangeAdapter   adapter;
  private View                        dialogView;

  public static void show(@NonNull FragmentManager fragmentManager, @NonNull RecipientId recipientId) {
    Bundle arguments = new Bundle();
    arguments.putStringArray(RECIPIENT_IDS_EXTRA, new String[] { recipientId.serialize() });
    arguments.putInt(CONTINUE_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__accept);
    arguments.putBoolean(SKIP_CALLBACKS_EXTRA, true);
    SafetyNumberChangeDialog fragment = new SafetyNumberChangeDialog();
    fragment.setArguments(arguments);
    fragment.show(fragmentManager, SAFETY_NUMBER_DIALOG);
  }

  public static void show(@NonNull FragmentManager fragmentManager, @NonNull List<IdentityRecord> identityRecords) {
    List<String> ids = Stream.of(identityRecords)
                             .filterNot(IdentityRecord::isFirstUse)
                             .map(record -> record.getRecipientId().serialize())
                             .distinct()
                             .toList();

    Bundle arguments = new Bundle();
    arguments.putStringArray(RECIPIENT_IDS_EXTRA, ids.toArray(new String[0]));
    arguments.putInt(CONTINUE_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__send_anyway);
    SafetyNumberChangeDialog fragment = new SafetyNumberChangeDialog();
    fragment.setArguments(arguments);
    fragment.show(fragmentManager, SAFETY_NUMBER_DIALOG);
  }

  public static void show(@NonNull FragmentActivity fragmentActivity, @NonNull MessageRecord messageRecord) {
    List<String> ids = Stream.of(messageRecord.getIdentityKeyMismatches())
                             .map(mismatch -> mismatch.getRecipientId(fragmentActivity).serialize())
                             .distinct()
                             .toList();

    Bundle arguments = new Bundle();
    arguments.putStringArray(RECIPIENT_IDS_EXTRA, ids.toArray(new String[0]));
    arguments.putLong(MESSAGE_ID_EXTRA, messageRecord.getId());
    arguments.putString(MESSAGE_TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    arguments.putInt(CONTINUE_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__send_anyway);
    SafetyNumberChangeDialog fragment = new SafetyNumberChangeDialog();
    fragment.setArguments(arguments);
    fragment.show(fragmentActivity.getSupportFragmentManager(), SAFETY_NUMBER_DIALOG);
  }

  public static void showForCall(@NonNull FragmentManager fragmentManager, @NonNull RecipientId recipientId) {
    Bundle arguments = new Bundle();
    arguments.putStringArray(RECIPIENT_IDS_EXTRA, new String[] { recipientId.serialize() });
    arguments.putInt(CONTINUE_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__call_anyway);
    SafetyNumberChangeDialog fragment = new SafetyNumberChangeDialog();
    fragment.setArguments(arguments);
    fragment.show(fragmentManager, SAFETY_NUMBER_DIALOG);
  }

  public static void showForGroupCall(@NonNull FragmentManager fragmentManager, @NonNull List<IdentityRecord> identityRecords) {
    List<String> ids = Stream.of(identityRecords)
                             .filterNot(IdentityRecord::isFirstUse)
                             .map(record -> record.getRecipientId().serialize())
                             .distinct()
                             .toList();

    Bundle arguments = new Bundle();
    arguments.putStringArray(RECIPIENT_IDS_EXTRA, ids.toArray(new String[0]));
    arguments.putInt(CONTINUE_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__join_call);
    SafetyNumberChangeDialog fragment = new SafetyNumberChangeDialog();
    fragment.setArguments(arguments);
    fragment.show(fragmentManager, SAFETY_NUMBER_DIALOG);
  }

  public static void showForDuringGroupCall(@NonNull FragmentManager fragmentManager, @NonNull Collection<RecipientId> recipientIds) {
    Fragment previous = fragmentManager.findFragmentByTag(SAFETY_NUMBER_DIALOG);
    if (previous != null) {
      ((SafetyNumberChangeDialog) previous).updateRecipients(recipientIds);
      return;
    }

    List<String> ids = Stream.of(recipientIds)
                             .map(RecipientId::serialize)
                             .distinct()
                             .toList();

    Bundle arguments = new Bundle();
    arguments.putStringArray(RECIPIENT_IDS_EXTRA, ids.toArray(new String[0]));
    arguments.putInt(CONTINUE_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__continue_call);
    arguments.putInt(CANCEL_TEXT_RESOURCE_EXTRA, R.string.safety_number_change_dialog__leave_call);
    SafetyNumberChangeDialog fragment = new SafetyNumberChangeDialog();
    fragment.setArguments(arguments);
    fragment.show(fragmentManager, SAFETY_NUMBER_DIALOG);
  }

  private SafetyNumberChangeDialog() { }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return dialogView;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    //noinspection ConstantConditions
    List<RecipientId> recipientIds = Stream.of(getArguments().getStringArray(RECIPIENT_IDS_EXTRA)).map(RecipientId::from).toList();
    long              messageId    = getArguments().getLong(MESSAGE_ID_EXTRA, -1);
    String            messageType  = getArguments().getString(MESSAGE_TYPE_EXTRA, null);

    viewModel = ViewModelProviders.of(this, new SafetyNumberChangeViewModel.Factory(recipientIds, (messageId != -1) ? messageId : null, messageType)).get(SafetyNumberChangeViewModel.class);
    viewModel.getChangedRecipients().observe(getViewLifecycleOwner(), adapter::submitList);
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    int continueText = requireArguments().getInt(CONTINUE_TEXT_RESOURCE_EXTRA, android.R.string.ok);
    int cancelText   = requireArguments().getInt(CANCEL_TEXT_RESOURCE_EXTRA, android.R.string.cancel);

    dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.safety_number_change_dialog, null);

    AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), getTheme());

    configureView(dialogView);

    builder.setTitle(R.string.safety_number_change_dialog__safety_number_changes)
           .setView(dialogView)
           .setPositiveButton(continueText, this::handleSendAnyway)
           .setNegativeButton(cancelText, this::handleCancel);

    setCancelable(false);

    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(d -> {
      Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
      positive.setEnabled(false);
      viewModel.getTrustOrVerifyReady().observe(getViewLifecycleOwner(), positive::setEnabled);
    });

    return dialog;
  }

  @Override
  public void onDestroyView() {
    dialogView = null;
    super.onDestroyView();
  }

  private void configureView(View view) {
    RecyclerView list = view.findViewById(R.id.safety_number_change_dialog_list);
    adapter = new SafetyNumberChangeAdapter(this);
    list.setAdapter(adapter);
    list.setItemAnimator(null);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
  }

  private void updateRecipients(Collection<RecipientId> recipientIds) {
    viewModel.updateRecipients(recipientIds);
  }

  private void handleSendAnyway(DialogInterface dialogInterface, int which) {
    Log.d(TAG, "handleSendAnyway");

    boolean skipCallbacks = requireArguments().getBoolean(SKIP_CALLBACKS_EXTRA, false);

    Activity activity = getActivity();
    Callback callback;
    if (activity instanceof Callback && !skipCallbacks) {
      callback = (Callback) activity;
    } else {
      Fragment parent = getParentFragment();
      if (parent instanceof Callback && !skipCallbacks) {
        callback = (Callback) parent;
      } else {
        callback = null;
      }
    }

    LiveData<TrustAndVerifyResult> trustOrVerifyResultLiveData = viewModel.trustOrVerifyChangedRecipients();

    Observer<TrustAndVerifyResult> observer = new Observer<TrustAndVerifyResult>() {
      @Override
      public void onChanged(TrustAndVerifyResult result) {
        if (callback != null) {
          switch (result.getResult()) {
            case TRUST_AND_VERIFY:
              Log.d(TAG, "Trust and verify");
              callback.onSendAnywayAfterSafetyNumberChange(result.getChangedRecipients());
              break;
            case TRUST_VERIFY_AND_RESEND:
              Log.d(TAG, "Trust, verify, and resent");
              callback.onMessageResentAfterSafetyNumberChange();
              break;
          }
        }
        trustOrVerifyResultLiveData.removeObserver(this);
      }
    };

    trustOrVerifyResultLiveData.observeForever(observer);
  }

  private void handleCancel(@NonNull DialogInterface dialogInterface, int which) {
    if (getActivity() instanceof Callback) {
      ((Callback) getActivity()).onCanceled();
    } else if (getParentFragment() instanceof Callback) {
      ((Callback) getParentFragment()).onCanceled();
    }
  }

  @Override
  public void onViewIdentityRecord(@NonNull IdentityRecord identityRecord) {
    startActivity(VerifyIdentityActivity.newIntent(requireContext(), identityRecord));
  }

  public interface Callback {
    void onSendAnywayAfterSafetyNumberChange(@NonNull List<RecipientId> changedRecipients);

    void onMessageResentAfterSafetyNumberChange();

    void onCanceled();
  }
}
