/**
 * Copyright (C) 2014 Open Whisper Systems
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nineoldandroids.animation.ObjectAnimator;

import org.thoughtcrime.securesms.components.CircledImageView;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import java.util.ArrayList;
import java.util.Collections;

import de.gdata.messaging.SlidingTabLayout;
import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GService;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.NavDrawerAdapter;
import de.gdata.messaging.util.PrivacyBridge;
import de.gdata.messaging.util.ProfileAccessor;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity implements
        ConversationListFragment.ConversationSelectedListener {
    private static final int LAST_XX_CALLS = 20;
    // private final DynamicTheme dynamicTheme = new DynamicTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    private ConversationListFragment conversationListFragment;
    private ContactSelectionFragment contactSelectionFragment;
    private MasterSecret masterSecret;
    private GDataPreferences gDataPreferences;
    private ContentObserver observer;
    private static String inputText = "";
    private String[] navLabels;
    private TypedArray navIcons;
    private FloatingActionButton fab;
    private FloatingActionButton fabCOne;
    private FloatingActionButton fabCTwo;
    private FloatingActionButton fabCThree;
    private LinearLayout mDrawerNavi;
    private SlidingTabLayout mSlidingTabLayout;
    private LinearLayout actionFloatMenu;
    private FloatingActionButton fabConversation;
    private FloatingActionButton fabGroup;
    private TextView textViewCOne;
    private TextView textViewCTwo;
    private TextView textViewCThree;
    private TextView textViewNewConversation;
    private TextView textViewNewGroup;
    private Recipients recCalledRec;
    private Animation slideDown;
    private Animation slideUp;
    private CircledImageView fabImageBackgroundThree;
    private CircledImageView fabImageBackgroundOne;
    private CircledImageView fabImageBackgroundTwo;

    @Override
    public void onCreate(Bundle icicle) {
        //dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
        super.onCreate(icicle);
        gDataPreferences = new GDataPreferences(getBaseContext());
        setContentView(R.layout.gdata_conversation_list_activity);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(toolbar);
        navLabels = getResources().getStringArray(R.array.array_nav_labels);
        navIcons = getResources().obtainTypedArray(R.array.array_nav_icons);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout_gdata);

        mDrawerNavi = (LinearLayout) findViewById(R.id.drawerll);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.common_open_on_phone,  /* "open drawer" description */
                R.string.abc_action_bar_home_description  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle(R.string.app_name);
                initNavDrawer(navLabels, navIcons);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getSupportActionBar().setTitle(R.string.app_name);
            }
        };
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setElevation(0);
        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerLayout.setScrimColor(Color.argb(0xC8, 0xFF, 0xFF, 0xFF));
        actionFloatMenu = (LinearLayout) findViewById(R.id.action_float_menu);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fabCOne = (FloatingActionButton) findViewById(R.id.fab_new_contact_one);
        fabCTwo = (FloatingActionButton) findViewById(R.id.fab_new_contact_two);
        fabCThree = (FloatingActionButton) findViewById(R.id.fab_new_contact_three);
        fabImageBackgroundOne = (CircledImageView) findViewById(R.id.img_new_contact_one);
        fabImageBackgroundTwo = (CircledImageView) findViewById(R.id.img_new_contact_two);
        fabImageBackgroundThree = (CircledImageView) findViewById(R.id.img_new_contact_three);
        textViewCOne = (TextView) findViewById(R.id.textViewCOne);
        textViewCTwo = (TextView) findViewById(R.id.textViewCTwo);
        textViewCThree = (TextView) findViewById(R.id.textViewCThree);
        textViewNewConversation = (TextView) findViewById(R.id.textViewNew);
        textViewNewGroup = (TextView) findViewById(R.id.textViewGroup);

        textViewNewConversation.setText(R.string.text_secure_normal__menu_new_message);
        textViewNewGroup.setText(R.string.text_secure_normal__menu_new_group);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fabGroup = (FloatingActionButton) findViewById(R.id.fab_new_group);
        fabConversation = (FloatingActionButton) findViewById(R.id.fab_new_conversation);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(actionFloatMenu, "alpha", 1f, 0f);
        alpha.setRepeatMode(ObjectAnimator.REVERSE);
        alpha.setRepeatCount(1);
        alpha.setDuration(800);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleActionFloatMenu(true, false, false);
            }
        });
        fabGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createGroup();
            }
        });
        fabConversation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSingleContactSelection();
            }
        });
        initNavDrawer(navLabels, navIcons);

        LinearLayout profileDrawer = (LinearLayout) findViewById(R.id.drawer);
        profileDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrawerLayout.closeDrawer(mDrawerNavi);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        handleOpenProfile();
                    }
                }, 200);
            }
        });
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setTitle(R.string.app_name);
        initViewPagerLayout();
        GUtil.forceOverFlowMenu(getApplicationContext());
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(PrivacyBridge.ACTION_RELOAD_ADAPTER));
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(PushDecryptJob.ACTION_RELOAD_HEADER));
        slideUp = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_from_right);
        slideDown = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_to_right);
        refreshProfile();
        setActionFloatMenuIcons();
    }

    public void toggleActionFloatMenu(boolean toggleMenu, boolean toggleButton, boolean fastClose) {
        boolean firstPage = gDataPreferences.getViewPagersLastPage() == 0;

        ((RelativeLayout) findViewById(R.id.fastclose)).setVisibility(fastClose?View.INVISIBLE:View.VISIBLE);

        if(toggleMenu) {
            if (actionFloatMenu.getVisibility() == View.INVISIBLE) {
                if(firstPage) {
                    actionFloatMenu.setVisibility(actionFloatMenu.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                    actionFloatMenu.startAnimation(slideUp);
                }
            } else {
                if(firstPage) {
                    actionFloatMenu.startAnimation(slideDown);
                    actionFloatMenu.setVisibility(actionFloatMenu.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                } else {
                    actionFloatMenu.startAnimation(slideDown);
                    actionFloatMenu.setVisibility(View.INVISIBLE);
                }
            }
        }
        if(toggleButton) {
            if (fab.getVisibility() == View.INVISIBLE) {
                if(firstPage) {
                    fab.setVisibility(fab.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                    fab.startAnimation(slideUp);
                }
            } else {
                fab.startAnimation(slideDown);
                fab.setVisibility(fab.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            }
        }
        if(actionFloatMenu.getVisibility() == View.VISIBLE &&  findViewById(R.id.fastclose).getVisibility() == View.VISIBLE) {
            findViewById(R.id.overlay_gray).setVisibility(View.VISIBLE);
            findViewById(R.id.overlay_gray).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggleActionFloatMenu(true, false, false);
                }
            });
            fab.setImageBitmap(((BitmapDrawable)getResources().getDrawable(R.drawable.ic_cancel_white_24dp)).getBitmap());
        } else {
            fab.setImageBitmap(((BitmapDrawable)getResources().getDrawable(R.drawable.ic_add_white_24dp)).getBitmap());
            findViewById(R.id.overlay_gray).setVisibility(View.INVISIBLE);
        }
    }
    public void setActionFloatMenuIcons() {
        String mobileNumber = "";
        int i = 1;
        int found = 0;
        ArrayList<Recipients> recentlyRecipients = new ArrayList<Recipients>();

        String strOrder = CallLog.Calls.DATE + " DESC";
        Cursor mCallCursor = getApplicationContext().getContentResolver().query(CallLog.Calls.CONTENT_URI,
                null,
                CallLog.Calls.TYPE + " = " + CallLog.Calls.OUTGOING_TYPE +" OR " + CallLog.Calls.TYPE + " = " + CallLog.Calls.INCOMING_TYPE
                , null,
                strOrder);
        mCallCursor.moveToFirst();
        do {
            if (i>LAST_XX_CALLS)
                break;
            mobileNumber = mCallCursor.getString(mCallCursor.getColumnIndex(android.provider.CallLog.Calls.NUMBER));
            recCalledRec = RecipientFactory.getRecipientsFromString(getApplicationContext(), mobileNumber, false);

            if (recCalledRec.getPrimaryRecipient() != null && recCalledRec.getPrimaryRecipient().getName() != null && !recCalledRec.getPrimaryRecipient().getName().equals("")) {
                recentlyRecipients.add(recCalledRec);
                recCalledRec = null;
            }
            i++;
        }
        while (mCallCursor.moveToNext());

        recentlyRecipients = getFrequentContact(recentlyRecipients);

        for(Recipients recipients : recentlyRecipients) {
            found++;
            if (found == 1) {
                final Recipients fOne = recipients;
                fabCOne.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openConversationForRecipients(fOne);
                    }
                });
                setProfilePictureToFloatButton(fabCOne, fOne, fabImageBackgroundOne);
                textViewCOne.setText(fOne.getPrimaryRecipient().getName());
            } else if (found == 2) {
                final Recipients fTwo = recipients;
                fabCTwo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openConversationForRecipients(fTwo);
                    }
                });
                setProfilePictureToFloatButton(fabCTwo, fTwo, fabImageBackgroundTwo);
                textViewCTwo.setText(fTwo.getPrimaryRecipient().getName());
            } else if (found == 3) {
                final Recipients fThree = recipients;
                fabCThree.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openConversationForRecipients(fThree);
                    }
                });
                setProfilePictureToFloatButton(fabCThree, fThree, fabImageBackgroundThree);
                textViewCThree.setText(fThree.getPrimaryRecipient().getName());
            }
        }
        if (found < 3) {
            fabCThree.setVisibility(View.GONE);
            textViewCThree.setVisibility(View.GONE);
            fabImageBackgroundThree.setVisibility(View.GONE);
        }
        if (found < 2) {
            fabCTwo.setVisibility(View.GONE);
            textViewCTwo.setVisibility(View.GONE);
            fabImageBackgroundTwo.setVisibility(View.GONE);
        }
        if (found < 1) {
            fabCOne.setVisibility(View.GONE);
            textViewCOne.setVisibility(View.GONE);
            fabImageBackgroundOne.setVisibility(View.GONE);
        }
        if(gDataPreferences.getViewPagersLastPage() == 1) {
            toggleActionFloatMenu(true, true, true);
        } else {
            if(actionFloatMenu.getVisibility() == View.VISIBLE &&  findViewById(R.id.fastclose).getVisibility() == View.VISIBLE) {
                toggleActionFloatMenu(true, false, true);
            }
        }
    }

    private void setProfilePictureToFloatButton(final FloatingActionButton fab, Recipients recipients, CircledImageView imgView) {
        if(recipients.getPrimaryRecipient() != null) {
            ImageSlide avatarSlide = ProfileAccessor.getProfileAsImageSlide(this, masterSecret, GUtil.numberToLong(recipients.getPrimaryRecipient().getNumber())+"");
            if (avatarSlide != null) {
                    imgView.setVisibility(View.VISIBLE);
                    fab.setVisibility(View.INVISIBLE);
                    ProfileAccessor.buildGlideRequest(avatarSlide).into(imgView);
                    imgView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            fab.performClick();
                        }
                    });
            } else {
                fab.setVisibility(View.VISIBLE);
                fab.setImageBitmap(recipients.getPrimaryRecipient().getGeneratedAvatarWithColor(getApplicationContext(), fab.getSolidColor()));
                ((CardView) imgView.getParent()).setVisibility(View.GONE);
            }
        }
    }

    public ArrayList<Recipients> getFrequentContact(ArrayList<Recipients> logEntriesArray) {

        ArrayList<String> numArray        = new ArrayList<String>();
        ArrayList<Recipients> recArray        = new ArrayList<Recipients>();
        ArrayList<Recipients> orderedRecArray        = new ArrayList<Recipients>();
        ArrayList<Integer> callCountArray = new ArrayList<Integer>();

        for (int i = 0; i < logEntriesArray.size(); i++) {
            int index = numArray.indexOf(GUtil.numberToLong(logEntriesArray.get(i).getPrimaryRecipient().getNumber())+"");
            if (numArray.contains(GUtil.numberToLong(logEntriesArray.get(i).getPrimaryRecipient().getNumber())+"")) {
                int newCount = callCountArray.get(index) + 1;
                callCountArray.set(index, newCount);
            } else {
                numArray.add(GUtil.numberToLong(logEntriesArray.get(i).getPrimaryRecipient().getNumber())+"");
                recArray.add(logEntriesArray.get(i));
                callCountArray.add(1);
            }
        }
        addAndRemoveMax(callCountArray, recArray, orderedRecArray);

        return orderedRecArray;
    }
    public void addAndRemoveMax(ArrayList<Integer> callCountArray,  ArrayList<Recipients> recArray, ArrayList<Recipients> orderedRecArray) {
        if(callCountArray.size()>0) {
            int maxValue = Collections.max(callCountArray);
            int maxId = -1;
            for (int i = 0; i < callCountArray.size(); i++) {
                if (callCountArray.get(i) == maxValue) {
                    orderedRecArray.add(recArray.get(i));
                    maxId = i;
                    break;
                }
            }
            if (maxId > -1) {
                callCountArray.remove(maxId);
                recArray.remove(maxId);
                addAndRemoveMax(callCountArray, recArray, orderedRecArray);
            }
        }
    }
    private void openConversationForRecipients(Recipients rec) {
        if (masterSecret != null) {
            Intent intent = new Intent(getApplicationContext(), ConversationActivity.class);
            intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, rec.getIds());
            long existingThread = DatabaseFactory.getThreadDatabase(getApplicationContext()).getThreadIdIfExistsFor(rec);
            intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
            intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
            intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, 0);
            intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
            startActivity(intent);
        }
    }

    private void handleOpenProfile() {
        final Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("master_secret", masterSecret);
        intent.putExtra("profile_id", GUtil.numberToLong(gDataPreferences.getE164Number()) + "");
        if (getSupportActionBar() != null) {
            intent.putExtra("profile_name", getSupportActionBar().getTitle());
        }
        intent.putExtra("profile_number", gDataPreferences.getE164Number());
        startActivity(intent);
    }

    private void refreshProfile() {
        ImageView profileImageView = (ImageView) findViewById(R.id.profile_picture);
        Slide myProfileImage = ProfileAccessor.getMyProfilePicture(getApplicationContext());
        if (masterSecret != null && !(myProfileImage.getUri() + "").equals("")) {
            ProfileAccessor.setMasterSecred(masterSecret);
            if (GService.appContext == null) {
                GService.appContext = getApplicationContext();
            }
            ProfileAccessor.buildDraftGlideRequest(myProfileImage).into(profileImageView);
        } else {
            profileImageView.setImageBitmap(ContactPhotoFactory.getDefaultContactPhoto(getApplicationContext()));
        }
        TextView profileName = (TextView) findViewById(R.id.profileName);
        TextView profileStatus = (TextView) findViewById(R.id.profileStatus);
        profileStatus.setText(ProfileAccessor.getProfileStatus(this));
        profileName.setText(gDataPreferences.getE164Number());
    }

    public float dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float px = (float) ((dp * displayMetrics.density) + 0.5);
        return px;
    }

    private void initNavDrawer(String[] labels, TypedArray icons) {
        // Set the adapter for the list view
        NavDrawerAdapter adapter = new NavDrawerAdapter(this, labels, icons);
        // Set the list's click listener
        mDrawerList.setAdapter(adapter);
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
        mDrawerList.setItemChecked(0, false);
        mDrawerList.invalidate();
        mDrawerList.invalidateViews();
        adapter.notifyDataSetChanged();
    }

    /* The click listner for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
            mDrawerLayout.closeDrawer(mDrawerNavi);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    selectItem(position);
                }
            }, 200);
        }
    }

    private void selectItem(int position) {

        switch (position) {
            case NavDrawerAdapter.menu_new_message:
                openSingleContactSelection();
                break;
            case NavDrawerAdapter.menu_new_group:
                createGroup();
                break;
            case NavDrawerAdapter.menu_my_identity:
                handleMyIdentity();
                break;
            case NavDrawerAdapter.menu_clear_passphrase:
                handleClearPassphrase();
                break;
            case NavDrawerAdapter.menu_mark_all_read:
                handleMarkAllRead();
                break;
            case NavDrawerAdapter.menu_import_export:
                handleImportExport();
                break;
            case NavDrawerAdapter.menu_privacy:
                openPasswordDialogWithAction(CheckPasswordDialogFrag.ACTION_OPEN_PRIVACY);
                break;
            case NavDrawerAdapter.menu_privacy_hide:
                openPasswordDialogWithAction(CheckPasswordDialogFrag.ACTION_TOGGLE_VISIBILITY);
                break;
            case NavDrawerAdapter.menu_filter:
                openPasswordDialogWithAction(CheckPasswordDialogFrag.ACTION_OPEN_CALL_FILTER);
                break;
            case NavDrawerAdapter.menu_settings:
                handleDisplaySettings();
                break;
        }
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PrivacyBridge.ACTION_RELOAD_ADAPTER)) {
                reloadAdapter();
            } else if (intent.getAction().equals(PushDecryptJob.ACTION_RELOAD_HEADER)) {
                mSlidingTabLayout.refreshTabTitle();
            }
        }
    };

    @Override
    protected void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);
        mDrawerToggle.syncState();
    }

    @Override
    public void onResume() {
        super.onResume();
        // dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);
        initNavDrawer(navLabels, navIcons);
        refreshProfile();
        mSlidingTabLayout.refreshTabTitle();
        setActionFloatMenuIcons();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        MemoryCleaner.clean(masterSecret);
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    @Override
    public void onMasterSecretCleared() {
        // this.conversationListFragment.setMasterSecret(null);
        startActivity(new Intent(this, RoutingActivity.class));
        super.onMasterSecretCleared();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = this.getMenuInflater();
        menu.clear();

//    inflater.inflate(R.menu.gdata_text_secure_normal, menu);

        if (!(this.masterSecret != null && gDataPreferences.getViewPagersLastPage() == 0)) {
            menu.clear();
        }

        super.onPrepareOptionsMenu(menu);
        return true;
    }
    private void initializeSearch(MenuItem searchViewItem) {
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchViewItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (conversationListFragment != null) {
                    conversationListFragment.setQueryFilter(query);
                    return true;
                }

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return onQueryTextSubmit(newText);
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (conversationListFragment != null) {
                    conversationListFragment.resetQueryFilter();
                }

                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_new_message:
                openSingleContactSelection();
                return true;
            case R.id.menu_new_group:
                createGroup();
                return true;
            case R.id.menu_settings:
                handleDisplaySettings();
                return true;
            case R.id.menu_clear_passphrase:
                handleClearPassphrase();
                return true;
            case R.id.menu_mark_all_read:
                handleMarkAllRead();
                return true;
            case R.id.menu_import_export:
                handleImportExport();
                return true;
            case R.id.menu_privacy:
                openPasswordDialogWithAction(CheckPasswordDialogFrag.ACTION_OPEN_PRIVACY);
                return true;
            case R.id.menu_privacy_hide:
                openPasswordDialogWithAction(CheckPasswordDialogFrag.ACTION_TOGGLE_VISIBILITY);
                return true;
            case R.id.menu_filter:
                openPasswordDialogWithAction(CheckPasswordDialogFrag.ACTION_OPEN_CALL_FILTER);
                return true;
            case R.id.menu_my_identity:
                handleMyIdentity();
                return true;
        }
        return false;
    }

    @Override
    public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
        createConversation(threadId, recipients, distributionType);
    }

    private void createGroup() {
        Intent intent = new Intent(this, GroupCreateActivity.class);
        intent.putExtra("master_secret", masterSecret);
        startActivity(intent);
    }

    private void openSingleContactSelection() {
        Intent intent = new Intent(this, NewConversationActivity.class);
        intent.putExtra(NewConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
        startActivity(intent);
    }

    private void createConversation(long threadId, Recipients recipients, int distributionType) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
        intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

        startActivity(intent);
    }

    private void handleDisplaySettings() {
        Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
        preferencesIntent.putExtra("master_secret", masterSecret);
        startActivity(preferencesIntent);
    }

    private void handleClearPassphrase() {
        Intent intent = new Intent(this, KeyCachingService.class);
        intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
        startService(intent);
    }

    private void handleImportExport() {
        final Intent intent = new Intent(this, ImportExportActivity.class);
        intent.putExtra("master_secret", masterSecret);
        startActivity(intent);
    }

    private void handleMyIdentity() {
        final Intent intent = new Intent(this, ViewLocalIdentityActivity.class);
        intent.putExtra("master_secret", masterSecret);
        startActivity(intent);
    }

    private void handleMarkAllRead() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                DatabaseFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
                MessageNotifier.updateNotification(ConversationListActivity.this, masterSecret);
                return null;
            }
        }.execute();
        Intent intent = new Intent(PushDecryptJob.ACTION_RELOAD_HEADER);
        LocalBroadcastManager.getInstance(GService.appContext).sendBroadcast(intent);
    }

    private void initializeContactUpdatesReceiver() {
        observer = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // TODO only clear updated recipients from cache
                RecipientFactory.clearCache();
                ConversationListActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if ((conversationListFragment != null &&
                                ((ConversationListAdapter) conversationListFragment.getListAdapter()) != null)) {
                            ((ConversationListAdapter) conversationListFragment.getListAdapter()).notifyDataSetChanged();
                        }
                    }
                });
            }
        };

        getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer);
    }

    private void initializeResources() {
        this.masterSecret = getIntent().getParcelableExtra("master_secret");
        this.conversationListFragment.setMasterSecret(masterSecret);
    }

    public class PagerAdapter extends FragmentPagerAdapter {
        public static final java.lang.String EXTRA_FRAGMENT_PAGE_TITLE = "pageTitle";

        public PagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getItem(position).getArguments().getString(EXTRA_FRAGMENT_PAGE_TITLE);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(int position) {
            return position == 0 ? conversationListFragment : contactSelectionFragment;
        }
    }

    private void initViewPagerLayout() {
        conversationListFragment = ConversationListFragment.newInstance(getString(R.string.gdata_conversation_list_page_title));
        contactSelectionFragment = ContactSelectionFragment.newInstance(getString(R.string.gdata_contact_selection_page_title));

        ViewPager vpPager = (ViewPager) findViewById(R.id.pager);
        PagerAdapter adapterViewPager = new PagerAdapter(getSupportFragmentManager());
        vpPager.setAdapter(adapterViewPager);
        vpPager.setCurrentItem(gDataPreferences.getViewPagersLastPage());

        mSlidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        Integer[] iconResourceArray = {R.drawable.stock_sms_gray,
                R.drawable.ic_tab_contacts};

        mSlidingTabLayout.setIconResourceArray(iconResourceArray);
        mSlidingTabLayout.setViewPager(vpPager);

        initializeResources();
        initializeContactUpdatesReceiver();

        DirectoryRefreshListener.schedule(this);

        mSlidingTabLayout.setCustomTabColorizer(new SlidingTabLayout.TabColorizer() {

            @Override
            public int getIndicatorColor(int position) {
                return getResources().getColor(R.color.white);    //define any color in xml resources and set it here, I have used white
            }

            @Override
            public int getDividerColor(int position) {
                return getResources().getColor(R.color.transparent);
            }
        });
        mSlidingTabLayout.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {

            }

            @Override
            public void onPageSelected(int i) {
                gDataPreferences.setViewPagerLastPage(i);
                supportInvalidateOptionsMenu();
                toggleActionFloatMenu(i != 0, true, false);
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });
    }

    public void startCheckingPassword() {
        boolean pwCorrect = GService.isPasswordCorrect(inputText);
        if (pwCorrect || GService.isNoPasswordSet()) {
            openISFAActivity();
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.privacy_pw_dialog_toast_wrong), Toast.LENGTH_LONG).show();
        }
    }

    private static int ACTION_ID = 0;

    @SuppressLint("ValidFragment")
    class CheckPasswordDialogFrag extends DialogFragment {
        private EditText input;
        private LinearLayout layout;
        private TextView hint;
        private static final int ACTION_OPEN_PRIVACY = 0;
        private static final int ACTION_TOGGLE_VISIBILITY = 1;
        private static final int ACTION_OPEN_CALL_FILTER = 2;

        private Context mContext;

        CheckPasswordDialogFrag newInstance() {
            input = new EditText(getActivity());
            mContext = getActivity();
            hint = new TextView(mContext);
            layout = new LinearLayout(mContext);
            layout.setOrientation(LinearLayout.VERTICAL);
            hint.setText(getString(R.string.privacy_pw_dialog_hint));
            hint.setPadding(10, 0, 0, 0);
            LinearLayout.LayoutParams LLParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            layout.setLayoutParams(LLParams);

            layout.addView(input);
            layout.addView(hint);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setGravity(Gravity.CENTER | Gravity.BOTTOM);
            input.setTransformationMethod(PasswordTransformationMethod.getInstance());
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput((input), InputMethodManager.SHOW_IMPLICIT);
            CheckPasswordDialogFrag fragment = new CheckPasswordDialogFrag();
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            newInstance();
            return new AlertDialog.Builder(getActivity())
                    .setIcon(R.drawable.icon_lock)
                    .setTitle(getString(R.string.privacy_pw_dialog_header))
                    .setView(layout)
                    .setPositiveButton(getString(R.string.picker_set),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    inputText = input.getText().toString();
                                    startCheckingPassword();
                                }
                            })
                    .setNegativeButton(getString(R.string.ExportFragment_cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    dialog.cancel();
                                }
                            }).create();
        }


    }

    private void openISFAActivity() {
        if (ACTION_ID == CheckPasswordDialogFrag.ACTION_OPEN_PRIVACY) {
            try {
                Intent intent = new Intent("de.gdata.mobilesecurity.privacy.PrivacyListActivity");
                intent.putExtra("title", getString(R.string.app_name));
                intent.putExtra("header", getString(R.string.menu_privacy_single_hide));
                intent.putExtra("numberpicker_allow_wildcard", false);
                startActivity(intent);
            } catch (Exception e) {
            }
        } else if (ACTION_ID == CheckPasswordDialogFrag.ACTION_TOGGLE_VISIBILITY) {
            gDataPreferences.setPrivacyActivated(!gDataPreferences.isPrivacyActivated());
            reloadAdapter();
            String toastText = gDataPreferences.isPrivacyActivated()
                    ? getApplicationContext().getString(R.string.privacy_pw_dialog_toast_hide) : getApplicationContext().getString(R.string.privacy_pw_dialog_toast_reload);

            Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                invalidateOptionsMenu();
            } else {
                supportInvalidateOptionsMenu();
            }
            initNavDrawer(navLabels, navIcons);
        } else if (ACTION_ID == CheckPasswordDialogFrag.ACTION_OPEN_CALL_FILTER) {
            try {
                Intent intent = new Intent("de.gdata.mobilesecurity.activities.filter.FilterListActivity");
                intent.putExtra("title", getString(R.string.app_name));
                startActivity(intent);
            } catch (Exception e) {
                Log.d("GDATA", "Activity not found " + e.toString());
            }
        }
    }

    public void openPasswordDialogWithAction(int action) {
        if (GUtil.featureCheck(getApplicationContext(), true)) {
            ACTION_ID = action;
            if (GService.isNoPasswordSet()) {
                startCheckingPassword();
            } else {
                new CheckPasswordDialogFrag().show(getSupportFragmentManager(), "PW_DIALOG_TAG");
            }
        }
    }

    public void reloadAdapter() {
        if (conversationListFragment != null) {
            conversationListFragment.reloadAdapter();
        }
        if (contactSelectionFragment != null) {
            contactSelectionFragment.reloadAdapter();
        }
    }
}
