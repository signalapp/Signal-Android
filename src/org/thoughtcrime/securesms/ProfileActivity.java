package org.thoughtcrime.securesms;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;


public class ProfileActivity extends PassphraseRequiredActionBarActivity {

    private MasterSecret masterSecret;
    public static final int PICK_IMAGE = 1;
    public static final int TAKE_PHOTO = 2;
    public static final int AVATAR_SIZE = 410;
    private DynamicTheme dynamicTheme = new DynamicTheme();
    private String profileId;
    private static Bitmap avatarBmp;
    private boolean isGroup;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        dynamicTheme.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_activity);
        setStatusBarColor();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getIntent().getStringExtra("profile_name"));
        getSupportActionBar().setSubtitle(getIntent().getStringExtra("profile_number"));
        getSupportActionBar().hide();
        initializeResources();
        this.overridePendingTransition(R.anim.slide_from_top,
                R.anim.slide_out_top);
        Window window = this.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    }

    public void setStatusBarColor() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            int statusBarColor = Color.parseColor("#00000000");

            if (statusBarColor == Color.BLACK && window.getNavigationBarColor() == Color.BLACK) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            }
            window.setStatusBarColor(statusBarColor);
        }
    }

    @Override
    public void onResume() {
        dynamicTheme.onResume(this);
        super.onResume();
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

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void initializeResources() {
        this.masterSecret = getIntent().getParcelableExtra("master_secret");
        this.profileId = getIntent().getStringExtra("profile_id");
        this.isGroup = getIntent().getBooleanExtra("is_group", false);
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        Log.w("", "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
        super.onActivityResult(reqCode, resultCode, data);
            switch (reqCode) {
                case PICK_IMAGE:
                    if (data != null) {
                        try {
                            OutputStream out;
                            File f = AttachmentManager.getOutputMediaFile(getApplicationContext());
                            if (f.exists()) {
                                f.delete();
                            }
                            out = new FileOutputStream(f);
                            out.write(GUtil.readBytes(getApplicationContext(), data.getData()));
                            out.close();
                            if(!isGroup) {
                                ImageSlide chosenImage = new ImageSlide(this, Uri.fromFile(f));
                                ProfileAccessor.setProfilePicture(this, chosenImage);
                            } else {
                                new DecodeCropAndSetAsyncTask(Uri.fromFile(f)).execute();
                            }
                        } catch (IOException e) {
                            Toast.makeText(getApplicationContext(),getString(R.string.MediaPreviewActivity_unssuported_media_type), Toast.LENGTH_LONG).show();
                            Log.w("GDATA", e);
                        } catch (BitmapDecodingException e) {
                            Toast.makeText(getApplicationContext(),getString(R.string.MediaPreviewActivity_unssuported_media_type), Toast.LENGTH_LONG).show();
                            Log.w("GDATA", e);
                        }
                    }
                    break;
                case TAKE_PHOTO:
                    if (resultCode == 0) {
                        return;
                    }
                        try {
                            File image = AttachmentManager.getOutputMediaFile(getApplicationContext());
                            if (image != null) {
                                Uri fileUri = Uri.fromFile(image);
                                if(!isGroup) {
                                    ImageSlide chosenImage = new ImageSlide(this, fileUri);
                                    ProfileAccessor.setProfilePicture(this, chosenImage);
                                } else {
                                    new DecodeCropAndSetAsyncTask(fileUri).execute();
                                }
                            }
                        } catch (IOException e) {
                            Log.w("GDATA", e);
                            Toast.makeText(getApplicationContext(),getString(R.string.MediaPreviewActivity_unssuported_media_type), Toast.LENGTH_LONG).show();
                        } catch (BitmapDecodingException e) {
                            Log.w("GDATA", e);
                            Toast.makeText(getApplicationContext(),getString(R.string.MediaPreviewActivity_unssuported_media_type), Toast.LENGTH_LONG).show();
                        }
                    break;
            }
    }

    private class DecodeCropAndSetAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        private final Uri avatarUri;

        DecodeCropAndSetAsyncTask(Uri uri) {
            avatarUri = uri;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            if (avatarUri != null) {
                try {
                    avatarBmp = BitmapUtil.createScaledBitmap(ProfileActivity.this, masterSecret, avatarUri, AVATAR_SIZE, AVATAR_SIZE);
                } catch (IOException | BitmapDecodingException e) {
                    Log.w("GDATA", e);
                    return null;
                }
            }
            return avatarBmp;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
        }
    }

    public static void setAvatarTemp(Bitmap avatar) {
        avatarBmp = avatar;
    }
    public static Bitmap getAvatarTemp() {
        return avatarBmp;
    }
}