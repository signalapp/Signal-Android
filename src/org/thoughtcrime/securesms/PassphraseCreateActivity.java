/**
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.nineoldandroids.animation.ArgbEvaluator;
import com.nineoldandroids.animation.ObjectAnimator;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.VersionTracker;

import me.relex.circleindicator.CircleIndicator;

/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseCreateActivity extends PassphraseActivity {

  private RelativeLayout layout;
  private Button         continueButton;
  private ViewPager      pager;
  private PagerAdapter   adapter;

  private static final int[] colors = new int[] {
      0xFF2090EA, 0xFF673AB7, 0xFF009688
  };

  public PassphraseCreateActivity() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.create_passphrase_activity);
    initializeResources();
    TextSecurePreferences.setOnboardingCompleted(this, false);

    getSupportActionBar().hide();
  }

  private void initializeResources() {
    layout         = (RelativeLayout)findViewById(R.id.prompt_layout);
    continueButton = (Button)findViewById(R.id.continue_button);
    continueButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setOnboardingCompleted(PassphraseCreateActivity.this, true);
        setResult(Activity.RESULT_OK);
        finish();
      }
    });

    CircleIndicator indicator = (CircleIndicator) findViewById(R.id.indicator);
    pager = (ViewPager) findViewById(R.id.viewpager);
    adapter = new IntroPagerAdapter(getSupportFragmentManager());
    pager.setAdapter(adapter);
    final ArgbEvaluator evaluator = new ArgbEvaluator();
    indicator.setViewPager(pager);
    indicator.setOnPageChangeListener(new OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        final int color = (Integer) evaluator.evaluate(positionOffset, colors[position], colors[(position + 1) % colors.length]);
        layout.setBackgroundColor(color);
        continueButton.setTextColor(color);
      }

      @Override
      public void onPageSelected(int position) {
      }

      @Override
      public void onPageScrollStateChanged(int state) {
      }
    });

    if (!MasterSecretUtil.isPassphraseInitialized(this)) {
      TextSecurePreferences.setPasswordDisabled(this, true);
      new SecretGenerator().execute(MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
    } else {
      continueButton.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
          continueButton.getViewTreeObserver().removeOnPreDrawListener(this);
          onMasterSecretSet();
          return true;
        }
      });
    }
  }

  private class SecretGenerator extends AsyncTask<String, Void, Void> {
    private MasterSecret masterSecret;

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected Void doInBackground(String... params) {
      String passphrase = params[0];
      masterSecret      = MasterSecretUtil.generateMasterSecret(PassphraseCreateActivity.this,
                                                                passphrase);

      MemoryCleaner.clean(passphrase);

      MasterSecretUtil.generateAsymmetricMasterSecret(PassphraseCreateActivity.this, masterSecret);
      IdentityKeyUtil.generateIdentityKeys(PassphraseCreateActivity.this, masterSecret);
      VersionTracker.updateLastSeenVersion(PassphraseCreateActivity.this);

      return null;
    }

    @Override
    protected void onPostExecute(Void param) {
      setMasterSecret(masterSecret);
    }
  }

  @Override
  protected void onMasterSecretSet() {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ViewAnimationUtils.createCircularReveal(continueButton,
                                              continueButton.getWidth() / 2,
                                              continueButton.getHeight() / 2, 0, continueButton.getWidth())
                        .setDuration(1000)
                        .start();
    } else {
      ObjectAnimator.ofFloat(continueButton, "alpha", 0.0f, 1.0f).setDuration(500).start();
    }
    continueButton.setVisibility(View.VISIBLE);
  }
}
