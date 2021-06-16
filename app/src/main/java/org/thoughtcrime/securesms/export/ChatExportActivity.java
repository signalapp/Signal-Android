package org.thoughtcrime.securesms.export;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ActivityTransitionUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FullscreenHelper;

public class ChatExportActivity extends PassphraseRequiredActivity {

    private static final String RECIPIENT_ID      = "RECIPIENT_ID";

    private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    public static Intent createIntent(Context requireContext, RecipientId recipientId) {
        Intent intent = new Intent(requireContext, ChatExportActivity.class);
        intent.putExtra(RECIPIENT_ID, recipientId);
        return intent;
    }

    @Override
    protected void onPreCreate() {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, boolean ready) {
        ChatExportViewModel.Factory factory = new ChatExportViewModel.Factory(getIntent().getParcelableExtra(RECIPIENT_ID));
        ViewModelProviders.of(this, factory).get(ChatExportViewModel.class);

        dynamicTheme.onCreate(this);
        setContentView(R.layout.chat_export_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        new FullscreenHelper (this).showSystemUI();

        toolbar.setNavigationOnClickListener(unused -> {
            if (!Navigation.findNavController(this, R.id.nav_host_fragment).popBackStack()) {
                finish();
                ActivityTransitionUtil.setSlideOutTransition(this);
            }
        });

        if (savedInstanceState == null) {
            Bundle   extras = getIntent().getExtras();
            NavGraph graph  = Navigation.findNavController(this, R.id.nav_host_fragment).getGraph();

            Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(graph, extras != null ? extras : new Bundle());
        }
    }

    @Override
    public void onBackPressed() {
        ActivityTransitionUtil.setSlideOutTransition(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
    }
}
