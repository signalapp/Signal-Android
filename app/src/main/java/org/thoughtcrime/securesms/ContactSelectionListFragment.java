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
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.concurrent.RxExtensions;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.contacts.ContactChipViewModel;
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode;
import org.thoughtcrime.securesms.contacts.HeaderAction;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.SelectedContacts;
import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchMediator;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchSortOrder;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository;
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameAciFetchResult;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
public final class ContactSelectionListFragment extends LoggingFragment {
  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ContactSelectionListFragment.class);

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 0;
  private static final int CHIP_GROUP_REVEAL_DURATION_MS = 150;

  public static final int NO_LIMIT = Integer.MAX_VALUE;

  public static final String DISPLAY_MODE       = "display_mode";
  public static final String REFRESHABLE        = "refreshable";
  public static final String RECENTS            = "recents";
  public static final String SELECTION_LIMITS   = "selection_limits";
  public static final String CURRENT_SELECTION  = "current_selection";
  public static final String HIDE_COUNT         = "hide_count";
  public static final String CAN_SELECT_SELF    = "can_select_self";
  public static final String DISPLAY_CHIPS      = "display_chips";
  public static final String RV_PADDING_BOTTOM  = "recycler_view_padding_bottom";
  public static final String RV_CLIP            = "recycler_view_clipping";
  public static final String INCLUDE_CHAT_TYPES = "include_chat_types";

  private ConstraintLayout                constraintLayout;
  private TextView                        emptyText;
  private OnContactSelectedListener       onContactSelectedListener;
  private SwipeRefreshLayout              swipeRefresh;
  private String                          cursorFilter;
  private RecyclerView                    recyclerView;
  private RecyclerViewFastScroller        fastScroller;
  private RecyclerView                    chipRecycler;
  private OnSelectionLimitReachedListener onSelectionLimitReachedListener;
  private MappingAdapter                  contactChipAdapter;
  private ContactChipViewModel            contactChipViewModel;
  private LifecycleDisposable             lifecycleDisposable;
  private HeaderActionProvider            headerActionProvider;
  private TextView                        headerActionView;
  private ContactSearchMediator           contactSearchMediator;

  @Nullable private NewConversationCallback              newConversationCallback;
  @Nullable private FindByCallback                       findByCallback;
  @Nullable private NewCallCallback                      newCallCallback;
  @Nullable private ScrollCallback                       scrollCallback;
  @Nullable private OnItemLongClickListener              onItemLongClickListener;
  private           SelectionLimits                      selectionLimit    = SelectionLimits.NO_LIMITS;
  private           Set<RecipientId>                     currentSelection;
  private           boolean                              isMulti;
  private           boolean                              canSelectSelf;
  private           boolean                              resetPositionOnCommit = false;

  private           ListClickListener                    listClickListener = new ListClickListener();
  @Nullable private SwipeRefreshLayout.OnRefreshListener onRefreshListener;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof NewConversationCallback) {
      newConversationCallback = (NewConversationCallback) context;
    }

    if (context instanceof FindByCallback) {
      findByCallback = (FindByCallback) context;
    }

    if (context instanceof NewCallCallback) {
      newCallCallback = (NewCallCallback) context;
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

    if (hasContactsPermissions(requireContext()) && !TextSecurePreferences.hasSuccessfullyRetrievedDirectory(getActivity())) {
        handleContactPermissionGranted();
    } else {
      requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      contactSearchMediator.refresh();
    }
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText                = view.findViewById(android.R.id.empty);
    recyclerView             = view.findViewById(R.id.recycler_view);
    swipeRefresh             = view.findViewById(R.id.swipe_refresh);
    fastScroller             = view.findViewById(R.id.fast_scroller);
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

      @Override
      public void onAnimationFinished(@NonNull RecyclerView.ViewHolder viewHolder) {
        recyclerView.setAlpha(1f);
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

    contactSearchMediator = new ContactSearchMediator(
        this,
        currentSelection.stream()
                        .map(r -> new ContactSearchKey.RecipientSearchKey(r, false))
                        .collect(java.util.stream.Collectors.toSet()),
        selectionLimit,
        new ContactSearchAdapter.DisplayOptions(
            isMulti,
            ContactSearchAdapter.DisplaySecondaryInformation.ALWAYS,
            newCallCallback != null,
            false
        ),
        this::mapStateToConfiguration,
        new ContactSearchMediator.SimpleCallbacks() {
          @Override
          public void onAdapterListCommitted(int size) {
            onLoadFinished(size);
          }
        },
        false,
        (context, fixedContacts, displayOptions, callbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks) -> new ContactSelectionListAdapter(
            context,
            fixedContacts,
            displayOptions,
            new ContactSelectionListAdapter.OnContactSelectionClick() {
              @Override
              public void onDismissFindContactsBannerClicked() {
                SignalStore.uiHints().markDismissedContactsPermissionBanner();
                if (onRefreshListener != null) {
                  onRefreshListener.onRefresh();
                }
              }

              @Override
              public void onFindContactsClicked() {
                requestContactPermissions();
              }

              @Override
              public void onRefreshContactsClicked() {
                if (onRefreshListener != null) {
                  setRefreshing(true);
                  onRefreshListener.onRefresh();
                }
              }

              @Override
              public void onNewGroupClicked() {
                newConversationCallback.onNewGroup(false);
              }

              @Override
              public void onFindByPhoneNumberClicked() {
                findByCallback.onFindByPhoneNumber();
              }

              @Override
              public void onFindByUsernameClicked() {
                findByCallback.onFindByUsername();
              }

              @Override
              public void onInviteToSignalClicked() {
                if (newConversationCallback != null) {
                  newConversationCallback.onInvite();
                }

                if (newCallCallback != null) {
                  newCallCallback.onInvite();
                }
              }

              @Override
              public void onStoryClicked(@NonNull View view1, @NonNull ContactSearchData.Story story, boolean isSelected) {
                throw new UnsupportedOperationException();
              }

              @Override
              public void onKnownRecipientClicked(@NonNull View view1, @NonNull ContactSearchData.KnownRecipient knownRecipient, boolean isSelected) {
                listClickListener.onItemClick(knownRecipient.getContactSearchKey());
              }

              @Override
              public void onExpandClicked(@NonNull ContactSearchData.Expand expand) {
                callbacks.onExpandClicked(expand);
              }

              @Override
              public void onUnknownRecipientClicked(@NonNull View view, @NonNull ContactSearchData.UnknownRecipient unknownRecipient, boolean isSelected) {
                listClickListener.onItemClick(unknownRecipient.getContactSearchKey());
              }

              @Override
              public void onChatTypeClicked(@NonNull View view, @NonNull ContactSearchData.ChatTypeRow chatTypeRow, boolean isSelected) {
                listClickListener.onItemClick(chatTypeRow.getContactSearchKey());
              }
            },
            (anchorView, data) -> listClickListener.onItemLongClick(anchorView, data.getContactSearchKey()),
            storyContextMenuCallbacks,
            new CallButtonClickCallbacks()

        ),
        new ContactSelectionListAdapter.ArbitraryRepository()
    );

    return view;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    constraintLayout = null;
    onRefreshListener = null;
  }

  public int getSelectedMembersSize() {
    return contactSearchMediator.getSelectedMembersSize();
  }

  private @NonNull Bundle safeArguments() {
    return getArguments() != null ? getArguments() : new Bundle();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public @NonNull List<SelectedContact> getSelectedContacts() {
    if (contactSearchMediator == null) {
      return Collections.emptyList();
    }

    return contactSearchMediator.getSelectedContacts()
                                .stream()
                                .map(ContactSearchKey::requireSelectedContact)
                                .collect(java.util.stream.Collectors.toList());
  }

  public int getSelectedContactsCount() {
    if (contactSearchMediator == null) {
      return 0;
    }

    return contactSearchMediator.getSelectedContacts().size();
  }

  public int getTotalMemberCount() {
    if (contactSearchMediator == null) {
      return 0;
    }

    return getSelectedContactsCount() + contactSearchMediator.getFixedContactsSize();
  }

  private Set<RecipientId> getCurrentSelection() {
    List<RecipientId> currentSelection = safeArguments().getParcelableArrayList(CURRENT_SELECTION);
    if (currentSelection == null) {
      currentSelection = requireActivity().getIntent().getParcelableArrayListExtra(CURRENT_SELECTION);
    }

    return currentSelection == null ? Collections.emptySet()
                                    : Collections.unmodifiableSet(new HashSet<>(currentSelection));
  }

  public boolean isMulti() {
    return isMulti;
  }

  private void requestContactPermissions() {
    Permissions.with(this)
               .request(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
               .ifNecessary()
               .onAllGranted(() -> {
                 recyclerView.setAlpha(0.5f);
                 if (!TextSecurePreferences.hasSuccessfullyRetrievedDirectory(getActivity())) {
                   handleContactPermissionGranted();
                 } else {
                   contactSearchMediator.refresh();
                   if (onRefreshListener != null) {
                     swipeRefresh.setRefreshing(true);
                     onRefreshListener.onRefresh();
                   }
                 }
               })
               .onAnyDenied(() -> contactSearchMediator.refresh())
               .withPermanentDenialDialog(getString(R.string.ContactSelectionListFragment_signal_requires_the_contacts_permission_in_order_to_display_your_contacts), null, R.string.ContactSelectionListFragment_allow_access_contacts, R.string.ContactSelectionListFragment_to_find_people, getParentFragmentManager())
               .execute();
  }

  private void initializeCursor() {
    recyclerView.addItemDecoration(new LetterHeaderDecoration(requireContext(), this::hideLetterHeaders));
    recyclerView.setAdapter(contactSearchMediator.getAdapter());
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

  public void setQueryFilter(String filter) {
    if (Objects.equals(filter, this.cursorFilter)) {
      return;
    }

    this.resetPositionOnCommit = true;
    this.cursorFilter          = filter;

    contactSearchMediator.onFilterChanged(filter);
  }

  public void resetQueryFilter() {
    setQueryFilter(null);

    this.resetPositionOnCommit = true;

    swipeRefresh.setRefreshing(false);
  }

  public boolean hasQueryFilter() {
    return !TextUtils.isEmpty(cursorFilter);
  }

  public void setRefreshing(boolean refreshing) {
    swipeRefresh.setRefreshing(refreshing);
  }

  public void reset() {
    contactSearchMediator.clearSelection();
    fastScroller.setVisibility(View.GONE);
    headerActionView.setVisibility(View.GONE);
  }

  private void onLoadFinished(int count) {
    if (resetPositionOnCommit) {
      resetPositionOnCommit = false;
      recyclerView.scrollToPosition(0);
    }

    swipeRefresh.setVisibility(View.VISIBLE);

    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
    boolean useFastScroller = count > 20;
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

  private boolean shouldDisplayRecents() {
    return safeArguments().getBoolean(RECENTS, requireActivity().getIntent().getBooleanExtra(RECENTS, false));
  }

  @SuppressLint("StaticFieldLeak")
  private void handleContactPermissionGranted() {
    final Context context = requireContext();

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected void onPreExecute() {
        if (onRefreshListener != null) {
          setRefreshing(true);
          onRefreshListener.onRefresh();
        }
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
          reset();
        } else {
          Context context = getContext();
          if (context != null) {
            Toast.makeText(getContext(), R.string.ContactSelectionListFragment_error_retrieving_contacts_check_your_network_connection, Toast.LENGTH_LONG).show();
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
  public void markSelected(@NonNull Set<RecipientId> contacts) {
    if (contacts.isEmpty()) {
      return;
    }

    Set<SelectedContact> toMarkSelected = contacts.stream()
                                                  .filter(r -> !contactSearchMediator.getSelectedContacts()
                                                                                     .contains(new ContactSearchKey.RecipientSearchKey(r, false)))
                                                  .map(SelectedContact::forRecipientId)
                                                  .collect(java.util.stream.Collectors.toSet());

    if (toMarkSelected.isEmpty()) {
      return;
    }

    for (final SelectedContact selectedContact : toMarkSelected) {
      markContactSelected(selectedContact);
    }
  }

  public void addRecipientToSelectionIfAble(@NonNull RecipientId recipientId) {
    listClickListener.onItemClick(new ContactSearchKey.RecipientSearchKey(recipientId, false));
  }

  private class ListClickListener {
    public void onItemClick(ContactSearchKey contact) {
      boolean         isUnknown       = contact instanceof ContactSearchKey.UnknownRecipientKey;
      SelectedContact selectedContact = contact.requireSelectedContact();

      if (!canSelectSelf && !selectedContact.hasUsername() && Recipient.self().getId().equals(selectedContact.getOrCreateRecipientId(requireContext()))) {
        Toast.makeText(requireContext(), R.string.ContactSelectionListFragment_you_do_not_need_to_add_yourself_to_the_group, Toast.LENGTH_SHORT).show();
        return;
      }

      if (selectedContact.hasChatType() && !contactSearchMediator.getSelectedContacts().contains(selectedContact.toContactSearchKey())) {
        if (onContactSelectedListener != null) {
          onContactSelectedListener.onBeforeContactSelected(true, Optional.empty(), null, Optional.of(selectedContact.getChatType()), allowed -> {
            if (allowed) {
              markContactSelected(selectedContact);
            }
          });
        }
        return;
      } else if (selectedContact.hasChatType()) {
        markContactUnselected(selectedContact);
        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(Optional.ofNullable(selectedContact.getRecipientId()), selectedContact.getNumber(), Optional.of(selectedContact.getChatType()));
        }
        return;
      }

      if (!isMulti || !contactSearchMediator.getSelectedContacts().contains(selectedContact.toContactSearchKey())) {
        if (selectionHardLimitReached()) {
          if (onSelectionLimitReachedListener != null) {
            onSelectionLimitReachedListener.onHardLimitReached(selectionLimit.getHardLimit());
          } else {
            GroupLimitDialog.showHardLimitMessage(requireContext());
          }
          return;
        }

        if (contact instanceof ContactSearchKey.UnknownRecipientKey && ((ContactSearchKey.UnknownRecipientKey) contact).getSectionKey() == ContactSearchConfiguration.SectionKey.USERNAME) {
          String      username      = ((ContactSearchKey.UnknownRecipientKey) contact).getQuery();
          AlertDialog loadingDialog = SimpleProgressDialog.show(requireContext());

          SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            try {
              return RxExtensions.safeBlockingGet(UsernameRepository.fetchAciForUsername(UsernameUtil.sanitizeUsernameFromSearch(username)));
            } catch (InterruptedException e) {
              Log.w(TAG, "Interrupted?", e);
              return UsernameAciFetchResult.NetworkError.INSTANCE;
            }
          }, result  -> {
            loadingDialog.dismiss();

            // TODO Could be more specific with errors
            if (result instanceof UsernameAciFetchResult.Success success) {
              Recipient       recipient = Recipient.externalUsername(success.getAci(), username);
              SelectedContact selected  = SelectedContact.forUsername(recipient.getId(), username);

              if (onContactSelectedListener != null) {
                onContactSelectedListener.onBeforeContactSelected(true, Optional.of(recipient.getId()), null, Optional.empty(), allowed -> {
                  if (allowed) {
                    markContactSelected(selected);
                  }
                });
              } else {
                markContactSelected(selected);
              }
            } else {
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle(R.string.ContactSelectionListFragment_username_not_found)
                  .setMessage(getString(R.string.ContactSelectionListFragment_s_is_not_a_signal_user, username))
                  .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                  .show();
            }
          });
        } else {
          if (onContactSelectedListener != null) {
            onContactSelectedListener.onBeforeContactSelected(
                isUnknown,
                Optional.ofNullable(selectedContact.getRecipientId()),
                selectedContact.getNumber(),
                Optional.empty(),
                allowed -> {
              if (allowed) {
                markContactSelected(selectedContact);
              }
            });
          } else {
            markContactSelected(selectedContact);
          }
        }
      } else {
        markContactUnselected(selectedContact);

        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(Optional.ofNullable(selectedContact.getRecipientId()), selectedContact.getNumber(), Optional.empty());
        }
      }
    }

    public boolean onItemLongClick(View anchorView, ContactSearchKey item) {
      if (onItemLongClickListener != null) {
        return onItemLongClickListener.onLongClick(anchorView, item, recyclerView);
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

  public void markContactSelected(@NonNull SelectedContact selectedContact) {
    contactSearchMediator.setKeysSelected(Collections.singleton(selectedContact.toContactSearchKey()));
    if (isMulti) {
      addChipForSelectedContact(selectedContact);
    }
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    contactSearchMediator.setKeysNotSelected(Collections.singleton(selectedContact.toContactSearchKey()));
    contactChipViewModel.remove(selectedContact);

    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void handleSelectedContactsChanged(@NonNull List<SelectedContacts.Model<?>> selectedContacts) {
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
    if (selectedContact.hasChatType()) {
      contactChipViewModel.add(selectedContact);
    } else {
      SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                     () -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                     resolved -> contactChipViewModel.add(selectedContact));
    }
  }

  private Unit onChipCloseIconClicked(SelectedContacts.Model<?> model) {
    markContactUnselected(model.getSelectedContact());
    if (onContactSelectedListener != null) {
      if (model instanceof SelectedContacts.ChatTypeModel) {
        onContactSelectedListener.onContactDeselected(Optional.empty(), null, Optional.of(model.getSelectedContact().getChatType()));
      } else {
        onContactSelectedListener.onContactDeselected(Optional.of(((SelectedContacts.RecipientModel) model).getRecipient().getId()), ((SelectedContacts.RecipientModel) model).getRecipient().getE164().orElse(null), Optional.empty());
      }
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
    this.onRefreshListener = onRefreshListener;
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipRecycler) ? chipRecycler.getWidth() : 0;
    chipRecycler.smoothScrollBy(x, 0);
  }

  private @NonNull ContactSearchConfiguration mapStateToConfiguration(@NonNull ContactSearchState contactSearchState) {
    int displayMode = safeArguments().getInt(DISPLAY_MODE, requireActivity().getIntent().getIntExtra(DISPLAY_MODE, ContactSelectionDisplayMode.FLAG_ALL));

    boolean includeRecents             = safeArguments().getBoolean(RECENTS, requireActivity().getIntent().getBooleanExtra(RECENTS, false));
    boolean includePushContacts        = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_PUSH);
    boolean includeSmsContacts         = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_SMS);
    boolean includeActiveGroups        = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS);
    boolean includeInactiveGroups      = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS);
    boolean includeSelf                = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_SELF);
    boolean includeV1Groups            = !flagSet(displayMode, ContactSelectionDisplayMode.FLAG_HIDE_GROUPS_V1);
    boolean includeNew                 = !flagSet(displayMode, ContactSelectionDisplayMode.FLAG_HIDE_NEW);
    boolean includeRecentsHeader       = !flagSet(displayMode, ContactSelectionDisplayMode.FLAG_HIDE_RECENT_HEADER);
    boolean includeGroupsAfterContacts = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_GROUPS_AFTER_CONTACTS);
    boolean blocked                    = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_BLOCK);
    boolean includeGroupMembers        = flagSet(displayMode, ContactSelectionDisplayMode.FLAG_GROUP_MEMBERS);
    boolean includeChatTypes           = safeArguments().getBoolean(INCLUDE_CHAT_TYPES);
    boolean hasQuery                   = !TextUtils.isEmpty(contactSearchState.getQuery());

    ContactSearchConfiguration.TransportType        transportType = resolveTransportType(includePushContacts, includeSmsContacts);
    ContactSearchConfiguration.Section.Recents.Mode mode          = resolveRecentsMode(transportType, includeActiveGroups);
    ContactSearchConfiguration.NewRowMode           newRowMode    = resolveNewRowMode(blocked, includeActiveGroups);

    return ContactSearchConfiguration.build(builder -> {
      builder.setQuery(contactSearchState.getQuery());

      if (newConversationCallback != null                               &&
          !hasContactsPermissions(requireContext())                     &&
          !SignalStore.uiHints().getDismissedContactsPermissionBanner() &&
          !hasQuery) {
        builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.FIND_CONTACTS_BANNER.getCode());
      }

      if (newConversationCallback != null && !hasQuery) {
        builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.NEW_GROUP.getCode());
      }

      if (findByCallback != null && !hasQuery) {
        builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.FIND_BY_USERNAME.getCode());
        builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.FIND_BY_PHONE_NUMBER.getCode());
      }

      if (includeChatTypes && !hasQuery) {
        builder.addSection(new ContactSearchConfiguration.Section.ChatTypes(true, null));
      }

      if (transportType != null) {
        if (!hasQuery && includeRecents) {
          builder.addSection(new ContactSearchConfiguration.Section.Recents(
              25,
              mode,
              includeInactiveGroups,
              includeV1Groups,
              includeSmsContacts,
              includeSelf,
              includeRecentsHeader,
              null
          ));
        }

        boolean hideHeader = newCallCallback != null || (newConversationCallback != null && !hasQuery);
        builder.addSection(new ContactSearchConfiguration.Section.Individuals(
            includeSelf,
            transportType,
            !hideHeader,
            null,
            !hideLetterHeaders(),
            newConversationCallback != null ? ContactSearchSortOrder.RECENCY : ContactSearchSortOrder.NATURAL
        ));
      }

      if ((includeGroupsAfterContacts || hasQuery) && includeActiveGroups) {
        builder.addSection(new ContactSearchConfiguration.Section.Groups(
            includeSmsContacts,
            includeV1Groups,
            includeInactiveGroups,
            false,
            ContactSearchSortOrder.NATURAL,
            false,
            true,
            null
        ));
      }

      if (hasQuery && includeGroupMembers) {
        builder.addSection(new ContactSearchConfiguration.Section.GroupMembers());
      }

      if (includeNew) {
        builder.phone(newRowMode);
        builder.username(newRowMode);
      }

      if ((newCallCallback != null || newConversationCallback != null)) {
        addMoreSection(builder);
        builder.withEmptyState(emptyBuilder -> {
          emptyBuilder.addSection(ContactSearchConfiguration.Section.Empty.INSTANCE);
          addMoreSection(emptyBuilder);
          return Unit.INSTANCE;
        });
      }

      return Unit.INSTANCE;
    });
  }

  private boolean hasContactsPermissions(@NonNull Context context) {
    return Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS);
  }

  private void addMoreSection(@NonNull ContactSearchConfiguration.Builder builder) {
    builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.MORE_HEADING.getCode());
    if (hasContactsPermissions(requireContext())) {
      builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.REFRESH_CONTACTS.getCode());
    } else if (SignalStore.uiHints().getDismissedContactsPermissionBanner()) {
      builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.FIND_CONTACTS.getCode());
    }
    builder.arbitrary(ContactSelectionListAdapter.ArbitraryRepository.ArbitraryRow.INVITE_TO_SIGNAL.getCode());
  }

  private static @Nullable ContactSearchConfiguration.TransportType resolveTransportType(boolean includePushContacts, boolean includeSmsContacts) {
    if (includePushContacts && includeSmsContacts) {
      return ContactSearchConfiguration.TransportType.ALL;
    } else if (includePushContacts) {
      return ContactSearchConfiguration.TransportType.PUSH;
    } else if (includeSmsContacts) {
      return ContactSearchConfiguration.TransportType.SMS;
    } else {
      return null;
    }
  }

  private static @NonNull ContactSearchConfiguration.Section.Recents.Mode resolveRecentsMode(ContactSearchConfiguration.TransportType transportType, boolean includeGroupContacts) {
    if (transportType != null && includeGroupContacts) {
      return ContactSearchConfiguration.Section.Recents.Mode.ALL;
    } else if (includeGroupContacts) {
      return ContactSearchConfiguration.Section.Recents.Mode.GROUPS;
    } else {
      return ContactSearchConfiguration.Section.Recents.Mode.INDIVIDUALS;
    }
  }

  private @NonNull ContactSearchConfiguration.NewRowMode resolveNewRowMode(boolean isBlocked, boolean isActiveGroups) {
    if (isBlocked) {
      return ContactSearchConfiguration.NewRowMode.BLOCK;
    } else if (newCallCallback != null) {
      return ContactSearchConfiguration.NewRowMode.NEW_CALL;
    } else if (isActiveGroups) {
      return ContactSearchConfiguration.NewRowMode.NEW_CONVERSATION;
    } else {
      return ContactSearchConfiguration.NewRowMode.ADD_TO_GROUP;
    }
  }

  private static boolean flagSet(int mode, int flag) {
    return (mode & flag) > 0;
  }

  private class CallButtonClickCallbacks implements ContactSearchAdapter.CallButtonClickCallbacks {
    @Override
    public void onVideoCallButtonClicked(@NonNull Recipient recipient) {
      CommunicationActions.startVideoCall(ContactSelectionListFragment.this, recipient, () -> {
        YouAreAlreadyInACallSnackbar.show(requireView());
      });
    }

    @Override
    public void onAudioCallButtonClicked(@NonNull Recipient recipient) {
      CommunicationActions.startVoiceCall(ContactSelectionListFragment.this, recipient, () -> {
        YouAreAlreadyInACallSnackbar.show(requireView());
      });
    }
  }

  public interface OnContactSelectedListener {
    /**
     * Provides an opportunity to disallow selecting an item. Call the callback with false to disallow, or true to allow it.
     */
    void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType, @NonNull Consumer<Boolean> callback);

    void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType);

    void onSelectionChanged();
  }

  public interface OnSelectionLimitReachedListener {
    void onSuggestedLimitReached(int limit);

    void onHardLimitReached(int limit);
  }

  public interface NewConversationCallback {
    void onInvite();

    void onNewGroup(boolean forceV1);
  }

  public interface FindByCallback {
    void onFindByUsername();

    void onFindByPhoneNumber();
  }

  public interface NewCallCallback {
    void onInvite();
  }

  public interface ScrollCallback {
    void onBeginScroll();
  }

  public interface HeaderActionProvider {
    @NonNull HeaderAction getHeaderAction();
  }

  public interface OnItemLongClickListener {
    boolean onLongClick(View anchorView, ContactSearchKey contactSearchKey, RecyclerView recyclerView);
  }
}
