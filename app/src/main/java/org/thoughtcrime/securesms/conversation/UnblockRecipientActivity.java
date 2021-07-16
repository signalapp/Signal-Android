package org.thoughtcrime.securesms.conversation;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.Mp02CustomDialog;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.preferences.CorrectedPreferenceFragment;
import org.thoughtcrime.securesms.preferences.widgets.Mp02CommonPreference;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UnblockRecipientActivity extends PassphraseRequiredActivity {
    private static final String TAG = UnblockRecipientActivity.class.getSimpleName();
    public static final String RECIPIENT_ID = "recipient";
    private static final String PREFERENCE_DELETE = "pref_key_delete";
    private static final String PREFERENCE_UNBLOCK = "pref_key_unblock";
    private RecipientId recipientId;
    public long threadId;
    public long lastSeen;

    public static @NonNull
    Intent getLaunchIntent(@NonNull Context context, @NonNull RecipientId id) {
        Intent intent = new Intent(context, UnblockRecipientActivity.class);
        intent.putExtra(UnblockRecipientActivity.RECIPIENT_ID, id);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, boolean ready) {
        setContentView(R.layout.unblock_recipient_activity);
        this.recipientId = getIntent().getParcelableExtra(RECIPIENT_ID);
        long threadId02 = getIntent().getLongExtra("threadId02", threadId);
        Bundle bundle = new Bundle();
        bundle.putLong("threadId02",threadId02);
        bundle.putParcelable(RECIPIENT_ID, recipientId);
        threadId = threadId02;
        initFragment(R.id.unblock_recipient_fragment, new UnblockRecipientFragment(), null, bundle);
    }

    public long getThreadId() {
        return threadId;
    }

    public static class UnblockRecipientFragment extends CorrectedPreferenceFragment implements Preference.OnPreferenceClickListener {
        private LiveRecipient recipient;
        private Mp02CommonPreference mDeletePref;
        private Mp02CommonPreference mUnblockPref;
        private Set<Long> deleteConversationSet;

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            recipient = Recipient.live(getArguments().getParcelable(RECIPIENT_ID));
            mDeletePref = (Mp02CommonPreference) findPreference(PREFERENCE_DELETE);
            mDeletePref.setOnPreferenceClickListener(new DeleteClickedListener());
            mUnblockPref = (Mp02CommonPreference) findPreference(PREFERENCE_UNBLOCK);
            mUnblockPref.setOnPreferenceClickListener(new UnblockClickedListener());
            Set<Long> batchSet = Collections.synchronizedSet(new HashSet<Long>());
            batchSet.add(((UnblockRecipientActivity)getActivity()).getThreadId());
            deleteConversationSet = batchSet;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.unblock_recipient_preferences);
        }

        @Override
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
            RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            recyclerView.setItemAnimator(null);
            recyclerView.setLayoutAnimation(null);
            return recyclerView;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            return false;
        }

        private class DeleteClickedListener implements Preference.OnPreferenceClickListener{

            @Override
            public boolean onPreferenceClick(Preference preference) {
                handleDelete(preference.getContext());
                return true;
            }
        }

        private class UnblockClickedListener implements Preference.OnPreferenceClickListener{

            @Override
            public boolean onPreferenceClick(Preference preference) {
                handleUnblock(preference.getContext());
                return true;
            }
        }

        private void handleDelete(@NonNull Context context) {
            int titleRes = R.plurals.ConversationListFragment_delete_selected_conversations;
            int bodyRes = R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations;

            Mp02CustomDialog dialog = new Mp02CustomDialog(context);
            dialog.setMessage(getResources().getQuantityString(titleRes, 1, 1) + '\n' + getResources().getQuantityString(bodyRes, 1, 1));
            dialog.setCancelable(true);
            dialog.setPositiveListener(R.string.delete, () -> {
                if (deleteConversationSet != null) {
                    new AsyncTask<Void, Void, Void>() {
                        private ProgressDialog progressDialog;

                        @Override
                        protected void onPreExecute() {
                            progressDialog = ProgressDialog.show(getActivity(),
                                    getActivity().getString(R.string.ConversationListFragment_deleting),
                                    getActivity().getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                    true, false);
                        }

                        @Override
                        protected Void doInBackground(Void... params) {
                            DatabaseFactory.getThreadDatabase(getActivity()).deleteConversations(deleteConversationSet);
                            ApplicationDependencies.getMessageNotifier().updateNotification(getActivity());
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void result) {
                            progressDialog.dismiss();
                            getActivity().finish();
                            ConversationActivity.instance.finish();
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            });
            dialog.setNegativeListener(android.R.string.cancel, null);
            dialog.show();
        }

        private void handleUnblock(@NonNull Context context) {
            int titleRes = R.string.ConversationActivity_unblock_this_contact_question;
            int bodyRes = R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact;

            if (recipient.resolve().isGroup()) {
                titleRes = R.string.ConversationActivity_unblock_this_group_question;
                bodyRes = R.string.ConversationActivity_unblock_this_group_description;
            }
            Mp02CustomDialog dialog = new Mp02CustomDialog(context);
            dialog.setMessage(getString(titleRes) + '\n' + getString(bodyRes));
            dialog.setCancelable(true);
            dialog.setPositiveListener(R.string.RecipientPreferenceActivity_unblock, () -> {
                SignalExecutors.BOUNDED.execute(() -> {
                    RecipientUtil.unblock(context, recipient.get());
                    getActivity().finish();
                });
            });
            dialog.setNegativeListener(android.R.string.cancel, null);
            dialog.show();
        }

    }
}
