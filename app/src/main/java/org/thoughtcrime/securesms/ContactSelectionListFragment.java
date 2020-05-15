/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.contacts.ContactChip;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 *
 */
public final class ContactSelectionListFragment extends    Fragment
                                                implements LoaderManager.LoaderCallbacks<Cursor>
{
  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ContactSelectionListFragment.class);

  public static final String DISPLAY_MODE = "display_mode";
  public static final String MULTI_SELECT = "multi_select";
  public static final String REFRESHABLE  = "refreshable";
  public static final String RECENTS      = "recents";

  private final Debouncer scrollDebounce = new Debouncer(100);

  private TextView                    emptyText;
  private OnContactSelectedListener   onContactSelectedListener;
  private SwipeRefreshLayout          swipeRefresh;
  private View                        showContactsLayout;
  private Button                      showContactsButton;
  private TextView                    showContactsDescription;
  private ProgressWheel               showContactsProgress;
  private String                      cursorFilter;
  private RecyclerView                recyclerView;
  private RecyclerViewFastScroller    fastScroller;
  private ContactSelectionListAdapter cursorRecyclerViewAdapter;
  private ChipGroup                   chipGroup;
  private HorizontalScrollView        chipGroupScrollContainer;

  @Nullable private FixedViewsAdapter headerAdapter;
  @Nullable private FixedViewsAdapter footerAdapter;
  @Nullable private ListCallback      listCallback;
            private GlideRequests     glideRequests;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof ListCallback) {
      listCallback = (ListCallback) context;
    }
  }

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onActivityCreated(icicle);

    initializeCursor();
  }

  @Override
  public void onStart() {
    super.onStart();

    Permissions.with(this)
               .request(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
               .ifNecessary()
               .onAllGranted(() -> {
                 if (!TextSecurePreferences.hasSuccessfullyRetrievedDirectory(getActivity())) {
                   handleContactPermissionGranted();
                 } else {
                   LoaderManager.getInstance(this).initLoader(0, null, this);
                 }
               })
               .onAnyDenied(() -> {
                 FragmentActivity activity = requireActivity();

                 activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

                 if (activity.getIntent().getBooleanExtra(RECENTS, false)) {
                   LoaderManager.getInstance(this).initLoader(0, null, ContactSelectionListFragment.this);
                 } else {
                   initializeNoContactsPermission();
                 }
               })
               .execute();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText                = ViewUtil.findById(view, android.R.id.empty);
    recyclerView             = ViewUtil.findById(view, R.id.recycler_view);
    swipeRefresh             = ViewUtil.findById(view, R.id.swipe_refresh);
    fastScroller             = ViewUtil.findById(view, R.id.fast_scroller);
    showContactsLayout       = view.findViewById(R.id.show_contacts_container);
    showContactsButton       = view.findViewById(R.id.show_contacts_button);
    showContactsDescription  = view.findViewById(R.id.show_contacts_description);
    showContactsProgress     = view.findViewById(R.id.progress);
    chipGroup                = view.findViewById(R.id.chipGroup);
    chipGroupScrollContainer = view.findViewById(R.id.chipGroupScrollContainer);

    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    swipeRefresh.setEnabled(requireActivity().getIntent().getBooleanExtra(REFRESHABLE, true));

    autoScrollOnNewItem();

    return view;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public @NonNull List<SelectedContact> getSelectedContacts() {
    if (cursorRecyclerViewAdapter == null) {
      return Collections.emptyList();
    }

    return cursorRecyclerViewAdapter.getSelectedContacts();
  }

  public int getSelectedContactsCount() {
    if (cursorRecyclerViewAdapter == null) {
      return 0;
    }

    return cursorRecyclerViewAdapter.getSelectedContactsCount();
  }

  private boolean isMulti() {
    return requireActivity().getIntent().getBooleanExtra(MULTI_SELECT, false);
  }

  private void initializeCursor() {
    glideRequests = GlideApp.with(this);

    cursorRecyclerViewAdapter = new ContactSelectionListAdapter(requireContext(),
                                                                glideRequests,
                                                                null,
                                                                new ListClickListener(),
                                                                isMulti());

    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();

    if (listCallback != null && FeatureFlags.newGroupUI()) {
      headerAdapter = new FixedViewsAdapter(createNewGroupItem(listCallback));
      headerAdapter.hide();
      concatenateAdapter.addAdapter(headerAdapter);
    }

    concatenateAdapter.addAdapter(cursorRecyclerViewAdapter);

    if (listCallback != null) {
      footerAdapter = new FixedViewsAdapter(createInviteActionView(listCallback));
      footerAdapter.hide();
      concatenateAdapter.addAdapter(footerAdapter);
    }

    recyclerView.setAdapter(concatenateAdapter);
    recyclerView.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));
  }

  private View createInviteActionView(@NonNull ListCallback listCallback) {
    View view = LayoutInflater.from(requireContext())
                              .inflate(R.layout.contact_selection_invite_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> listCallback.onInvite());
    return view;
  }

  private View createNewGroupItem(@NonNull ListCallback listCallback) {
    View view = LayoutInflater.from(requireContext())
                              .inflate(R.layout.contact_selection_new_group_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> listCallback.onNewGroup());
    return view;
  }

  private void initializeNoContactsPermission() {
    swipeRefresh.setVisibility(View.GONE);

    showContactsLayout.setVisibility(View.VISIBLE);
    showContactsProgress.setVisibility(View.INVISIBLE);
    showContactsDescription.setText(R.string.contact_selection_list_fragment__signal_needs_access_to_your_contacts_in_order_to_display_them);
    showContactsButton.setVisibility(View.VISIBLE);

    showContactsButton.setOnClickListener(v -> {
      Permissions.with(this)
                 .request(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(R.string.ContactSelectionListFragment_signal_requires_the_contacts_permission_in_order_to_display_your_contacts))
                 .onSomeGranted(permissions -> {
                   if (permissions.contains(Manifest.permission.WRITE_CONTACTS)) {
                     handleContactPermissionGranted();
                   }
                 })
                 .execute();
    });
  }

  public void setQueryFilter(String filter) {
    this.cursorFilter = filter;
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    setQueryFilter(null);
    swipeRefresh.setRefreshing(false);
  }

  public void setRefreshing(boolean refreshing) {
    swipeRefresh.setRefreshing(refreshing);
  }

  public void reset() {
    cursorRecyclerViewAdapter.clearSelectedContacts();

    if (!isDetached() && !isRemoving() && getActivity() != null && !getActivity().isFinishing()) {
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ContactsCursorLoader(getActivity(),
                                    getActivity().getIntent().getIntExtra(DISPLAY_MODE, DisplayMode.FLAG_ALL),
                                    cursorFilter, getActivity().getIntent().getBooleanExtra(RECENTS, false));
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, @Nullable Cursor data) {
    swipeRefresh.setVisibility(View.VISIBLE);
    showContactsLayout.setVisibility(View.GONE);

    cursorRecyclerViewAdapter.changeCursor(data);

    if (footerAdapter != null) {
      footerAdapter.show();
    }

    if (headerAdapter != null) {
      headerAdapter.show();
    }

    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
    boolean useFastScroller = data != null && data.getCount() > 20;
    recyclerView.setVerticalScrollBarEnabled(!useFastScroller);
    if (useFastScroller) {
      fastScroller.setVisibility(View.VISIBLE);
      fastScroller.setRecyclerView(recyclerView);
    } else {
      fastScroller.setRecyclerView(null);
      fastScroller.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    cursorRecyclerViewAdapter.changeCursor(null);
    fastScroller.setVisibility(View.GONE);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleContactPermissionGranted() {
    final Context context = requireContext();

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected void onPreExecute() {
        swipeRefresh.setVisibility(View.GONE);
        showContactsLayout.setVisibility(View.VISIBLE);
        showContactsButton.setVisibility(View.INVISIBLE);
        showContactsDescription.setText(R.string.ConversationListFragment_loading);
        showContactsProgress.setVisibility(View.VISIBLE);
        showContactsProgress.spin();
      }

      @Override
      protected Boolean doInBackground(Void... voids) {
        try {
          DirectoryHelper.refreshDirectory(context, false);
          return true;
        } catch (IOException e) {
          Log.w(TAG, e);
        }
        return false;
      }

      @Override
      protected void onPostExecute(Boolean result) {
        if (result) {
          showContactsLayout.setVisibility(View.GONE);
          swipeRefresh.setVisibility(View.VISIBLE);
          reset();
        } else {
          Toast.makeText(getContext(), R.string.ContactSelectionListFragment_error_retrieving_contacts_check_your_network_connection, Toast.LENGTH_LONG).show();
          initializeNoContactsPermission();
        }
      }
    }.execute();
  }

  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {
      SelectedContact selectedContact = contact.isUsernameType() ? SelectedContact.forUsername(contact.getRecipientId().orNull(), contact.getNumber())
                                                                 : SelectedContact.forPhone(contact.getRecipientId().orNull(), contact.getNumber());

      if (!isMulti() || !cursorRecyclerViewAdapter.isSelectedContact(selectedContact)) {
        if (contact.isUsernameType()) {
          AlertDialog loadingDialog = SimpleProgressDialog.show(requireContext());

          SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            return UsernameUtil.fetchUuidForUsername(requireContext(), contact.getNumber());
          }, uuid -> {
            loadingDialog.dismiss();
            if (uuid.isPresent()) {
              Recipient recipient = Recipient.externalUsername(requireContext(), uuid.get(), contact.getNumber());
              SelectedContact selected = SelectedContact.forUsername(recipient.getId(), contact.getNumber());
              markContactSelected(selected, contact);

              if (onContactSelectedListener != null) {
                onContactSelectedListener.onContactSelected(Optional.of(recipient.getId()), null);
              }
            } else {
              new AlertDialog.Builder(requireContext())
                             .setTitle(R.string.ContactSelectionListFragment_username_not_found)
                             .setMessage(getString(R.string.ContactSelectionListFragment_s_is_not_a_signal_user, contact.getNumber()))
                             .setPositiveButton(R.string.ContactSelectionListFragment_okay, (dialog, which) -> dialog.dismiss())
                             .show();
            }
          });
        } else {
          markContactSelected(selectedContact, contact);

          if (onContactSelectedListener != null) {
            onContactSelectedListener.onContactSelected(contact.getRecipientId(), contact.getNumber());
          }
        }
      } else {
        markContactUnselected(selectedContact, contact);

        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(contact.getRecipientId(), contact.getNumber());
        }
      }}
  }

  private void markContactSelected(@NonNull SelectedContact selectedContact, @NonNull ContactSelectionListItem listItem) {
    cursorRecyclerViewAdapter.addSelectedContact(selectedContact);
    listItem.setChecked(true);
    if (isMulti() && FeatureFlags.newGroupUI()) {
      chipGroup.addView(newChipForContact(listItem, selectedContact));
    }
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact, @NonNull ContactSelectionListItem listItem) {
    cursorRecyclerViewAdapter.removeFromSelectedContacts(selectedContact);
    listItem.setChecked(false);
    removeChipForContact(selectedContact);
  }

  private void removeChipForContact(@NonNull SelectedContact contact) {
    for (int i = chipGroup.getChildCount() - 1; i >= 0; i--) {
      View v = chipGroup.getChildAt(i);
      if (v instanceof ContactChip && contact.matches(((ContactChip) v).getContact())) {
        chipGroup.removeView(v);
      }
    }
  }

  private View newChipForContact(@NonNull ContactSelectionListItem contact, @NonNull SelectedContact selectedContact) {
    final ContactChip chip = new ContactChip(requireContext());
    chip.setText(contact.getChipName());
    chip.setContact(selectedContact);

    LiveRecipient recipient = contact.getRecipient();
    if (recipient != null) {
      recipient.observe(getViewLifecycleOwner(), resolved -> {
          chip.setAvatar(glideRequests, resolved);
          chip.setText(resolved.getShortDisplayName(chip.getContext()));
        }
      );
    }

    chip.setCloseIconVisible(true);
    chip.setOnCloseIconClickListener(view -> {
      markContactUnselected(selectedContact, contact);
      chipGroup.removeView(chip);
    });
    return chip;
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  private void autoScrollOnNewItem() {
    chipGroup.addOnLayoutChangeListener((view1, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (right > oldRight) {
        scrollDebounce.publish(this::smoothScrollChipsToEnd);
      }
    });
  }

  private void smoothScrollChipsToEnd() {
    int x = chipGroupScrollContainer.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR ? chipGroup.getWidth() : 0;
    chipGroupScrollContainer.smoothScrollTo(x, 0);
  }

  public interface OnContactSelectedListener {
    void onContactSelected(Optional<RecipientId> recipientId, String number);
    void onContactDeselected(Optional<RecipientId> recipientId, String number);
  }

  public interface ListCallback {
    void onInvite();
    void onNewGroup();
  }
}
