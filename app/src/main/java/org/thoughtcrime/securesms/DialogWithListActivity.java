package org.thoughtcrime.securesms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DialogWithListActivity extends Activity {

    public static final String MODE = "mode";
    public static final int FOR_MUTE = 1;
    private int mode;

    private RecyclerView mRvOption;
    private RVOptionAdapter rvOptionAdapter;
    private List<String> mData = new ArrayList<>();


    private final int viewTypeDM = 1;//disappearing Message


    public static int mFocusHeight;
    public static int mNormalHeight;
    public static int mFocusTextSize;
    public static int mNormalTextSize;
    public static int mFocusPaddingX;
    public static int mNormalPaddingX;
    private int currentExpiration;
    private RecipientId recipientId;
    private int[] expirationTimes;
    private long[] muteTime;
    private LiveRecipient recipient;
    private long threadId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_with_list);

        initView();

        initData();

    }

    private void initData() {

//        intent.putExtra(STRING_CURRENT_EXPIRATION,recipient.get().getExpireMessages());
//        intent.putExtra(RECIPIENT_EXTRA,recipient.getId());
        Intent intent = getIntent();
        mode = intent.getIntExtra(MODE, 0);
        if (mode == FOR_MUTE) {
            recipientId = intent.getParcelableExtra(ConversationActivity.RECIPIENT_EXTRA);
        } else {
            currentExpiration = intent.getIntExtra(ConversationActivity.STRING_CURRENT_EXPIRATION, -1);
            recipientId = intent.getParcelableExtra(ConversationActivity.RECIPIENT_EXTRA);
            recipient = Recipient.live(getIntent().getParcelableExtra(ConversationActivity.RECIPIENT_EXTRA));
            threadId = getIntent().getLongExtra(ConversationActivity.THREAD_ID_EXTRA, -1);
        }

        Resources res = getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

        String[] expirationDisplayValues;
        if (mode == FOR_MUTE) {
            muteTime = new long[5];
            muteTime[0]  = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            muteTime[1]  = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
            muteTime[2]  = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
            muteTime[3]  = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7);
            muteTime[4]  = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365);

            expirationDisplayValues = getResources().getStringArray(R.array.mute_durations);
        } else {
            expirationTimes = getResources().getIntArray(R.array.expiration_times);
            expirationDisplayValues = new String[expirationTimes.length];
            int selectedIndex = expirationTimes.length - 1;
            for (int i = 0; i < expirationTimes.length; i++) {
                expirationDisplayValues[i] = ExpirationUtil.getExpirationDisplayValue(this, expirationTimes[i]);

                if ((currentExpiration >= expirationTimes[i]) &&
                        (i == expirationTimes.length - 1 || currentExpiration < expirationTimes[i + 1])) {
                    selectedIndex = i;
                }
            }
        }

        mData = Arrays.asList(expirationDisplayValues);
        rvOptionAdapter = new RVOptionAdapter(mData,this);
        mRvOption.setAdapter(rvOptionAdapter);
//        new AsyncTask<Void, Void, Void>() {
//            @Override
//            protected Void doInBackground(Void... params) {
//                DatabaseFactory.getRecipientDatabase(DialogWithListActivity.this).setExpireMessages(recipient.getId(), expirationTime);
//                OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipient(), System.currentTimeMillis(), expirationTime * 1000L);
//                MessageSender.send(DialogWithListActivity.this, outgoingMessage, threadId, false, null);
//
//                return null;
//            }
//
//            @Override
//            protected void onPostExecute(Void result) {
//                invalidateOptionsMenu();
////                if (fragment != null) fragment.setLastSeen(0);
//            }
//        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void initView() {
        mRvOption = ViewUtil.findById(this, R.id.rv_options);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRvOption.setClipToPadding(false);
        mRvOption.setClipChildren(false);
        mRvOption.setPadding(0, 76, 0, 200);
        mRvOption.setLayoutManager(layoutManager);


    }


    class RVOptionAdapter extends RecyclerView.Adapter<RVOptionAdapter.ViewHolder> {

        private List<String> mData;
        private Context mContext;

        public RVOptionAdapter(List<String> mData, Context mContext) {
            this.mData = mData;
            this.mContext = mContext;
        }

        public void setData(List<String> mData) {
            this.mData = mData;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == viewTypeDM) {
                View view =  LayoutInflater.from(mContext).inflate(R.layout.item_option_rv, parent, false);;
                TextView mTvOption = view.findViewById(R.id.tv_options);
                CheckBox mCbOption = view.findViewById(R.id.cb_options);

                mTvOption.setOnFocusChangeListener((view1, hasFocused) -> {
                    updateFocusView(view, mTvOption, hasFocused);
                });


                return new ViewHolder(view);
            }
            return null;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.tvOption.setText(mData.get(position));
            holder.tvOption.setOnClickListener( v -> {
                if (mode == FOR_MUTE) {
                    SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getRecipientDatabase(getApplicationContext()).setMuted(recipientId, muteTime[position]));
                    DialogWithListActivity.this.finish();
                } else {
                    itemClick(position);/*mCbOption.setChecked(true);notifyDataSetChanged();*/
                 }
                });
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        @Override
        public int getItemViewType(int position) {
            return viewTypeDM;
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            public TextView tvOption;
            public CheckBox mCbOption;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOption = itemView.findViewById(R.id.tv_options);
                mCbOption = itemView.findViewById(R.id.cb_options);
            }


        }
    }

    private void itemClick(int position) {
        int expirationTime = expirationTimes[position];

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getRecipientDatabase(
                  DialogWithListActivity.this).setExpireMessages(recipientId, expirationTime);
          OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipient(), System.currentTimeMillis(), expirationTime * 1000L);
          MessageSender.send(DialogWithListActivity.this, outgoingMessage, threadId, false, null,null);

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          invalidateOptionsMenu();
          DialogWithListActivity.this.finish();
        }


      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    protected Recipient getRecipient() {
        return this.recipient.get();
    }

    private void updateFocusView(View parent, TextView tv, boolean itemFocus) {

        ValueAnimator va;
        if (itemFocus) {
            va = ValueAnimator.ofFloat(0, 1);
        } else {
            va = ValueAnimator.ofFloat(1, 0);
        }

        va.addUpdateListener(valueAnimator -> {
            float scale = (float) valueAnimator.getAnimatedValue();
            float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
            float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
            float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
            int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
            int color = alpha * 0x1000000 + 0xffffff;

            tv.setTextColor(color);
            parent.setPadding((int) padding, parent.getPaddingTop(), parent.getPaddingRight(), parent.getPaddingBottom());

            tv.setTextSize((int) textsize);
            tv.getLayoutParams().height = (int) height;
            parent.getLayoutParams().height = (int) (height);

        });

        FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(FastOutLinearInInterpolator);
        if (itemFocus) {
            va.setDuration(270);
            va.start();
        } else {
            va.setDuration(270);
            va.start();
        }
    }
}
