package org.thoughtcrime.securesms;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;

public class ViewProfilePhotoActivity extends AppCompatActivity {

    private static final String TAG = ViewProfilePhotoActivity.class.getSimpleName();

    private ZoomingImageView avatar;
    private RecipientId  recipientId;

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTheme(R.style.TextSecure_MediaPreview);
        setContentView(R.layout.activity_view_profile_photo);

        setSupportActionBar(findViewById(R.id.toolbar));

        getSupportActionBar().setTitle(getIntent().hasExtra(Intent.EXTRA_TITLE) ? getIntent().getExtras().getString(Intent.EXTRA_TITLE) : Recipient.self().getDisplayName());

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initializeResource();

        if(getIntent().hasExtra(MediaStore.EXTRA_OUTPUT)) recipientId  = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);

        initializeProfileAvatar(recipientId);
    }

    private void initializeResource() {
        this.avatar = findViewById(R.id.avatar);
    }

    private void initializeProfileAvatar(RecipientId recipientId) {
        if(recipientId == null)  recipientId = Recipient.self().getId();

        if (AvatarHelper.getAvatarFile(this, recipientId).exists() && AvatarHelper.getAvatarFile(this, recipientId).length() > 0) {
            RecipientId finalRecipientId = recipientId;
            new AsyncTask<Void, Void, Uri>() {
                @Override
                protected Uri doInBackground(Void... voids) {
                    return Uri.fromFile(AvatarHelper.getAvatarFile(ViewProfilePhotoActivity.this,finalRecipientId));
                }

                @Override
                protected void onPostExecute(Uri result) {
                    super.onPostExecute(result);
                    if (result != null) {
                        avatar.setImageUri(GlideApp.with(ViewProfilePhotoActivity.this),result, MediaUtil.IMAGE_JPEG);
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }
}
