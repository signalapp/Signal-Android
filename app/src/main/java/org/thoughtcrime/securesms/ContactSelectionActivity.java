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

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DisplayMetricsUtil;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Base activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class ContactSelectionActivity extends PassphraseRequiredActivity
                                               implements SwipeRefreshLayout.OnRefreshListener,
                                                          ContactSelectionListFragment.OnContactSelectedListener,
                                                          ContactSelectionListFragment.ScrollCallback
{
  private static final String TAG = Log.tag(ContactSelectionActivity.class);

  public static final String EXTRA_LAYOUT_RES_ID = "layout_res_id";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  protected ContactSelectionListFragment contactsFragment;

  private Toolbar           toolbar;
  private ContactFilterView contactFilterView;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    if (!getIntent().hasExtra(ContactSelectionListFragment.DISPLAY_MODE)) {
      boolean includeSms  = Util.isDefaultSmsProvider(this) && SignalStore.misc().getSmsExportPhase().allowSmsFeatures();
      int     displayMode = includeSms ? ContactSelectionDisplayMode.FLAG_ALL : ContactSelectionDisplayMode.FLAG_PUSH | ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS | ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS | ContactSelectionDisplayMode.FLAG_SELF;
      getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    }

    setContentView(getIntent().getIntExtra(EXTRA_LAYOUT_RES_ID, R.layout.contact_selection_activity));

    initializeContactFilterView();
    initializeToolbar();
    initializeResources();
    initializeSearch();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  protected Toolbar getToolbar() {
    return toolbar;
  }

  protected ContactFilterView getContactFilterView() {
    return contactFilterView;
  }

  private void initializeContactFilterView() {
    this.contactFilterView = findViewById(R.id.contact_filter_edit_text);

    if (getResources().getDisplayMetrics().heightPixels >= DimensionUnit.DP.toPixels(600)) {
      this.contactFilterView.focusAndShowKeyboard();
    }
  }

  private void initializeToolbar() {
    this.toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
  }

  private void initializeResources() {
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setOnRefreshListener(this);
  }

  private void initializeSearch() {
    contactFilterView.setOnFilterChangedListener(filter -> contactsFragment.setQueryFilter(filter));
  }

  @Override
  public void onRefresh() {
    new RefreshDirectoryTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getApplicationContext());
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, String number, @NonNull Consumer<Boolean> callback) {
    callback.accept(true);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number) {}

  @Override
  public void onBeginScroll() {
    hideKeyboard();
  }

  private void hideKeyboard() {
    ServiceUtil.getInputMethodManager(this)
               .hideSoftInputFromWindow(toolbar.getWindowToken(), 0);
    toolbar.clearFocus();
  }

  private static class RefreshDirectoryTask extends AsyncTask<Context, Void, Void> {

    private final WeakReference<ContactSelectionActivity> activity;

    private RefreshDirectoryTask(ContactSelectionActivity activity) {
      this.activity = new WeakReference<>(activity);
    }

    @Override
    protected Void doInBackground(Context... params) {
      try {
        ContactDiscovery.refreshAll(params[0], true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      ContactSelectionActivity activity = this.activity.get();

      if (activity != null && !activity.isFinishing()) {
        activity.contactFilterView.clear();
        activity.contactsFragment.resetQueryFilter();
      }
    }
  }
}
