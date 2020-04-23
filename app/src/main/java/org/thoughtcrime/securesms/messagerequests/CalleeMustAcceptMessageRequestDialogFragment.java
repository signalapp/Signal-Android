package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.concurrent.TimeUnit;

public class CalleeMustAcceptMessageRequestDialogFragment extends DialogFragment {

  private static final long   TIMEOUT_MS       = TimeUnit.SECONDS.toMillis(10);
  private static final String ARG_RECIPIENT_ID = "arg.recipient.id";

  private TextView        description;
  private AvatarImageView avatar;
  private View            okay;

  private final Handler  handler   = new Handler(Looper.getMainLooper());
  private final Runnable dismisser = this::dismiss;

  public static DialogFragment create(@NonNull RecipientId recipientId) {
    DialogFragment fragment = new CalleeMustAcceptMessageRequestDialogFragment();
    Bundle         args     = new Bundle();

    args.putParcelable(ARG_RECIPIENT_ID, recipientId);

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setStyle(DialogFragment.STYLE_NO_FRAME, R.style.TextSecure_DarkNoActionBar);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.callee_must_accept_message_request_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    description = view.findViewById(R.id.description);
    avatar      = view.findViewById(R.id.avatar);
    okay        = view.findViewById(R.id.okay);

    avatar.setFallbackPhotoProvider(new FallbackPhotoProvider());
    okay.setOnClickListener(v -> dismiss());

    RecipientId                                     recipientId = requireArguments().getParcelable(ARG_RECIPIENT_ID);
    CalleeMustAcceptMessageRequestViewModel.Factory factory     = new CalleeMustAcceptMessageRequestViewModel.Factory(recipientId);
    CalleeMustAcceptMessageRequestViewModel         viewModel   = ViewModelProviders.of(this, factory).get(CalleeMustAcceptMessageRequestViewModel.class);

    viewModel.getRecipient().observe(getViewLifecycleOwner(), recipient -> {
      description.setText(getString(R.string.CalleeMustAcceptMessageRequestDialogFragment__s_will_get_a_message_request_from_you, recipient.getDisplayName(requireContext())));
      avatar.setAvatar(GlideApp.with(this), recipient, false);
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    handler.postDelayed(dismisser, TIMEOUT_MS);
  }

  @Override
  public void onPause() {
    super.onPause();

    handler.removeCallbacks(dismisser);
  }

  private static class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      return new ResourceContactPhoto(R.drawable.ic_profile_80);
    }
  }
}
