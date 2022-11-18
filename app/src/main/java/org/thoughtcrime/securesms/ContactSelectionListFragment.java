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
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.MyEditText;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.components.recyclerview.ToolbarShadowAnimationHelper;
import org.thoughtcrime.securesms.contacts.AbstractContactsCursorLoader;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.contacts.ContactChip;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
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
import org.thoughtcrime.securesms.sharing.ShareActivity;
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
import java.util.Set;
import java.util.function.Consumer;

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

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 1;
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
  private TextView                                    showContactsTextview;
  private TextView                                    showContactsDescription;
  private RelativeLayout                              rlContainer;
  private ProgressWheel                               showContactsProgress;
  private String                                      cursorFilter;
  private RecyclerView                                recyclerView;
  private RecyclerViewFastScroller                    fastScroller;
  private ContactSelectionListAdapter                 cursorRecyclerViewAdapter;
  private ChipGroup                                   chipGroup;
  private HorizontalScrollView                        chipGroupScrollContainer;
  private OnSelectionLimitReachedListener             onSelectionLimitReachedListener;
  private AbstractContactsCursorLoaderFactoryProvider cursorFactoryProvider;
  private View.OnClickListener                        shareConfirmClickListener;
  private boolean                                     isSharing = false;
  private View                                        shadowView;
  private ToolbarShadowAnimationHelper                toolbarShadowAnimationHelper;

  @Nullable private FixedViewsAdapter                 headerAdapter;
  @Nullable private FixedViewsAdapter                 footerAdapter;
  @Nullable private ListCallback                      listCallback;
  @Nullable private ScrollCallback                    scrollCallback;
            private GlideRequests                     glideRequests;
            private SelectionLimits                   selectionLimit   = SelectionLimits.NO_LIMITS;
            private Set<RecipientId>                  currentSelection;
            private boolean                           isMulti;
            private boolean                           hideCount;
            private boolean                           canSelectSelf;

  public int mFocusHeight;
  public int mNormalHeight;
  public int mNormalPaddingX;
  public int mFocusPaddingX;
  public int mFocusTextSize;
  public int mNormalTextSize;
  private int marginTop = 76;
  private boolean isScrollUp = false;

  @Nullable
  private FixedViewsAdapter footer1Adapter, footer2Adapter,searchAdapter, applyAdapter;
  @Nullable
  private RefreshCallback refreshCallback;
  @Nullable
  private ApplyCallBack applyCallBack;
  @Nullable
  private SearchCallBack searchCallBack;

  private static final float WELCOME_OPTIOON_SCALE_FOCUS = 1.3f;
  private static final float WELCOME_OPTIOON_SCALE_NON_FOCUS = 1.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_FOCUS = 12.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS = 1.0f;
  private ItemAnimViewController itemAnimViewController;

  private void MP02_Animate(View view, boolean b)
  {
    float scale = b ? WELCOME_OPTIOON_SCALE_FOCUS : WELCOME_OPTIOON_SCALE_NON_FOCUS;
    float transx = b ? WELCOME_OPTIOON_TRANSLATION_X_FOCUS : WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS;
    ViewCompat.animate(view)
            .scaleX(scale)
            .scaleY(scale)
            .translationX(transx)
            .start();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    recyclerView.setFocusableInTouchMode(true);
    recyclerView.requestFocus();
//    recyclerView.setOnKeyListener(new View.OnKeyListener(){
//
//      @Override
//      public boolean onKey(View v, int keyCode, KeyEvent event) {
//        switch(keyCode) {
//          case KeyEvent.KEYCODE_DPAD_DOWN:
//            ContactSelectionListAdapter.setScrollUp(true);
//            //return true;
//            break;
//          case KeyEvent.KEYCODE_DPAD_UP:
//            ContactSelectionListAdapter.setScrollUp(false);
//            //return true;
//            break;
//          //default:
//
//          //  break;
//        }
//        return false;
//      }
//    });
  }

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

    if (context instanceof RefreshCallback){
      refreshCallback = (RefreshCallback) context;
    }

    if (context instanceof SearchCallBack){
      searchCallBack = (SearchCallBack) context;
    }

    if (context instanceof ApplyCallBack){
      applyCallBack = (ApplyCallBack) context;
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
  }

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onActivityCreated(icicle);

    initializeCursor();
  }

  public void setOnKey(){
    searchAdapter.setScorllUp(false);
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
    rlContainer              = view.findViewById(R.id.rl_container);
//    fastScroller             = view.findViewById(R.id.fast_scroller);
    showContactsLayout       = view.findViewById(R.id.show_contacts_container);
    showContactsTextview = view.findViewById(R.id.show_contacts_textview);
    showContactsDescription  = view.findViewById(R.id.show_contacts_description);
//    showContactsProgress     = view.findViewById(R.id.progress);
    chipGroup                = view.findViewById(R.id.chipGroup);
    chipGroupScrollContainer = view.findViewById(R.id.chipGroupScrollContainer);
    constraintLayout         = view.findViewById(R.id.container);
    shadowView               = view.findViewById(R.id.toolbar_shadow);
    toolbarShadowAnimationHelper = new ToolbarShadowAnimationHelper(shadowView);

    recyclerView.addOnScrollListener(toolbarShadowAnimationHelper);

    Resources res = getActivity().getResources();
    mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_small_height);
    mNormalHeight = res.getDimensionPixelSize(R.dimen.small_height);

    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_small_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.small_textsize);

    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    recyclerView.setItemAnimator(new DefaultItemAnimator() {
      @Override
      public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
      }
    });

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
    itemAnimViewController = new ItemAnimViewController(rlContainer,mFocusTextSize,mFocusHeight,marginTop);
    itemAnimViewController.setItemVisibility(true);

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
    return view;
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

    if (getActivity() instanceof ShareActivity){
      ShareActivity activity = (ShareActivity) getActivity();
      shareConfirmClickListener = activity.getShareConfirmClickListener();
      isSharing = true;
    }else{
      shareConfirmClickListener = null;
    }
    cursorRecyclerViewAdapter = new ContactSelectionListAdapter(requireContext(),
                                                                glideRequests,
                                                                null,
                                                                new ListClickListener(),
                                                                isMulti,
                                                                currentSelection,
                                                                rlContainer,
                                                                72,onFocusChangeListener,
                                                                shareConfirmClickListener,
                                                                isSharing);

    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();

    if (searchCallBack != null) {
      searchAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer,createSearchActionView(searchCallBack));
      concatenateAdapter.addAdapter(searchAdapter);
    }

    if (listCallback != null) {
      headerAdapter = new FixedViewsAdapter(requireContext(),72,rlContainer,createNewGroupItem(listCallback));
      headerAdapter.hide();
      concatenateAdapter.addAdapter(headerAdapter);
    }

    if (applyCallBack != null) {
      applyAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer,createApplyActionView(applyCallBack));
      concatenateAdapter.addAdapter(applyAdapter);
    }

    if (listCallback != null) {
      footerAdapter = new FixedViewsAdapter(requireContext(),72,rlContainer,createInviteActionView(listCallback));
      footerAdapter.hide();
      concatenateAdapter.addAdapter(footerAdapter);
    }

    if (refreshCallback!=null){
      footer1Adapter = new FixedViewsAdapter(requireContext(),72,rlContainer,createRefreshActionView(refreshCallback));
      concatenateAdapter.addAdapter(footer1Adapter);
    }

    concatenateAdapter.addAdapter(cursorRecyclerViewAdapter);

