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
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.MyEditText;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 */
public final class ContactSelectionListFragment2 extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {
  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ContactSelectionListFragment2.class);

  public static final String DISPLAY_MODE = "display_mode";
  public static final String REFRESHABLE = "refreshable";
  public static final String RECENTS = "recents";
  public static final String SELECTION_LIMITS  = "selection_limits";
  public static final String CURRENT_SELECTION = "current_selection";
  public static final String HIDE_COUNT        = "hide_count";

  private TextView emptyText;
  private List<SelectedContact> selectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private SwipeRefreshLayout swipeRefresh;
  private View showContactsLayout;
  private TextView showContactsTextview;
  private TextView showContactsDescription;
  private RelativeLayout rlContainer;
  //  private ProgressWheel               showContactsProgress;
  private String cursorFilter;
  private RecyclerView recyclerView;
  //  private RecyclerViewFastScroller    fastScroller;
  private ContactSelectionListAdapter cursorRecyclerViewAdapter;
  private SelectionLimits selectionLimit   = SelectionLimits.NO_LIMITS;
  private Set<RecipientId>  currentSelection;

  public int mFocusHeight;
  public int mNormalHeight;
  public int mNormalPaddingX;
  public int mFocusPaddingX;
  public int mFocusTextSize;
  public int mNormalTextSize;
  private int marginTop = 76;
  private boolean isScrollUp = false;

  @Nullable
  private FixedViewsAdapter footerAdapter, footer1Adapter, footer2Adapter,searchAdapter, applyAdapter;
  @Nullable
  private SendSmsToCallback inviteCallback;
  @Nullable
  private RefreshCallback refreshCallback;
  @Nullable
  private NewGroupCallback newGroupCallback;
  @Nullable
  private ApplyCallBack applyCallBack;
  @Nullable
  private SearchCallBack searchCallBack;



  private static final float WELCOME_OPTIOON_SCALE_FOCUS = 1.3f;
  private static final float WELCOME_OPTIOON_SCALE_NON_FOCUS = 1.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_FOCUS = 12.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS = 1.0f;
  private ItemAnimViewController itemAnimViewController;

  private void MP02_Animate(View view, boolean b) {
    float scale = b ? WELCOME_OPTIOON_SCALE_FOCUS : WELCOME_OPTIOON_SCALE_NON_FOCUS;
    float transx = b ? WELCOME_OPTIOON_TRANSLATION_X_FOCUS : WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS;
    ViewCompat.animate(view)
            .scaleX(scale)
            .scaleY(scale)
            .translationX(transx)
            .start();
  }


  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof SendSmsToCallback) {
      inviteCallback = (SendSmsToCallback) context;
    }
    if (context instanceof RefreshCallback){
      refreshCallback = (RefreshCallback) context;
    }
    if (context instanceof NewGroupCallback){
      newGroupCallback = (NewGroupCallback) context;
    }
    if (context instanceof SearchCallBack){
      searchCallBack = (SearchCallBack) context;
    }
    if (context instanceof ApplyCallBack){
      applyCallBack = (ApplyCallBack) context;
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
                this.getLoaderManager().initLoader(0, null, this);
              }
            })
            .onAnyDenied(() -> {
              getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

              if (getActivity().getIntent().getBooleanExtra(RECENTS, false)) {
                getLoaderManager().initLoader(0, null, ContactSelectionListFragment2.this);
              } else {
                initializeNoContactsPermission();
              }
            })
            .execute();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    recyclerView.setFocusableInTouchMode(true);
    recyclerView.requestFocus();
//    recyclerView.setOnKeyListener(new View.OnKeyListener(){
//      @Override
//      public boolean onKey(View v, int keyCode, KeyEvent event) {
//        Log.d(TAG, "onkey1");
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
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText = ViewUtil.findById(view, android.R.id.empty);
    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    swipeRefresh = ViewUtil.findById(view, R.id.swipe_refresh);
    rlContainer = ViewUtil.findById(view,R.id.rl_container);
//    fastScroller = ViewUtil.findById(view, R.id.fast_scroller);
    showContactsLayout = view.findViewById(R.id.show_contacts_container);
    showContactsTextview = view.findViewById(R.id.show_contacts_textview);
    showContactsDescription = view.findViewById(R.id.show_contacts_description);
