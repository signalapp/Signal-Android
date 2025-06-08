package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.List;

/**
 * Fragment that selects Signal contacts. Intended to be used in the camera-first capture flow.
 */
public class CameraContactSelectionFragment extends LoggingFragment implements CameraContactAdapter.CameraContactListener {

  private Controller                      controller;
  private CameraContactSelectionViewModel contactViewModel;
  private RecyclerView                    contactList;
  private CameraContactAdapter            contactAdapter;
  private RecyclerView                    selectionList;
  private CameraContactSelectionAdapter   selectionAdapter;
  private Toolbar                         toolbar;
  private View                            sendButton;
  private Group                           selectionFooterGroup;
  private ViewGroup                       cameraContactsEmpty;
  private View                            inviteButton;

  public static Fragment newInstance() {
    return new CameraContactSelectionFragment();
  }


  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);

    CameraContactSelectionViewModel.Factory factory = new CameraContactSelectionViewModel.Factory(new CameraContactsRepository(requireContext()));

    this.contactViewModel = new ViewModelProvider(requireActivity(), factory).get(CameraContactSelectionViewModel.class);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement controller interface.");
    }
    controller = (Controller) getActivity();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    int            theme          = DynamicTheme.isDarkTheme(inflater.getContext()) ? R.style.TextSecure_DarkTheme
                                                                                    : R.style.TextSecure_LightTheme;
    return ThemeUtil.getThemedInflater(inflater.getContext(), inflater, theme)
                    .inflate(R.layout.camera_contact_selection_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.contactList          = view.findViewById(R.id.camera_contacts_list);
    this.selectionList        = view.findViewById(R.id.camera_contacts_selected_list);
    this.toolbar              = view.findViewById(R.id.camera_contacts_toolbar);
    this.sendButton           = view.findViewById(R.id.camera_contacts_send_button);
    this.selectionFooterGroup = view.findViewById(R.id.camera_contacts_footer_group);
    this.cameraContactsEmpty  = view.findViewById(R.id.camera_contacts_empty);
    this.inviteButton         = view.findViewById(R.id.camera_contacts_invite_button);
    this.contactAdapter       = new CameraContactAdapter(Glide.with(this), this);
    this.selectionAdapter     = new CameraContactSelectionAdapter();

    contactList.setLayoutManager(new LinearLayoutManager(requireContext()));
    contactList.setAdapter(contactAdapter);

    selectionList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
    selectionList.setAdapter(selectionAdapter);

    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

    inviteButton.setOnClickListener(v -> onInviteContactsClicked());

    initViewModel();
  }

  @Override
  public void onPrepareOptionsMenu(@NonNull Menu menu) {
    requireActivity().getMenuInflater().inflate(R.menu.camera_contacts, menu);

    MenuItem   searchViewItem                    = menu.findItem(R.id.menu_search);
    SearchView searchView                        = (SearchView) searchViewItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        contactViewModel.onQueryUpdated(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        contactViewModel.onQueryUpdated(query);
        return true;
      }
    };

    searchViewItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchView.setOnQueryTextListener(queryListener);
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        searchView.setOnQueryTextListener(null);
        contactViewModel.onSearchClosed();
        return true;
      }
    });
  }

  @Override
  public void onContactClicked(@NonNull Recipient recipient) {
    contactViewModel.onContactClicked(recipient);
  }

  @Override
  public void onInviteContactsClicked() {
    startActivity(AppSettingsActivity.invite(requireContext()));
  }

  private void initViewModel() {
    contactViewModel.getContacts().observe(getViewLifecycleOwner(), contactState -> {
      if (contactState == null) return;

      if (contactState.getContacts().isEmpty() && TextUtils.isEmpty(contactState.getQuery())) {
        cameraContactsEmpty.setVisibility(View.VISIBLE);
        contactList.setVisibility(View.GONE);
        selectionFooterGroup.setVisibility(View.GONE);
      } else {
        cameraContactsEmpty.setVisibility(View.GONE);
        contactList.setVisibility(View.VISIBLE);

        sendButton.setOnClickListener(v -> controller.onCameraContactsSendClicked(contactState.getSelected()));

        contactAdapter.setContacts(contactState.getContacts(), contactState.getSelected());
        selectionAdapter.setRecipients(contactState.getSelected());

        selectionFooterGroup.setVisibility(contactState.getSelected().isEmpty() ? View.GONE : View.VISIBLE);
      }
    });

    contactViewModel.getError().observe(getViewLifecycleOwner(), error -> {
      if (error == null) return;

      if (error == CameraContactSelectionViewModel.Error.MAX_SELECTION) {
        String message = getResources().getQuantityString(R.plurals.CameraContacts_you_can_share_with_a_maximum_of_n_conversations, CameraContactSelectionViewModel.MAX_SELECTION_COUNT, CameraContactSelectionViewModel.MAX_SELECTION_COUNT);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
      }
    });
  }

  public interface Controller {
    void onCameraContactsSendClicked(@NonNull List<Recipient> recipients);
  }
}
