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
import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.material.chip.ChipGroup;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.components.emoji.WarningTextView;
import org.thoughtcrime.securesms.contacts.ContactChip;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 *
 */
public final class ContactSelectionListFragment extends LoggingFragment
                                                implements LoaderManager.LoaderCallbacks<Cursor>
{
  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ContactSelectionListFragment.class);

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 1;
  private static final int CHIP_GROUP_REVEAL_DURATION_MS = 150;

  public static final int NO_LIMIT = Integer.MAX_VALUE;

  public static final String DISPLAY_MODE      = "display_mode";
  public static final String REFRESHABLE       = "refreshable";
  public static final String RECENTS           = "recents";
  public static final String SELECTION_LIMITS  = "selection_limits";
  public static final String CURRENT_SELECTION = "current_selection";
  public static final String HIDE_COUNT        = "hide_count";

  private ConstraintLayout            constraintLayout;
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
  private WarningTextView             groupLimit;

  @Nullable private FixedViewsAdapter headerAdapter;
  @Nullable private FixedViewsAdapter footerAdapter;
  @Nullable private ListCallback      listCallback;
  @Nullable private ScrollCallback    scrollCallback;
            private GlideRequests     glideRequests;
            private SelectionLimits   selectionLimit   = SelectionLimits.NO_LIMITS;
            private Set<RecipientId>  currentSelection;
            private boolean           isMulti;
            private boolean           hideCount;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof ListCallback) {
      listCallback = (ListCallback) context;
    }

    if (context instanceof ScrollCallback) {
      scrollCallback = (ScrollCallback) context;
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

    emptyText                = view.findViewById(android.R.id.empty);
    recyclerView             = view.findViewById(R.id.recycler_view);
    swipeRefresh             = view.findViewById(R.id.swipe_refresh);
    fastScroller             = view.findViewById(R.id.fast_scroller);
    showContactsLayout       = view.findViewById(R.id.show_contacts_container);
    showContactsButton       = view.findViewById(R.id.show_contacts_button);
    showContactsDescription  = view.findViewById(R.id.show_contacts_description);
    showContactsProgress     = view.findViewById(R.id.progress);
    chipGroup                = view.findViewById(R.id.chipGroup);
    chipGroupScrollContainer = view.findViewById(R.id.chipGroupScrollContainer);
    groupLimit               = view.findViewById(R.id.group_limit);
    constraintLayout         = view.findViewById(R.id.container);

    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    recyclerView.setItemAnimator(new DefaultItemAnimator() {
      @Override
      public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
      }
    });

    Intent intent = requireActivity().getIntent();

    swipeRefresh.setEnabled(intent.getBooleanExtra(REFRESHABLE, true));

    hideCount      = intent.getBooleanExtra(HIDE_COUNT, false);
    selectionLimit = intent.getParcelableExtra(SELECTION_LIMITS);
    isMulti        = selectionLimit != null;

    if (!isMulti) {
      selectionLimit = SelectionLimits.NO_LIMITS;
    }

    currentSelection = getCurrentSelection();

    updateGroupLimit(getChipCount());

    return view;
  }

  private void updateGroupLimit(int chipCount) {
    int members = currentSelection.size() + chipCount;
    groupLimit.setText(getResources().getQuantityString(R.plurals.ContactSelectionListFragment_d_members, members, members));
    groupLimit.setVisibility(isMulti && !hideCount ? View.VISIBLE : View.GONE);
    groupLimit.setWarning(selectionWarningLimitExceeded());
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

  private Set<RecipientId> getCurrentSelection() {
    List<RecipientId> currentSelection = requireActivity().getIntent().getParcelableArrayListExtra(CURRENT_SELECTION);

    return currentSelection == null ? Collections.emptySet()
                                    : Collections.unmodifiableSet(Stream.of(currentSelection).collect(Collectors.toSet()));
  }

  public boolean isMulti() {
    return isMulti;
  }

  private void initializeCursor() {
    glideRequests = GlideApp.with(this);

    cursorRecyclerViewAdapter = new ContactSelectionListAdapter(requireContext(),
                                                                glideRequests,
                                                                null,
                                                                new ListClickListener(),
                                                                isMulti,
                                                                currentSelection);

    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();

    if (listCallback != null) {
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
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          if (scrollCallback != null) {
            scrollCallback.onBeginScroll();
          }
        }
      }
    });
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
    view.setOnClickListener(v -> listCallback.onNewGroup(false));
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

  public boolean hasQueryFilter() {
    return !TextUtils.isEmpty(cursorFilter);
  }

  public void setRefreshing(boolean refreshing) {
    swipeRefresh.setRefreshing(refreshing);
  }

  public void reset() {
    cursorRecyclerViewAdapter.clearSelectedContacts();

    if (!isDetached() && !isRemoving() && getActivity() != null && !getActivity().isFinishing()) {
      LoaderManager.getInstance(this).restartLoader(0, null, this);
    }
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int id, Bundle args) {
    FragmentActivity activity = requireActivity();
    return new ContactsCursorLoader(activity,
                                    activity.getIntent().getIntExtra(DISPLAY_MODE, DisplayMode.FLAG_ALL),
                                    cursorFilter, activity.getIntent().getBooleanExtra(RECENTS, false));
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
      if (TextUtils.isEmpty(cursorFilter)) {
        headerAdapter.show();
      } else {
        headerAdapter.hide();
      }
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
          Context context = getContext();
          if (context != null) {
            Toast.makeText(getContext(), R.string.ContactSelectionListFragment_error_retrieving_contacts_check_your_network_connection, Toast.LENGTH_LONG).show();
            initializeNoContactsPermission();
          }
        }
      }
    }.execute();
  }

  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {
      SelectedContact selectedContact = contact.isUsernameType() ? SelectedContact.forUsername(contact.getRecipientId().orNull(), contact.getNumber())
                                                                 : SelectedContact.forPhone(contact.getRecipientId().orNull(), contact.getNumber());

      if (isMulti && Recipient.self().getId().equals(selectedContact.getOrCreateRecipientId(requireContext()))) {
        Toast.makeText(requireContext(), R.string.ContactSelectionListFragment_you_do_not_need_to_add_yourself_to_the_group, Toast.LENGTH_SHORT).show();
        return;
      }

      if (!isMulti || !cursorRecyclerViewAdapter.isSelectedContact(selectedContact)) {
        if (selectionHardLimitReached()) {
          GroupLimitDialog.showHardLimitMessage(requireContext());
          return;
        }

        if (contact.isUsernameType()) {
          AlertDialog loadingDialog = SimpleProgressDialog.show(requireContext());

          SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            return UsernameUtil.fetchUuidForUsername(requireContext(), contact.getNumber());
          }, uuid -> {
            loadingDialog.dismiss();
            if (uuid.isPresent()) {
              Recipient recipient = Recipient.externalUsername(requireContext(), uuid.get(), contact.getNumber());
              SelectedContact selected = SelectedContact.forUsername(recipient.getId(), contact.getNumber());

              if (onContactSelectedListener != null) {
                if (onContactSelectedListener.onBeforeContactSelected(Optional.of(recipient.getId()), null)) {
                  markContactSelected(selected);
                  cursorRecyclerViewAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(contact), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
                }
              } else {
                markContactSelected(selected);
                cursorRecyclerViewAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(contact), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
              }
            } else {
              new AlertDialog.Builder(requireContext())
                             .setTitle(R.string.ContactSelectionListFragment_username_not_found)
                             .setMessage(getString(R.string.ContactSelectionListFragment_s_is_not_a_signal_user, contact.getNumber()))
                             .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                             .show();
            }
          });
        } else {
          if (onContactSelectedListener != null) {
            if (onContactSelectedListener.onBeforeContactSelected(contact.getRecipientId(), contact.getNumber())) {
              markContactSelected(selectedContact);
              cursorRecyclerViewAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(contact), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
            }
          } else {
            markContactSelected(selectedContact);
            cursorRecyclerViewAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(contact), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
          }
        }
      } else {
        markContactUnselected(selectedContact);
        cursorRecyclerViewAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(contact), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);

        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(contact.getRecipientId(), contact.getNumber());
        }
      }
    }
  }

  private boolean selectionHardLimitReached() {
    return getChipCount() + currentSelection.size() >= selectionLimit.getHardLimit();
  }

  private boolean selectionWarningLimitReachedExactly() {
    return getChipCount() + currentSelection.size() == selectionLimit.getRecommendedLimit();
  }

  private boolean selectionWarningLimitExceeded() {
    return getChipCount() + currentSelection.size() > selectionLimit.getRecommendedLimit();
  }

  private void markContactSelected(@NonNull SelectedContact selectedContact) {
    cursorRecyclerViewAdapter.addSelectedContact(selectedContact);
    if (isMulti) {
      addChipForSelectedContact(selectedContact);
    }
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    cursorRecyclerViewAdapter.removeFromSelectedContacts(selectedContact);
    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
    removeChipForContact(selectedContact);
  }

  private void removeChipForContact(@NonNull SelectedContact contact) {
    for (int i = chipGroup.getChildCount() - 1; i >= 0; i--) {
      View v = chipGroup.getChildAt(i);
      if (v instanceof ContactChip && contact.matches(((ContactChip) v).getContact())) {
        chipGroup.removeView(v);
      }
    }

    updateGroupLimit(getChipCount());

    if (getChipCount() == 0) {
      setChipGroupVisibility(ConstraintSet.GONE);
    }
  }

  private void addChipForSelectedContact(@NonNull SelectedContact selectedContact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   ()       -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                   resolved -> addChipForRecipient(resolved, selectedContact));
  }

  private void addChipForRecipient(@NonNull Recipient recipient, @NonNull SelectedContact selectedContact) {
    final ContactChip chip = new ContactChip(requireContext());

    if (getChipCount() == 0) {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }

    chip.setText(recipient.getShortDisplayName(requireContext()));
    chip.setContact(selectedContact);
    chip.setCloseIconVisible(true);
    chip.setOnCloseIconClickListener(view -> {
      markContactUnselected(selectedContact);

      if (onContactSelectedListener != null) {
        onContactSelectedListener.onContactDeselected(Optional.of(recipient.getId()), recipient.getE164().orNull());
      }
    });

    chipGroup.getLayoutTransition().addTransitionListener(new LayoutTransition.TransitionListener() {
      @Override
      public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
      }

      @Override
      public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
        if (view == chip && transitionType == LayoutTransition.APPEARING) {
          chipGroup.getLayoutTransition().removeTransitionListener(this);
          registerChipRecipientObserver(chip, recipient.live());
          chipGroup.post(ContactSelectionListFragment.this::smoothScrollChipsToEnd);
        }
      }
    });

    chip.setAvatar(glideRequests, recipient, () -> addChip(chip));
  }

  private void addChip(@NonNull ContactChip chip) {
    chipGroup.addView(chip);
    updateGroupLimit(getChipCount());
    if (selectionWarningLimitReachedExactly()) {
      GroupLimitDialog.showRecommendedLimitMessage(requireContext());
    }
  }

  private int getChipCount() {
    int count = chipGroup.getChildCount() - CHIP_GROUP_EMPTY_CHILD_COUNT;
    if (count < 0) throw new AssertionError();
    return count;
  }

  private void registerChipRecipientObserver(@NonNull ContactChip chip, @Nullable LiveRecipient recipient) {
    if (recipient != null) {
      recipient.observe(getViewLifecycleOwner(), resolved -> {
        if (chip.isAttachedToWindow()) {
          chip.setAvatar(glideRequests, resolved, null);
          chip.setText(resolved.getShortDisplayName(chip.getContext()));
        }
      });
    }
  }

  private void setChipGroupVisibility(int visibility) {
    TransitionManager.beginDelayedTransition(constraintLayout, new AutoTransition().setDuration(CHIP_GROUP_REVEAL_DURATION_MS));

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(constraintLayout);
    constraintSet.setVisibility(R.id.chipGroupScrollContainer, visibility);
    constraintSet.applyTo(constraintLayout);
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  private void smoothScrollChipsToEnd() {
    int x = chipGroupScrollContainer.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR ? chipGroup.getWidth() : 0;
    chipGroupScrollContainer.smoothScrollTo(x, 0);
  }

  public interface OnContactSelectedListener {
    /** @return True if the contact is allowed to be selected, otherwise false. */
    boolean onBeforeContactSelected(Optional<RecipientId> recipientId, String number);
    void onContactDeselected(Optional<RecipientId> recipientId, String number);
  }

  public interface ListCallback {
    void onInvite();
    void onNewGroup(boolean forceV1);
  }

  public interface ScrollCallback {
    void onBeginScroll();
  }
}