//    recyclerView.addItemDecoration(new LetterHeaderDecoration(requireContext(), this::hideLetterHeaders));
    recyclerView.setAdapter(concatenateAdapter);
    recyclerView.setClipToPadding(false);
    recyclerView.setClipChildren(false);
    recyclerView.setPadding(0, 76, 0, 200);
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
    TextView view = (TextView)LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_invite_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> listCallback.onInvite());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
//    view.setOnFocusChangeListener(this::MP02_Animate);
    return view;
  }

  private View createApplyActionView(@NonNull ApplyCallBack applyCallBack) {
    TextView view = (TextView) LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_apply_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> applyCallBack.onApply());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
    return view;
  }

  private View createSearchActionView(@NonNull SearchCallBack searchCallBack) {
    MyEditText view =
            (MyEditText) LayoutInflater.from(requireContext())
                    .inflate(R.layout.contact_selection_search_action_item, (ViewGroup) requireView(), false);
    MyEditText mEdit = view.findViewById(R.id.name);
    mEdit.setOnFilterChangedListener((filter, nochange) -> {
      setQueryFilter(filter);
      searchAdapter.setScorllUp(nochange);
    });
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
    return view;
  }

  private View createRefreshActionView(@NonNull RefreshCallback refreshCallback) {
    View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_refresh_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> refreshCallback.onReFresh());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