//    showContactsProgress = view.findViewById(R.id.progress);

    Resources res = getActivity().getResources();
    mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
    mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);


    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    swipeRefresh.setEnabled(getActivity().getIntent().getBooleanExtra(REFRESHABLE, true));
    itemAnimViewController = new ItemAnimViewController(rlContainer,mFocusTextSize,mFocusHeight,marginTop);
    itemAnimViewController.setItemVisibility(true);
    selectionLimit = requireActivity().getIntent().getParcelableExtra(SELECTION_LIMITS);
    if (!isMulti()) {
      selectionLimit = SelectionLimits.NO_LIMITS;
    }
    currentSelection = getCurrentSelection();


    return view;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public @NonNull
  List<SelectedContact> getSelectedContacts() {
    List<SelectedContact> selected = new LinkedList<>();
    if (selectedContacts != null) {
      selected.addAll(selectedContacts);
    }

    return selected;
  }

  private Set<RecipientId> getCurrentSelection() {
    List<RecipientId> currentSelection = requireActivity().getIntent().getParcelableArrayListExtra(CURRENT_SELECTION);

    return currentSelection == null ? Collections.emptySet()
            : Collections.unmodifiableSet(Stream.of(currentSelection).collect(Collectors.toSet()));
  }

  private boolean isMulti() {
    return selectionLimit != null;
  }

  private void initializeCursor() {
    cursorRecyclerViewAdapter = new ContactSelectionListAdapter(requireContext(),
            GlideApp.with(this),
            null,
            new ListClickListener(),
            isMulti(),
            currentSelection,
            rlContainer,
            72,onFocusChangeListener,
            null,
            false);
    selectedContacts = cursorRecyclerViewAdapter.getSelectedContacts();

    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();

    if (searchCallBack != null) {
      searchAdapter = new FixedViewsAdapter(requireContext(), 0, rlContainer,createSearchActionView(searchCallBack));
      concatenateAdapter.addAdapter(searchAdapter);
    }

    if (applyCallBack != null) {
      applyAdapter = new FixedViewsAdapter(requireContext(), 0, rlContainer,createApplyActionView(applyCallBack));
      concatenateAdapter.addAdapter(applyAdapter);
    }

    if (inviteCallback != null) {
      //footerAdapter = new FixedViewsAdapter(createInviteActionView(inviteCallback));
      footerAdapter = new FixedViewsAdapter(requireContext(),72,rlContainer,createInviteActionView(inviteCallback));
      footerAdapter.hide();
      concatenateAdapter.addAdapter(footerAdapter);
    }

    concatenateAdapter.addAdapter(cursorRecyclerViewAdapter);

//    if (refreshCallback!=null){
//      footer1Adapter = new FixedViewsAdapter(requireContext(),72,rlContainer,createRefreshActionView(refreshCallback));
//      concatenateAdapter.addAdapter(footer1Adapter);
//    }
//    if (newGroupCallback!=null){
//      footer2Adapter = new FixedViewsAdapter(requireContext(),72,rlContainer,createNewGroupActionView(newGroupCallback));
//      concatenateAdapter.addAdapter(footer2Adapter);
//    }

    recyclerView.setAdapter(concatenateAdapter);
    recyclerView.setClipToPadding(false);
    recyclerView.setClipChildren(false);
    recyclerView.setPadding(0, 76, 0, 200);
    recyclerView.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));


  }

  private View createInviteActionView(@NonNull SendSmsToCallback inviteCallback) {
    TextView view = (TextView) LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_invite_action_item, (ViewGroup) requireView(), false);
    view.setText(R.string.InviteActivity_send);
    view.setOnClickListener(v -> inviteCallback.onSend());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
//    view.setOnFocusChangeListener(onFocusChangeListener2);
    return view;
  }

  private View createApplyActionView(@NonNull ApplyCallBack applyCallBack) {
    TextView view = (TextView) LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_apply_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> applyCallBack.onApply());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
