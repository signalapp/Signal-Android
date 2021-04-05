package org.thoughtcrime.securesms.giph.ui;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.DynamicDarkToolbarTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class GiphyActivity extends PassphraseRequiredActivity
    implements GiphyActivityToolbar.OnLayoutChangedListener,
               GiphyActivityToolbar.OnFilterChangedListener,
               GiphyAdapter.OnItemClickListener
{

  private static final String TAG = GiphyActivity.class.getSimpleName();

  public static final String EXTRA_IS_MMS     = "extra_is_mms";
  public static final String EXTRA_WIDTH      = "extra_width";
  public static final String EXTRA_HEIGHT     = "extra_height";
  public static final String EXTRA_COLOR      = "extra_color";
  public static final String EXTRA_BORDERLESS = "extra_borderless";

  private final DynamicTheme    dynamicTheme    = new DynamicDarkToolbarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private GiphyGifFragment     gifFragment;
  private GiphyStickerFragment stickerFragment;
  private boolean              forMms;

  private GiphyAdapter.GiphyViewHolder finishingImage;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.giphy_activity);

    initializeToolbar();
    initializeResources();
  }

  private void initializeToolbar() {

    GiphyActivityToolbar toolbar = findViewById(R.id.giphy_toolbar);
    toolbar.setOnFilterChangedListener(this);
    toolbar.setOnLayoutChangedListener(this);
    toolbar.setPersistence(GiphyActivityToolbarTextSecurePreferencesPersistence.fromContext(this));

    final int conversationColor = getConversationColor();
    toolbar.setBackgroundColor(conversationColor);
    WindowUtil.setStatusBarColor(getWindow(), conversationColor);

    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    ViewPager viewPager = findViewById(R.id.giphy_pager);
    TabLayout tabLayout = findViewById(R.id.tab_layout);

    this.gifFragment     = new GiphyGifFragment();
    this.stickerFragment = new GiphyStickerFragment();
    this.forMms          = getIntent().getBooleanExtra(EXTRA_IS_MMS, false);

    gifFragment.setClickListener(this);
    stickerFragment.setClickListener(this);

    viewPager.setAdapter(new GiphyFragmentPagerAdapter(this, getSupportFragmentManager(),
                                                       gifFragment, stickerFragment));
    tabLayout.setupWithViewPager(viewPager);
    tabLayout.setBackgroundColor(getConversationColor());
  }

  private @ColorInt int getConversationColor() {
    return getIntent().getIntExtra(EXTRA_COLOR, ActivityCompat.getColor(this, R.color.core_ultramarine));
  }

  @Override
  public void onFilterChanged(String filter) {
    this.gifFragment.setSearchString(filter);
    this.stickerFragment.setSearchString(filter);
  }

  @Override
  public void onLayoutChanged(boolean gridLayout) {
    gifFragment.setLayoutManager(gridLayout);
    stickerFragment.setLayoutManager(gridLayout);
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onClick(final GiphyAdapter.GiphyViewHolder viewHolder) {
    if (finishingImage != null) finishingImage.gifProgress.setVisibility(View.GONE);
    finishingImage = viewHolder;
    finishingImage.gifProgress.setVisibility(View.VISIBLE);

    new AsyncTask<Void, Void, Uri>() {
      @Override
      protected Uri doInBackground(Void... params) {
        try {
          byte[] data = viewHolder.getData(forMms);

          return BlobProvider.getInstance()
                             .forData(data)
                             .withMimeType(MediaUtil.IMAGE_GIF)
                             .createForSingleSessionOnDisk(GiphyActivity.this);
        } catch (InterruptedException | ExecutionException | IOException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      protected void onPostExecute(@Nullable Uri uri) {
        if (uri == null) {
          Toast.makeText(GiphyActivity.this, R.string.GiphyActivity_error_while_retrieving_full_resolution_gif, Toast.LENGTH_LONG).show();
        } else if (viewHolder == finishingImage) {
          Intent intent = new Intent();
          intent.setData(uri);
          intent.putExtra(EXTRA_WIDTH, viewHolder.image.getGifWidth());
          intent.putExtra(EXTRA_HEIGHT, viewHolder.image.getGifHeight());
          intent.putExtra(EXTRA_BORDERLESS, viewHolder.image.isSticker());
          setResult(RESULT_OK, intent);
          finish();
        } else {
          Log.w(TAG, "Resolved Uri is no longer the selected element...");
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static class GiphyFragmentPagerAdapter extends FragmentPagerAdapter {

    private final Context              context;
    private final GiphyGifFragment     gifFragment;
    private final GiphyStickerFragment stickerFragment;

    private GiphyFragmentPagerAdapter(@NonNull Context context,
                                      @NonNull FragmentManager fragmentManager,
                                      @NonNull GiphyGifFragment gifFragment,
                                      @NonNull GiphyStickerFragment stickerFragment)
    {
      super(fragmentManager);
      this.context         = context.getApplicationContext();
      this.gifFragment     = gifFragment;
      this.stickerFragment = stickerFragment;
    }

    @Override
    public Fragment getItem(int position) {
      if (position == 0) return gifFragment;
      else               return stickerFragment;
    }

    @Override
    public int getCount() {
      return 2;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      if (position == 0) return context.getString(R.string.GiphyFragmentPagerAdapter_gifs);
      else               return context.getString(R.string.GiphyFragmentPagerAdapter_stickers);
    }
  }

}
