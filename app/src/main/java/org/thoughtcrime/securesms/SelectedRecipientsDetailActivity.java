package org.thoughtcrime.securesms;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.conversation.SimSaveOptionsActivity;

public class SelectedRecipientsDetailActivity extends Activity {
    public static int mFocusHeight;
    public static int mNormalHeight;
    public static int mFocusTextSize;
    public static int mNormalTextSize;
    public static int mFocusPaddingX;
    public static int mNormalPaddingX;
    private RecyclerView mRecy;
    private SelectedRecipientsDetailAdapter mAdapter;
    private String name;
    private String number;
    private String addToContact;
    private Intent mIntentAdd;
    private Intent mIntentExist;
    private Boolean isContactAdded = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.selected_recipients_detail);
        Intent intent = getIntent();
        name = intent.getStringExtra("name");
        number = intent.getStringExtra("number");
        mIntentAdd = (Intent)intent.getParcelableExtra("intentAdd");
        mIntentExist = (Intent)intent.getParcelableExtra("intentExist");
        addToContact = getString(R.string.conversation_add_to_contacts__menu_add_to_contacts);
        mRecy = findViewById(R.id.selected_recipients_recy);
        mAdapter = new SelectedRecipientsDetailAdapter(this);
        mRecy.setAdapter(mAdapter);
        isContactAdded = isContact(number);

        LinearLayoutManager manager = new LinearLayoutManager(this);
        mRecy.setLayoutManager(manager);

        mRecy.setClipToPadding(false);
        mRecy.setClipChildren(false);
        mRecy.setPadding(0, 76, 0, 200);

        Resources res = getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
    }

    public class SelectedRecipientsDetailAdapter extends RecyclerView.Adapter<SelectedRecipientsDetailAdapter.ViewHolder> {
        private Context mContext;

        public SelectedRecipientsDetailAdapter(Context mContext) {
            this.mContext = mContext;
        }

        @NonNull
        @Override
        public SelectedRecipientsDetailAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View selectedView = LayoutInflater.from(mContext).inflate(R.layout.selected_detail_twoline_item, parent, false);
            TextView detailTitle = selectedView.findViewById(R.id.selected_item_twoline_title);
            TextView detailMsg = selectedView.findViewById(R.id.selected_item_twoline_msg);
            detailMsg.setVisibility(View.VISIBLE);
            selectedView.setOnFocusChangeListener((v, hasFocus) -> {
                updateFocusView(v, detailTitle, detailMsg, hasFocus);

            });
            return new ViewHolder(selectedView);
        }

        private void updateFocusView(View parent, TextView tv, TextView et, boolean itemFocus) {
            ValueAnimator va;
            if (itemFocus) {
                va = ValueAnimator.ofFloat(0, 1);
            } else {
                va = ValueAnimator.ofFloat(1, 0);
            }
            va.addUpdateListener(valueAnimator -> {
                float scale = (float) valueAnimator.getAnimatedValue();
                float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
                float editHeight = (float) (mFocusHeight) * (scale);
                float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
                float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
                int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
                int color = alpha * 0x1000000 + 0xffffff;

                tv.setTextColor(color);
                parent.setPadding((int) padding, parent.getPaddingTop(), parent.getPaddingRight(), parent.getPaddingBottom());
                if (et == null) {
                    tv.setTextSize((int) textsize);
                    tv.getLayoutParams().height = (int) height;
                    parent.getLayoutParams().height = (int) (height);
                } else {
                    tv.setTextSize(mNormalTextSize);
                    et.setTextSize((int) textsize);
                    et.setTextColor(color);
                    et.getLayoutParams().height = (int) editHeight;
                    parent.getLayoutParams().height = (int) (editHeight + mNormalHeight);
                }
            });
            FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
            va.setInterpolator(FastOutLinearInInterpolator);
            if (itemFocus) {
                if (tv.getText().equals(addToContact)){
                    tv.setVisibility(View.GONE);
                }
                et.setSelected(true);
                va.setDuration(270);
                va.start();
            } else {
                et.setSelected(false);
                tv.setVisibility(View.VISIBLE);
                va.setDuration(270);
                va.start();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull SelectedRecipientsDetailAdapter.ViewHolder holder, int position) {
            TextView detailTitle = holder.itemView.findViewById(R.id.selected_item_twoline_title);
            TextView detailMsg = holder.itemView.findViewById(R.id.selected_item_twoline_msg);
            detailTitle.setText(R.string.preferences__conversation_length_limit);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int layoutPosition= holder.getLayoutPosition();
                    if (holder.getLayoutPosition() == 2){
                        addToContacts();
                    }
                }
            });
            if (position == 0) {
                detailTitle.setText(getResources().getString(R.string.selected_user_name));
                detailMsg.setText(name);
            }else if(position == 1){
                detailTitle.setText(getResources().getString(R.string.selected_phone_number));
                detailMsg.setText(number);
            } else if (position == 2) {
                detailTitle.setText(addToContact);
                detailMsg.setText(addToContact);
            }
        }

        @Override
        public int getItemCount() {
            if (isContactAdded) {
                return 2;
            }
            return 3;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
    private void addToContacts() {
            Intent intent = new Intent(SelectedRecipientsDetailActivity.this, SimSaveOptionsActivity.class);
            intent.putExtra("intentAdd", mIntentAdd);
            intent.putExtra("intentExist", mIntentExist);
            startActivity(intent);
    }

    private boolean isContact(String recipientNumber) {
        Boolean isAdded = false;
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 1);
        } else {
            Cursor cursor = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null);
                }
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String contactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        if (contactNumber.equals(recipientNumber)) {
                            isAdded = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return isAdded;
    }
}
