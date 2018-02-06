package org.thoughtcrime.securesms;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v4.view.WindowCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.ViewUtil;

public class PinnedMessageActivity extends PassphraseRequiredActionBarActivity
        implements PinnedMessageFragment.PinnedMessageFragmentListener {

    private static final String TAG = PinnedMessageActivity.class.getSimpleName();
    public static final String ADDRESS_EXTRA   = "address";

    private   MasterSecret                masterSecret;
    private   long                        threadId;
    private   PinnedMessageFragment       fragment;
    private   Toolbar                     toolbar;
    private   TabLayout                   tabLayout;
    private   ViewPager                   viewPager;
    private   Recipient                   recipient;

    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    @Override
    protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
        Log.w(TAG, "onCreate()");
        this.masterSecret = masterSecret;

        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
        setContentView(R.layout.pinned_message_activity);

        TypedArray typedArray = obtainStyledAttributes(new int[] {R.attr.conversation_background});
        int color = typedArray.getColor(0, Color.WHITE);
        typedArray.recycle();

        getWindow().getDecorView().setBackgroundColor(color);

        fragment = initFragment(R.id.fragment_content, new PinnedMessageFragment(),
                masterSecret, dynamicLanguage.getCurrentLocale());

        initializeResources();
        initializeToolbar();
    }

    @Override
    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    private void initializeResources() {
        Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

//        this.viewPager = ViewUtil.findById(this, R.id.pager);
//        this.toolbar   = ViewUtil.findById(this, R.id.toolbar);
//        this.tabLayout = ViewUtil.findById(this, R.id.tab_layout);
        this.recipient = Recipient.from(this, address, true);
    }

    private void initializeToolbar() {
//        setSupportActionBar(this.toolbar);
//        getSupportActionBar().setTitle(recipient.toShortString());
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        this.recipient.addListener(recipient -> getSupportActionBar().setTitle(recipient.toShortString()));
    }
}
