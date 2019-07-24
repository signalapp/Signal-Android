/**
 * Copyright (C) 2016 Open Whisper Systems
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
package org.thoughtcrime.securesms.scribbles;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.MenuItem;

import org.thoughtcrime.securesms.R;

public class StickerSelectActivity extends FragmentActivity implements StickerSelectFragment.StickerSelectionListener {

  private static final String TAG = StickerSelectActivity.class.getSimpleName();

  public static final String EXTRA_STICKER_FILE = "extra_sticker_file";

  private static final int[] TAB_TITLES = new int[] {
      R.drawable.ic_tag_faces_white_24dp,
      R.drawable.ic_work_white_24dp,
      R.drawable.ic_pets_white_24dp,
      R.drawable.ic_local_dining_white_24dp,
      R.drawable.ic_wb_sunny_white_24dp
  };

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.scribble_select_sticker_activity);

    ViewPager viewPager = (ViewPager) findViewById(R.id.camera_sticker_pager);
    viewPager.setAdapter(new StickerPagerAdapter(getSupportFragmentManager(), this));

    TabLayout tabLayout = (TabLayout) findViewById(R.id.camera_sticker_tabs);
    tabLayout.setupWithViewPager(viewPager);

    for (int i=0;i<tabLayout.getTabCount();i++) {
      tabLayout.getTabAt(i).setIcon(TAB_TITLES[i]);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onStickerSelected(String name) {
    Intent intent = new Intent();
    intent.putExtra(EXTRA_STICKER_FILE, name);
    setResult(RESULT_OK, intent);
    finish();
  }

  static class StickerPagerAdapter extends FragmentStatePagerAdapter {

    private final Fragment[] fragments;

    StickerPagerAdapter(FragmentManager fm, StickerSelectFragment.StickerSelectionListener listener) {
      super(fm);

      this.fragments = new Fragment[] {
          StickerSelectFragment.newInstance("stickers/emoticons"),
          StickerSelectFragment.newInstance("stickers/clothes"),
          StickerSelectFragment.newInstance("stickers/animals"),
          StickerSelectFragment.newInstance("stickers/food"),
          StickerSelectFragment.newInstance("stickers/weather"),
          };

      for (Fragment fragment : fragments) {
        ((StickerSelectFragment)fragment).setListener(listener);
      }
    }

    @Override
    public Fragment getItem(int position) {
      return fragments[position];
    }

    @Override
    public int getCount() {
      return fragments.length;
    }
  }
}
