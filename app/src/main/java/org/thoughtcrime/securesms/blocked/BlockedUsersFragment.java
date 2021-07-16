package org.thoughtcrime.securesms.blocked;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.glide.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.Mp02CustomDialog;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;

public class BlockedUsersFragment extends Fragment {

  private BlockedUsersViewModel viewModel;
  private Listener              listener;
  private RecyclerView        recycler;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Listener) {
      listener = (Listener) context;
    } else {
      throw new ClassCastException("Expected context to implement Listener");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();

    listener = null;
  }

  @Override
  public void onResume() {
    super.onResume();
    recycler.requestFocus();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.blocked_users_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    recycler = view.findViewById(R.id.blocked_users_recycler);
    BlockedUsersAdapter adapter  = new BlockedUsersAdapter(this::handleRecipientClicked);

    recycler.setAdapter(adapter);
    recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
    recycler.setClipToPadding(false);
    recycler.setClipChildren(false);
    recycler.setPadding(0, 76, 0, 200);
    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();
    adapter.setAddListener(listener);
    concatenateAdapter.addAdapter(adapter);
    recycler.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));

    viewModel = ViewModelProviders.of(requireActivity()).get(BlockedUsersViewModel.class);
    viewModel.getRecipients().observe(getViewLifecycleOwner(), list -> {
      adapter.submitList(list);
    });
  }

  private void handleRecipientClicked(@NonNull Recipient recipient) {
    Mp02CustomDialog dialog = new Mp02CustomDialog(requireContext());
    dialog.setMessage(getString(R.string.BlockedUsersActivity__do_you_want_to_unblock_s, recipient.getDisplayName(requireContext())));
    dialog.setNegativeListener(android.R.string.cancel, new Mp02CustomDialog.Mp02DialogKeyListener() {
      @Override
      public void onDialogKeyClicked() {
        dialog.dismiss();
      }
    });
    dialog.setPositiveListener(R.string.BlockedUsersActivity__unblock, new Mp02CustomDialog.Mp02DialogKeyListener() {
      @Override
      public void onDialogKeyClicked() {
        viewModel.unblock(recipient.getId());
        dialog.dismiss();
      }
    });
    dialog.show();
  }

  interface Listener {
    void handleAddUserToBlockedList();
  }
}
