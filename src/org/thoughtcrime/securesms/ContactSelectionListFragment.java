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
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionListFragment extends    Fragment
                                          implements LoaderManager.LoaderCallbacks<Cursor>
{
  @SuppressWarnings("unused")
  private static final String TAG = ContactSelectionListFragment.class.getSimpleName();

  public static final String DISPLAY_MODE = "display_mode";
  public static final String MULTI_SELECT = "multi_select";
  public static final String REFRESHABLE  = "refreshable";
  public static final String RECENTS      = "recents";

  public final static int DISPLAY_MODE_ALL       = ContactsCursorLoader.MODE_ALL;
  public final static int DISPLAY_MODE_PUSH_ONLY = ContactsCursorLoader.MODE_PUSH_ONLY;
  public final static int DISPLAY_MODE_SMS_ONLY  = ContactsCursorLoader.MODE_SMS_ONLY;

  private static final int RECENT_CONTACTS_INCREMENT = 5;

  private TextView emptyText;

  private Set<String>               selectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private SwipeRefreshLayout        swipeRefresh;
  private View                      showContactsLayout;
  private Button                    showContactsButton;
  private TextView                  showContactsDescription;
  private ProgressWheel             showContactsProgress;
  private String                    cursorFilter;
  private RecyclerView              recyclerView;
  private RecyclerViewFastScroller  fastScroller;

  private int recentContactsLimit;

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
                   this.getLoaderManager().initLoader(0, null, this);
                 }
               })
               .onAnyDenied(() -> {
                 getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

                 if (getActivity().getIntent().getBooleanExtra(RECENTS, false)) {
                   getLoaderManager().initLoader(0, null, ContactSelectionListFragment.this);
                 } else {
                   initializeNoContactsPermission();
                 }
               })
               .execute();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText               = ViewUtil.findById(view, android.R.id.empty);
    recyclerView            = ViewUtil.findById(view, R.id.recycler_view);
    swipeRefresh            = ViewUtil.findById(view, R.id.swipe_refresh);
    fastScroller            = ViewUtil.findById(view, R.id.fast_scroller);
    showContactsLayout      = view.findViewById(R.id.show_contacts_container);
    showContactsButton      = view.findViewById(R.id.show_contacts_button);
    showContactsDescription = view.findViewById(R.id.show_contacts_description);
    showContactsProgress    = view.findViewById(R.id.progress);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    swipeRefresh.setEnabled(getActivity().getIntent().getBooleanExtra(REFRESHABLE, true) &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);

    return view;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public @NonNull List<String> getSelectedContacts() {
    List<String> selected = new LinkedList<>();
    if (selectedContacts != null) {
      selected.addAll(selectedContacts);
    }

    return selected;
  }

  private boolean isMulti() {
    return getActivity().getIntent().getBooleanExtra(MULTI_SELECT, false);
  }

  private void initializeCursor() {
    ContactSelectionListAdapter adapter = new ContactSelectionListAdapter(getActivity(),
                                                                          GlideApp.with(this),
                                                                          null,
                                                                          new ListClickListener(),
                                                                          new MoreClickListener(),
                                                                          isMulti());
    selectedContacts = adapter.getSelectedContacts();
    recyclerView.setAdapter(adapter);
    recyclerView.addItemDecoration(new StickyHeaderDecoration(adapter, true, true));
    recentContactsLimit = RECENT_CONTACTS_INCREMENT;
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
    this.getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    setQueryFilter(null);
    swipeRefresh.setRefreshing(false);
  }

  public void setRefreshing(boolean refreshing) {
    swipeRefresh.setRefreshing(refreshing);
  }

  public void reset() {
    selectedContacts.clear();

    if (!isDetached() && !isRemoving() && getActivity() != null && !getActivity().isFinishing()) {
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  public void showMoreRecentContacts() {
    recentContactsLimit += RECENT_CONTACTS_INCREMENT;
    getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ContactsCursorLoader(getActivity(),
                                    getActivity().getIntent().getIntExtra(DISPLAY_MODE, DISPLAY_MODE_ALL),
                                    cursorFilter, getActivity().getIntent().getBooleanExtra(RECENTS, false),
                                    recentContactsLimit);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    swipeRefresh.setVisibility(View.VISIBLE);
    showContactsLayout.setVisibility(View.GONE);

    ((CursorRecyclerViewAdapter) recyclerView.getAdapter()).changeCursor(data);
    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
    boolean useFastScroller = (recyclerView.getAdapter().getItemCount() > 20);
    recyclerView.setVerticalScrollBarEnabled(!useFastScroller);
    if (useFastScroller) {
      fastScroller.setVisibility(View.VISIBLE);
      fastScroller.setRecyclerView(recyclerView);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((CursorRecyclerViewAdapter) recyclerView.getAdapter()).changeCursor(null);
    fastScroller.setVisibility(View.GONE);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleContactPermissionGranted() {
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
          DirectoryHelper.refreshDirectory(getContext(), false);
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
      if (!isMulti() || !selectedContacts.contains(contact.getNumber())) {
        selectedContacts.add(contact.getNumber());
        contact.setChecked(true);
        if (onContactSelectedListener != null) onContactSelectedListener.onContactSelected(contact.getNumber());
      } else {
        selectedContacts.remove(contact.getNumber());
        contact.setChecked(false);
        if (onContactSelectedListener != null) onContactSelectedListener.onContactDeselected(contact.getNumber());
      }
    }
  }

  private class MoreClickListener implements ContactSelectionListAdapter.MoreClickListener {
    @Override
    public void onMoreClick() {
      showMoreRecentContacts();
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  public interface OnContactSelectedListener {
    void onContactSelected(String number);
    void onContactDeselected(String number);
  }

}
