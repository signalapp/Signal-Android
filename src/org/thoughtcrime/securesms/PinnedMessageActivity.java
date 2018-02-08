package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ViewUtil;

public class PinnedMessageActivity extends PassphraseRequiredActionBarActivity
        implements PinnedMessageFragment.PinnedMessageFragmentListener {

    private static final String TAG = PinnedMessageActivity.class.getSimpleName();
    public static final String ADDRESS_EXTRA   = "address";

    private   MasterSecret                masterSecret;
    private   long                        threadId;
    private   PinnedMessageFragment       fragment;
    private   Toolbar                     toolbar;
    private   Recipient                   recipient;

    private final DynamicTheme dynamicTheme    = new DynamicNoActionBarTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    @Override
    protected void onPreCreate() {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
        Log.w(TAG, "onCreate()");


        this.masterSecret = masterSecret;

        setContentView(R.layout.pinned_message_activity);

        fragment = initFragment(R.id.fragment_content, new PinnedMessageFragment(),
                masterSecret, dynamicLanguage.getCurrentLocale());

        initializeResources();
        initializeToolbar();
    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);
    }

    @Override
    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
        }

        return false;
    }

    private void initializeResources() {
        Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);
        this.threadId   = getIntent().getLongExtra("THREADID", -1);

//        this.viewPager = ViewUtil.findById(this, R.id.pager);
//        this.toolbar   = ViewUtil.findById(this, R.id.toolbar);
//        this.tabLayout = ViewUtil.findById(this, R.id.tab_layout);

        this.toolbar   = ViewUtil.findById(this, R.id.toolbar);
        this.recipient = Recipient.from(this, address, true);
    }

    private void initializeToolbar() {
        setSupportActionBar(this.toolbar);
        getSupportActionBar().setTitle(recipient.toShortString());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        this.recipient.addListener(recipient -> getSupportActionBar().setTitle(recipient.toShortString()));
    }
}