//    view.setOnFocusChangeListener(onFocusChangeListener2);
    return view;
  }

  private View createSearchActionView(@NonNull SearchCallBack searchCallBack) {
    MyEditText view =
            (MyEditText) LayoutInflater.from(requireContext())
                    .inflate(R.layout.contact_selection_search_action_item, (ViewGroup) requireView(), false);
//    view.setOnClickListener(v -> searchCallBack.onSearch());
    MyEditText mEdit = view.findViewById(R.id.name);
//    mEdit.addTextChangedListener();
    mEdit.setOnFilterChangedListener((filter, nochange) -> {
      setQueryFilter(filter);
      searchAdapter.setScorllUp(nochange);
    });
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
//    view.setOnFocusChangeListener(this::MP02_Animate);
//    ((ContactSelectionActivity)getActivity()).setSupportActionBar(view);
//    assert  ((ContactSelectionActivity)getActivity()).getSupportActionBar() != null;
//    ((ContactSelectionActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
//    ((ContactSelectionActivity)getActivity()).getSupportActionBar().setDisplayShowTitleEnabled(false);
//    ((ContactSelectionActivity)getActivity()).getSupportActionBar().setIcon(null);
//    ((ContactSelectionActivity)getActivity()).getSupportActionBar().setLogo(null);

//    view.setOnFocusChangeListener(onFocusChangeListener2);

    return view;
  }

  private View createRefreshActionView(@NonNull RefreshCallback refreshCallback) {
    View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_refresh_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(view1 -> {
        refreshCallback.onReFresh();
    });
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
    view.setOnFocusChangeListener(this::MP02_Animate);
    return view;
  }

  private View createNewGroupActionView(@NonNull NewGroupCallback newGroupCallback) {
    View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.contact_selection_newgroup_action_item, (ViewGroup) requireView(), false);
    view.setOnClickListener(v -> {
        newGroupCallback.onNewGroup();
    });
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setDefaultFocusHighlightEnabled(false);
    }
    view.setOnFocusChangeListener(this::MP02_Animate);
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

  public void setRefreshing(boolean refreshing) {
    swipeRefresh.setRefreshing(refreshing);
  }

  public void reset() {
    selectedContacts.clear();

    if (!isDetached() && !isRemoving() && getActivity() != null && !getActivity().isFinishing()) {
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  @Override
  public @NonNull
  Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ContactsCursorLoader.Factory(getActivity(),
            getActivity().getIntent().getIntExtra(DISPLAY_MODE, DisplayMode.FLAG_ALL),
            cursorFilter, getActivity().getIntent().getBooleanExtra(RECENTS, false)).create();
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, @Nullable Cursor data) {
    swipeRefresh.setVisibility(View.VISIBLE);
    showContactsLayout.setVisibility(View.GONE);

    cursorRecyclerViewAdapter.changeCursor(data);

    if (footerAdapter != null) {
      footerAdapter.show();
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

      if (!isMulti() || !selectedContacts.contains(selectedContact)) {
        if (contact.isUsernameType()) {
          AlertDialog loadingDialog = SimpleProgressDialog.show(requireContext());

          SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            return UsernameUtil.fetchUuidForUsername(requireContext(), contact.getNumber());
          }, uuid -> {
            loadingDialog.dismiss();
            if (uuid.isPresent()) {
              Recipient recipient = Recipient.externalUsername(requireContext(), uuid.get(), contact.getNumber());
              selectedContacts.add(SelectedContact.forUsername(recipient.getId(), contact.getNumber()));
              contact.setChecked(true, false);

              if (onContactSelectedListener != null) {
                onContactSelectedListener.onContactSelected(Optional.of(recipient.getId()), null);
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
          selectedContacts.add(selectedContact);
          contact.setChecked(true, false);

          if (onContactSelectedListener != null) {
            onContactSelectedListener.onContactSelected(contact.getRecipientId(), contact.getNumber());
          }
        }
      } else {
        selectedContacts.remove(selectedContact);
        contact.setChecked(false, false);

        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(contact.getRecipientId(), contact.getNumber());
        }
      }
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  public interface OnContactSelectedListener {
    void onContactSelected(Optional<RecipientId> recipientId, String number);

    void onContactDeselected(Optional<RecipientId> recipientId, String number);
  }

  public interface SendSmsToCallback {
    void onSend();
  }

  public interface NewGroupCallback{
    void onNewGroup();
  }

  public interface RefreshCallback{
    void onReFresh();
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
      // Log.d(TAG,"focused is:"+focused+" text1 is:"+text1.getText().toString()+" text23 is:"+text2.getText().toString()+" "+text3.getText().toString());
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

  public interface SearchCallBack {
    void onSearch(View view);
  }

  public interface ApplyCallBack {
    void onApply();
  }
}
