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
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.contacts.AbstractContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.ContactChipViewModel;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.HeaderAction;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.SelectedContacts;
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sharing.ShareContact;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.Disposable;
import kotlin.Unit;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 */
public final class ContactSelectionListFragment extends LoggingFragment
    implements LoaderManager.LoaderCallbacks<Cursor>
{
  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ContactSelectionListFragment.class);

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 0;
  private static final int CHIP_GROUP_REVEAL_DURATION_MS = 150;

  public static final int NO_LIMIT = Integer.MAX_VALUE;

  public static final String DISPLAY_MODE      = "display_mode";
  public static final String REFRESHABLE       = "refreshable";
  public static final String RECENTS           = "recents";
  public static final String SELECTION_LIMITS  = "selection_limits";
  public static final String CURRENT_SELECTION = "current_selection";
  public static final String HIDE_COUNT        = "hide_count";
  public static final String CAN_SELECT_SELF   = "can_select_self";
  public static final String DISPLAY_CHIPS     = "display_chips";
  public static final String RV_PADDING_BOTTOM = "recycler_view_padding_bottom";
  public static final String RV_CLIP           = "recycler_view_clipping";

  private ConstraintLayout                            constraintLayout;
  private TextView                                    emptyText;
  private OnContactSelectedListener                   onContactSelectedListener;
  private SwipeRefreshLayout                          swipeRefresh;
  private View                                        showContactsLayout;
  private Button                                      showContactsButton;
  private TextView                                    showContactsDescription;
  private ProgressWheel                               showContactsProgress;
  private String                                      cursorFilter;
  private RecyclerView                                recyclerView;
  private RecyclerViewFastScroller                    fastScroller;
  private ContactSelectionListAdapter                 cursorRecyclerViewAdapter;
  private RecyclerView                                chipRecycler;
  private OnSelectionLimitReachedListener             onSelectionLimitReachedListener;
  private AbstractContactsCursorLoaderFactoryProvider cursorFactoryProvider;
  private MappingAdapter                              contactChipAdapter;
  private ContactChipViewModel                        contactChipViewModel;
  private LifecycleDisposable                         lifecycleDisposable;
  private HeaderActionProvider                        headerActionProvider;
  private TextView                                    headerActionView;

  @Nullable private FixedViewsAdapter       headerAdapter;
  @Nullable private FixedViewsAdapter       footerAdapter;
  @Nullable private ListCallback            listCallback;
  @Nullable private ScrollCallback          scrollCallback;
  @Nullable private OnItemLongClickListener onItemLongClickListener;
  private           GlideRequests           glideRequests;
  private           SelectionLimits         selectionLimit = SelectionLimits.NO_LIMITS;
  private           Set<RecipientId>        currentSelection;
  private           boolean                 isMulti;
  private           boolean                 hideCount;
  private           boolean                 canSelectSelf;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof ListCallback) {
      listCallback = (ListCallback) context;
    }

    if (getParentFragment() instanceof ScrollCallback) {
      scrollCallback = (ScrollCallback) getParentFragment();
    }

    if (context instanceof ScrollCallback) {
      scrollCallback = (ScrollCallback) context;
    }

    if (getParentFragment() instanceof OnContactSelectedListener) {
      onContactSelectedListener = (OnContactSelectedListener) getParentFragment();
    }

    if (context instanceof OnContactSelectedListener) {
      onContactSelectedListener = (OnContactSelectedListener) context;
    }

    if (context instanceof OnSelectionLimitReachedListener) {
      onSelectionLimitReachedListener = (OnSelectionLimitReachedListener) context;
    }

    if (getParentFragment() instanceof OnSelectionLimitReachedListener) {
      onSelectionLimitReachedListener = (OnSelectionLimitReachedListener) getParentFragment();
    }

    if (context instanceof AbstractContactsCursorLoaderFactoryProvider) {
      cursorFactoryProvider = (AbstractContactsCursorLoaderFactoryProvider) context;
    }

    if (getParentFragment() instanceof AbstractContactsCursorLoaderFactoryProvider) {
      cursorFactoryProvider = (AbstractContactsCursorLoaderFactoryProvider) getParentFragment();
    }

    if (context instanceof HeaderActionProvider) {
      headerActionProvider = (HeaderActionProvider) context;
    }

    if (getParentFragment() instanceof HeaderActionProvider) {
      headerActionProvider = (HeaderActionProvider) getParentFragment();
    }

    if (context instanceof OnItemLongClickListener) {
      onItemLongClickListener = (OnItemLongClickListener) context;
    }

    if (getParentFragment() instanceof OnItemLongClickListener) {
      onItemLongClickListener = (OnItemLongClickListener) getParentFragment();
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

                 if (safeArguments().getBoolean(RECENTS, activity.getIntent().getBooleanExtra(RECENTS, false))) {
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
    chipRecycler             = view.findViewById(R.id.chipRecycler);
    constraintLayout         = view.findViewById(R.id.container);
    headerActionView         = view.findViewById(R.id.header_action);

    final LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());

    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setItemAnimator(new DefaultItemAnimator() {
      @Override
      public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
      }
    });

    contactChipViewModel = new ViewModelProvider(this).get(ContactChipViewModel.class);
    contactChipAdapter   = new MappingAdapter();
    lifecycleDisposable  = new LifecycleDisposable();

    lifecycleDisposable.bindTo(getViewLifecycleOwner());
    SelectedContacts.register(contactChipAdapter, this::onChipCloseIconClicked);
    chipRecycler.setAdapter(contactChipAdapter);

    Disposable disposable = contactChipViewModel.getState().subscribe(this::handleSelectedContactsChanged);

    lifecycleDisposable.add(disposable);

    Intent intent    = requireActivity().getIntent();
    Bundle arguments = safeArguments();

    int     recyclerViewPadBottom = arguments.getInt(RV_PADDING_BOTTOM, intent.getIntExtra(RV_PADDING_BOTTOM, -1));
    boolean recyclerViewClipping  = arguments.getBoolean(RV_CLIP, intent.getBooleanExtra(RV_CLIP, true));

    if (recyclerViewPadBottom != -1) {
      ViewUtil.setPaddingBottom(recyclerView, recyclerViewPadBottom);
    }

    recyclerView.setClipToPadding(recyclerViewClipping);

    boolean isRefreshable = arguments.getBoolean(REFRESHABLE, intent.getBooleanExtra(REFRESHABLE, true));
    swipeRefresh.setNestedScrollingEnabled(isRefreshable);
    swipeRefresh.setEnabled(isRefreshable);

    hideCount      = arguments.getBoolean(HIDE_COUNT, intent.getBooleanExtra(HIDE_COUNT, false));
    selectionLimit = arguments.getParcelable(SELECTION_LIMITS);
    if (selectionLimit == null) {
      selectionLimit = intent.getParcelableExtra(SELECTION_LIMITS);
    }
    isMulti       = selectionLimit != null;
    canSelectSelf = arguments.getBoolean(CAN_SELECT_SELF, intent.getBooleanExtra(CAN_SELECT_SELF, !isMulti));

    if (!isMulti) {
      selectionLimit = SelectionLimits.NO_LIMITS;
    }

    currentSelection = getCurrentSelection();

    final HeaderAction headerAction;
    if (headerActionProvider != null) {
      headerAction = headerActionProvider.getHeaderAction();

      headerActionView.setEnabled(true);
      headerActionView.setText(headerAction.getLabel());
      headerActionView.setCompoundDrawablesRelativeWithIntrinsicBounds(headerAction.getIcon(), 0, 0, 0);
      headerActionView.setOnClickListener(v -> headerAction.getAction().run());
      recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

        private final Rect bounds = new Rect();

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (hideLetterHeaders()) {
            return;
          }

          int firstPosition = layoutManager.findFirstVisibleItemPosition();
          if (firstPosition == 0) {
            View firstChild = recyclerView.getChildAt(0);
            recyclerView.getDecoratedBoundsWithMargins(firstChild, bounds);
            headerActionView.setTranslationY(bounds.top);
          }
        }
      });
    } else {
      headerActionView.setEnabled(false);
    }

    return view;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    constraintLayout = null;
  }

  private @NonNull Bundle safeArguments() {
    return getArguments() != null ? getArguments() : new Bundle();
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

  public int getTotalMemberCount() {
    if (cursorRecyclerViewAdapter == null) {
      return 0;
    }

    return cursorRecyclerViewAdapter.getSelectedContactsCount() + cursorRecyclerViewAdapter.getCurrentContactsCount();
  }

  private Set<RecipientId> getCurrentSelection() {
    List<RecipientId> currentSelection = safeArguments().getParcelableArrayList(CURRENT_SELECTION);
    if (currentSelection == null) {
      currentSelection = requireActivity().getIntent().getParcelableArrayListExtra(CURRENT_SELECTION);
    }

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
                                                                currentSelection,
                                                                safeArguments().getInt(ContactSelectionArguments.CHECKBOX_RESOURCE, R.drawable.contact_selection_checkbox));

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

    recyclerView.addItemDecoration(new LetterHeaderDecoration(requireContext(), this::hideLetterHeaders));
    recyclerView.setAdapter(concatenateAdapter);
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

    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private boolean hideLetterHeaders() {
    return hasQueryFilter() || shouldDisplayRecents();
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

  public void setRecyclerViewPaddingBottom(@Px int paddingBottom) {
    ViewUtil.setPaddingBottom(recyclerView, paddingBottom);
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int id, Bundle args) {
    FragmentActivity activity       = requireActivity();
    int              displayMode    = safeArguments().getInt(DISPLAY_MODE, activity.getIntent().getIntExtra(DISPLAY_MODE, DisplayMode.FLAG_ALL));
    boolean          displayRecents = shouldDisplayRecents();

    if (cursorFactoryProvider != null) {
      return cursorFactoryProvider.get().create();
    } else {
      return new ContactsCursorLoader.Factory(activity, displayMode, cursorFilter, displayRecents).create();
    }
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

    if (headerActionView.isEnabled() && !hasQueryFilter()) {
      headerActionView.setVisibility(View.VISIBLE);
    } else {
      headerActionView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    cursorRecyclerViewAdapter.changeCursor(null);
    fastScroller.setVisibility(View.GONE);
    headerActionView.setVisibility(View.GONE);
  }

  private boolean shouldDisplayRecents() {
    return safeArguments().getBoolean(RECENTS, requireActivity().getIntent().getBooleanExtra(RECENTS, false));
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
          ContactDiscovery.refreshAll(context, false);
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

  /**
   * Allows the caller to submit a list of recipients to be marked selected. Useful for when a screen needs to load preselected
   * entries in the background before setting them in the adapter.
   *
   * @param contacts List of the contacts to select. This will not overwrite the current selection, but append to it.
   */
  public void markSelected(@NonNull Set<ShareContact> contacts) {
    if (contacts.isEmpty()) {
      return;
    }

    Set<SelectedContact> toMarkSelected = contacts.stream()
                                                  .map(contact -> {
                                                    if (contact.getRecipientId().isPresent()) {
                                                      return SelectedContact.forRecipientId(contact.getRecipientId().get());
                                                    } else {
                                                      return SelectedContact.forPhone(null, contact.getNumber());
                                                    }
                                                  })
                                                  .filter(c -> !cursorRecyclerViewAdapter.isSelectedContact(c))
                                                  .collect(java.util.stream.Collectors.toSet());

    if (toMarkSelected.isEmpty()) {
      return;
    }

    for (final SelectedContact selectedContact : toMarkSelected) {
      markContactSelected(selectedContact);
    }

    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount());
  }

  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {
      SelectedContact selectedContact = contact.isUsernameType() ? SelectedContact.forUsername(contact.getRecipientId().orElse(null), contact.getNumber())
                                                                 : SelectedContact.forPhone(contact.getRecipientId().orElse(null), contact.getNumber());

      if (!canSelectSelf && !selectedContact.hasUsername() && Recipient.self().getId().equals(selectedContact.getOrCreateRecipientId(requireContext()))) {
        Toast.makeText(requireContext(), R.string.ContactSelectionListFragment_you_do_not_need_to_add_yourself_to_the_group, Toast.LENGTH_SHORT).show();
        return;
      }

      if (!isMulti || !cursorRecyclerViewAdapter.isSelectedContact(selectedContact)) {
        if (selectionHardLimitReached()) {
          if (onSelectionLimitReachedListener != null) {
            onSelectionLimitReachedListener.onHardLimitReached(selectionLimit.getHardLimit());
          } else {
            GroupLimitDialog.showHardLimitMessage(requireContext());
          }
          return;
        }

        if (contact.isUsernameType()) {
          AlertDialog loadingDialog = SimpleProgressDialog.show(requireContext());

          SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            return UsernameUtil.fetchAciForUsername(contact.getNumber());
          }, uuid -> {
            loadingDialog.dismiss();
            if (uuid.isPresent()) {
              Recipient       recipient = Recipient.externalUsername(uuid.get(), contact.getNumber());
              SelectedContact selected  = SelectedContact.forUsername(recipient.getId(), contact.getNumber());

              if (onContactSelectedListener != null) {
                onContactSelectedListener.onBeforeContactSelected(Optional.of(recipient.getId()), null, allowed -> {
                  if (allowed) {
                    markContactSelected(selected);
                    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
                  }
                });
              } else {
                markContactSelected(selected);
                cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
              }
            } else {
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle(R.string.ContactSelectionListFragment_username_not_found)
                  .setMessage(getString(R.string.ContactSelectionListFragment_s_is_not_a_signal_user, contact.getNumber()))
                  .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                  .show();
            }
          });
        } else {
          if (onContactSelectedListener != null) {
            onContactSelectedListener.onBeforeContactSelected(contact.getRecipientId(), contact.getNumber(), allowed -> {
              if (allowed) {
                markContactSelected(selectedContact);
                cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
              }
            });
          } else {
            markContactSelected(selectedContact);
            cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
          }
        }
      } else {
        markContactUnselected(selectedContact);
        cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);

        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(contact.getRecipientId(), contact.getNumber());
        }
      }
    }

    @Override
    public boolean onItemLongClick(ContactSelectionListItem item) {
      if (onItemLongClickListener != null) {
        return onItemLongClickListener.onLongClick(item, recyclerView);
      } else {
        return false;
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
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    cursorRecyclerViewAdapter.removeFromSelectedContacts(selectedContact);
    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
    contactChipViewModel.remove(selectedContact);

    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void handleSelectedContactsChanged(@NonNull List<SelectedContacts.Model> selectedContacts) {
    contactChipAdapter.submitList(new MappingModelList(selectedContacts), this::smoothScrollChipsToEnd);

    if (selectedContacts.isEmpty()) {
      setChipGroupVisibility(ConstraintSet.GONE);
    } else {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }

    if (selectionWarningLimitReachedExactly()) {
      if (onSelectionLimitReachedListener != null) {
        onSelectionLimitReachedListener.onSuggestedLimitReached(selectionLimit.getRecommendedLimit());
      } else {
        GroupLimitDialog.showRecommendedLimitMessage(requireContext());
      }
    }
  }

  private void addChipForSelectedContact(@NonNull SelectedContact selectedContact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                   resolved -> contactChipViewModel.add(selectedContact));
  }

  private Unit onChipCloseIconClicked(SelectedContacts.Model model) {
    markContactUnselected(model.getSelectedContact());
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onContactDeselected(Optional.of(model.getRecipient().getId()), model.getRecipient().getE164().orElse(null));
    }

    return Unit.INSTANCE;
  }

  private int getChipCount() {
    int count = contactChipViewModel.getCount() - CHIP_GROUP_EMPTY_CHILD_COUNT;
    if (count < 0) throw new AssertionError();
    return count;
  }

  private void setChipGroupVisibility(int visibility) {
    if (!safeArguments().getBoolean(DISPLAY_CHIPS, requireActivity().getIntent().getBooleanExtra(DISPLAY_CHIPS, true))) {
      return;
    }

    AutoTransition transition = new AutoTransition();
    transition.setDuration(CHIP_GROUP_REVEAL_DURATION_MS);
    transition.excludeChildren(recyclerView, true);
    transition.excludeTarget(recyclerView, true);
    TransitionManager.beginDelayedTransition(constraintLayout, transition);

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(constraintLayout);
    constraintSet.setVisibility(R.id.chipRecycler, visibility);
    constraintSet.applyTo(constraintLayout);
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipRecycler) ? chipRecycler.getWidth() : 0;
    chipRecycler.smoothScrollBy(x, 0);
  }

  public interface OnContactSelectedListener {
    /**
     * Provides an opportunity to disallow selecting an item. Call the callback with false to disallow, or true to allow it.
     */
    void onBeforeContactSelected(@NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Consumer<Boolean> callback);

    void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number);

    void onSelectionChanged();
  }

  public interface OnSelectionLimitReachedListener {
    void onSuggestedLimitReached(int limit);

    void onHardLimitReached(int limit);
  }

  public interface ListCallback {
    void onInvite();

    void onNewGroup(boolean forceV1);
  }

  public interface ScrollCallback {
    void onBeginScroll();
  }

  public interface HeaderActionProvider {
    @NonNull HeaderAction getHeaderAction();
  }

  public interface OnItemLongClickListener {
    boolean onLongClick(ContactSelectionListItem contactSelectionListItem, RecyclerView recyclerView);
  }

  public interface AbstractContactsCursorLoaderFactoryProvider {
    @NonNull AbstractContactsCursorLoader.Factory get();
  }
}