//    view.setOnFocusChangeListener(this::MP02_Animate);
    return view;
  }

  private View createNewGroupItem(@NonNull ListCallback listCallback) {
    View view = LayoutInflater.from(requireContext())
                              .inflate(R.layout.contact_selection_new_group_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> listCallback.onNewGroup(false));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
    return view;
  }

  private void initializeNoContactsPermission() {
    swipeRefresh.setVisibility(View.GONE);

    showContactsLayout.setVisibility(View.VISIBLE);
//    showContactsProgress.setVisibility(View.INVISIBLE);
    showContactsDescription.setText(R.string.contact_selection_list_fragment__signal_needs_access_to_your_contacts_in_order_to_display_them);
    showContactsTextview.setVisibility(View.VISIBLE);
    showContactsTextview.setOnClickListener(v -> {
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
    showContactsTextview.setOnFocusChangeListener(this::MP02_Animate);
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
    Log.v(TAG, "onCreateLoader MODE :  " +  activity.getIntent().getIntExtra(DISPLAY_MODE, DisplayMode.FLAG_ALL));
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
    if (applyAdapter != null) {
      applyAdapter.show();
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
//    if (useFastScroller) {
//      fastScroller.setVisibility(View.VISIBLE);
//      fastScroller.setRecyclerView(recyclerView);
//    } else {
//      fastScroller.setRecyclerView(null);
//      fastScroller.setVisibility(View.GONE);
//    }
  }

  private boolean shouldDisplayRecents() {
    return safeArguments().getBoolean(RECENTS, requireActivity().getIntent().getBooleanExtra(RECENTS, false));
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    cursorRecyclerViewAdapter.changeCursor(null);
//    fastScroller.setVisibility(View.GONE);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleContactPermissionGranted() {
    final Context context = requireContext();

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected void onPreExecute() {
        swipeRefresh.setVisibility(View.GONE);
        showContactsLayout.setVisibility(View.VISIBLE);
        showContactsTextview.setVisibility(View.INVISIBLE);
        showContactsDescription.setText(R.string.ConversationListFragment_loading);
//        showContactsProgress.setVisibility(View.VISIBLE);
//        showContactsProgress.spin();
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

      if (!canSelectSelf && Recipient.self().getId().equals(selectedContact.getOrCreateRecipientId(requireContext()))) {
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
            return UsernameUtil.fetchUuidForUsername(requireContext(), contact.getNumber());
          }, uuid -> {
            loadingDialog.dismiss();
            if (uuid.isPresent()) {
              Recipient recipient = Recipient.externalUsername(requireContext(), uuid.get(), contact.getNumber());
              SelectedContact selected = SelectedContact.forUsername(recipient.getId(), contact.getNumber());

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
    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    cursorRecyclerViewAdapter.removeFromSelectedContacts(selectedContact);
    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
    removeChipForContact(selectedContact);
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void removeChipForContact(@NonNull SelectedContact contact) {
    for (int i = chipGroup.getChildCount() - 1; i >= 0; i--) {
      View v = chipGroup.getChildAt(i);
      if (v instanceof ContactChip && contact.matches(((ContactChip) v).getContact())) {
        chipGroup.removeView(v);
      }
    }

    if (getChipCount() == 0) {
//      setChipGroupVisibility(ConstraintSet.GONE);
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
//      setChipGroupVisibility(ConstraintSet.VISIBLE);
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
        if (getView() == null || !requireView().isAttachedToWindow()) {
          Log.w(TAG, "Fragment's view was detached before the animation completed.");
          return;
        }

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
    if (selectionWarningLimitReachedExactly()) {
      if (onSelectionLimitReachedListener != null) {
        onSelectionLimitReachedListener.onSuggestedLimitReached(selectionLimit.getRecommendedLimit());
      } else {
        GroupLimitDialog.showRecommendedLimitMessage(requireContext());
      }
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
    if (!safeArguments().getBoolean(DISPLAY_CHIPS, requireActivity().getIntent().getBooleanExtra(DISPLAY_CHIPS, true))) {
      return;
    }
    TransitionManager.beginDelayedTransition(constraintLayout, new AutoTransition().setDuration(CHIP_GROUP_REVEAL_DURATION_MS));

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(constraintLayout);
    constraintSet.setVisibility(R.id.chipGroupScrollContainer, visibility);
    constraintSet.applyTo(constraintLayout);
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipGroupScrollContainer) ? chipGroup.getWidth() : 0;
    chipGroupScrollContainer.smoothScrollTo(x, 0);
  }

  public interface OnContactSelectedListener {
    /** Provides an opportunity to disallow selecting an item. Call the callback with false to disallow, or true to allow it. */
    void onBeforeContactSelected(Optional<RecipientId> recipientId, String number, Consumer<Boolean> callback);
    void onContactDeselected(Optional<RecipientId> recipientId, String number);
    void onSelectionChanged();
  }

  private View.OnFocusChangeListener onFocusChangeListener2 = new View.OnFocusChangeListener() {
    @Override
    public void onFocusChange(View view, boolean focused) {
      int position = recyclerView.getChildAdapterPosition(view);
//      recyclerView.getChildCount();
      if (focused){
        if (position == 0){
          itemAnimViewController.setItemVisibility(true);
        }else itemAnimViewController.setItemVisibility(false);
        TextView textView = (TextView) recyclerView.getLayoutManager().findViewByPosition(position);
        if (isScrollUp){
          TextView textViewLast = (TextView) recyclerView.getLayoutManager().findViewByPosition(position-1);
          itemAnimViewController.actionUpIn(textViewLast.getText().toString(),textView.getText().toString());
        }else {
          if (position == 1){
            ContactSelectionListItem clItem = (ContactSelectionListItem) recyclerView.getLayoutManager().findViewByPosition(position+1);
            TextView text = (TextView)(clItem.nameView);
            itemAnimViewController.actionDownIn(text.getText().toString(),textView.getText().toString());
          }else {
            TextView textViewLast = (TextView) recyclerView.getLayoutManager().findViewByPosition(position+1);
            itemAnimViewController.actionDownIn(textViewLast.getText().toString(),textView.getText().toString());
          }
        }

      }

      TextView item = (TextView) view;
      float height = ((float) (mFocusHeight - mNormalHeight)) * (focused ? 1 : 0) + (float) mNormalHeight;
      float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (focused ? 1 : 0) + mNormalTextSize;
      float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (focused ? 1 : 0);
      int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (focused ? 1 : 0));
      int color = alpha * 0x1000000 + 0xffffff;
      item.setPadding((int) padding,item.getPaddingTop(),item.getPaddingRight(),item.getPaddingBottom());
      item.setTextSize((int) textsize);
      item.setTextColor(color);
      item.getLayoutParams().height = (int) height;
    }
  };


  public View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
    @Override
    public void onFocusChange(View view, boolean focused) {
//      ValueAnimator va ;
      ContactSelectionListItem CSLitem;
      CSLitem=(ContactSelectionListItem)(view);
      TextView text1 = (TextView)(CSLitem.nameView);
      TextView text2 = (TextView)(CSLitem.numberView);
      TextView text3 = (TextView)(CSLitem.labelView);
      //Log.d(TAG,"focused is:"+focused+" text1 is:"+text1.getText().toString()+" text23 is:"+text2.getText().toString()+" "+text3.getText().toString());
      if(focused){
        int position = recyclerView.getChildAdapterPosition(view);
        ContactSelectionListItem clItem = (ContactSelectionListItem) recyclerView.getLayoutManager().findViewByPosition(position);
        TextView textView = clItem.nameView;
        if (isScrollUp){
          if (position == 2){
            TextView lastview = (TextView) recyclerView.getLayoutManager().findViewByPosition(position-1);
            itemAnimViewController.actionUpIn(lastview.getText().toString(),textView.getText().toString());
          }else {
            ContactSelectionListItem lastClItem = (ContactSelectionListItem) recyclerView.getLayoutManager().findViewByPosition(position-1);
            TextView lastview = lastClItem.nameView;
            itemAnimViewController.actionUpIn(lastview.getText().toString(),textView.getText().toString());
          }
        }else {
          ContactSelectionListItem lastClItem = (ContactSelectionListItem) recyclerView.getLayoutManager().findViewByPosition(position+1);
          TextView lastview = lastClItem.nameView;
          itemAnimViewController.actionUpIn(lastview.getText().toString(),textView.getText().toString());
        }
      }
      float height = focused?mFocusHeight:mNormalHeight;
      float textsize = (float) mNormalTextSize;
      float padding = focused?(float)mNormalPaddingX -1:(float)mNormalPaddingX;
      int alpha = 0x81 ;
      int color =  alpha*0x1000000 + 0xffffff;

//          if(focused){
//            CSLitem.getLayoutParams().height = (int)height + mNormalHeight;
//            text1.getLayoutParams().height=mNormalHeight + 5;
//
//          } else {
//            CSLitem.getLayoutParams().height = (int)height;
//          }

      text1.setTextSize((int)textsize);
      text1.setTextColor(color);
      view.setPadding((int) padding,view.getPaddingTop(),view.getPaddingRight(),view.getPaddingBottom());
      text1.getLayoutParams().height = (int)height;
      view.getLayoutParams().height = (int) height;

//        }
//      });
//
//      FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
//      va.setInterpolator(FastOutLinearInInterpolator);
//      if (focused) {
//        va.setDuration(270);
//        va.start();
//      } else {
//        va.setDuration(270);
//        va.start();
//      }
//      oldtext1 = text1.getText().toString();
//      oldtext2 = text2.getText().toString() + " " +text3.getText().toString();

    }
  };

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

  public interface AbstractContactsCursorLoaderFactoryProvider {
    @NonNull AbstractContactsCursorLoader.Factory get();
  }

  public interface RefreshCallback{
    void onReFresh();
  }

  public interface SearchCallBack {
    void onSearch(View view);
  }

  public interface ApplyCallBack {
    void onApply();
  }
}
