package org.thoughtcrime.securesms;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.melnykov.fab.FloatingActionButton;
import com.nineoldandroids.animation.ArgbEvaluator;

import org.thoughtcrime.securesms.IntroPagerAdapter.IntroPage;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.Collections;
import java.util.List;

import me.relex.circleindicator.CircleIndicator;

public class ExperienceUpgradeActivity extends BaseActionBarActivity {
  private static final String TAG             = ExperienceUpgradeActivity.class.getSimpleName();
  private static final int    NOTIFICATION_ID = 1339;

  private enum ExperienceUpgrade {
    SIGNAL_REBRANDING(155,
                      new IntroPage(0xFF2090EA,
                                    BasicIntroFragment.newInstance(R.drawable.splash_logo,
                                                                   R.string.ExperienceUpgradeActivity_welcome_to_signal_dgaf,
                                                                   R.string.ExperienceUpgradeActivity_textsecure_is_now_called_signal)),
                      R.string.ExperienceUpgradeActivity_welcome_to_signal_excited,
                      R.string.ExperienceUpgradeActivity_textsecure_is_now_signal,
                      R.string.ExperienceUpgradeActivity_textsecure_is_now_signal_long);

    private            int             version;
    private            List<IntroPage> pages;
    private @StringRes int             notificationTitle;
    private @StringRes int             notificationText;
    private @StringRes int             notificationBigText;

    ExperienceUpgrade(int version,
                      @NonNull List<IntroPage> pages,
                      @StringRes int notificationTitle,
                      @StringRes int notificationText,
                      @StringRes int notificationBigText)
    {
      this.version = version;
      this.pages = pages;
      this.notificationTitle = notificationTitle;
      this.notificationText = notificationText;
      this.notificationBigText = notificationBigText;
    }

    ExperienceUpgrade(int version,
                      @NonNull IntroPage page,
                      @StringRes int notificationTitle,
                      @StringRes int notificationText,
                      @StringRes int notificationBigText)
    {
      this(version, Collections.singletonList(page), notificationTitle, notificationText, notificationBigText);
    }

    public int getVersion() {
      return version;
    }

    public List<IntroPage> getPages() {
      return pages;
    }

    public IntroPage getPage(int i) {
      return pages.get(i);
    }

    public int getNotificationTitle() {
      return notificationTitle;
    }

    public int getNotificationText() {
      return notificationText;
    }

    public int getNotificationBigText() {
      return notificationBigText;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStatusBarColor(getResources().getColor(R.color.signal_primary_dark));

    Optional<ExperienceUpgrade> upgrade = getExperienceUpgrade(this);
    if (!upgrade.isPresent()) {
      onContinue();
      return;
    }

    setContentView(R.layout.experience_upgrade_activity);
    final ViewPager            pager     = ViewUtil.findById(this, R.id.pager);
    final CircleIndicator      indicator = ViewUtil.findById(this, R.id.indicator);
    final FloatingActionButton fab       = ViewUtil.findById(this, R.id.fab);

    pager.setAdapter(new IntroPagerAdapter(getSupportFragmentManager(), upgrade.get().getPages()));

    if (upgrade.get().getPages().size() > 1) {
      indicator.setViewPager(pager);
      indicator.setOnPageChangeListener(new OnPageChangeListener(upgrade.get()));
    } else {
      indicator.setVisibility(View.GONE);
    }

    fab.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        onContinue();
      }
    });

    getWindow().setBackgroundDrawable(new ColorDrawable(upgrade.get().getPage(0).backgroundColor));
    ServiceUtil.getNotificationManager(this).cancel(NOTIFICATION_ID);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void setStatusBarColor(int color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(color);
    }
  }

  private void onContinue() {
    TextSecurePreferences.setLastExperienceVersionCode(this, Util.getCurrentApkReleaseVersion(this));
    startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
    finish();
  }

  public static boolean isUpdate(Context context) {
    return getExperienceUpgrade(context).isPresent();
  }

  public static Optional<ExperienceUpgrade> getExperienceUpgrade(Context context) {
    final int currentVersionCode = Util.getCurrentApkReleaseVersion(context);
    final int lastSeenVersion    = TextSecurePreferences.getLastExperienceVersionCode(context);
    Log.w(TAG, "getExperienceUpgrade(" + lastSeenVersion + ")");

    if (lastSeenVersion >= currentVersionCode || lastSeenVersion == 0) {
      TextSecurePreferences.setLastExperienceVersionCode(context, currentVersionCode);
      return Optional.absent();
    }

    Optional<ExperienceUpgrade> eligibleUpgrade = Optional.absent();
    for (ExperienceUpgrade upgrade : ExperienceUpgrade.values()) {
      if (lastSeenVersion < upgrade.getVersion()) eligibleUpgrade = Optional.of(upgrade);
    }

    return eligibleUpgrade;
  }

  private final class OnPageChangeListener implements ViewPager.OnPageChangeListener {
    private final ArgbEvaluator     evaluator = new ArgbEvaluator();
    private final ExperienceUpgrade upgrade;

    public OnPageChangeListener(ExperienceUpgrade upgrade) {
      this.upgrade = upgrade;
    }

    @Override
    public void onPageSelected(int position) {}

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      final int nextPosition = (position + 1) % upgrade.getPages().size();

      final int color = (Integer)evaluator.evaluate(positionOffset,
                                                    upgrade.getPage(position).backgroundColor,
                                                    upgrade.getPage(nextPosition).backgroundColor);
      getWindow().setBackgroundDrawable(new ColorDrawable(color));
    }
  }

  public static class AppUpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if(Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()) &&
         intent.getData().getSchemeSpecificPart().equals(context.getPackageName()))
      {
        Optional<ExperienceUpgrade> experienceUpgrade = getExperienceUpgrade(context);
        if (!experienceUpgrade.isPresent()) return;

        Intent       targetIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        Notification notification = new NotificationCompat.Builder(context)
                                        .setSmallIcon(R.drawable.icon_notification)
                                        .setColor(context.getResources().getColor(R.color.signal_primary))
                                        .setContentTitle(context.getString(experienceUpgrade.get().getNotificationTitle()))
                                        .setContentText(context.getString(experienceUpgrade.get().getNotificationText()))
                                        .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(experienceUpgrade.get().getNotificationBigText())))
                                        .setAutoCancel(true)
                                        .setContentIntent(PendingIntent.getActivity(context, 0,
                                                                                    targetIntent,
                                                                                    PendingIntent.FLAG_UPDATE_CURRENT))
                                        .build();
        ServiceUtil.getNotificationManager(context).notify(NOTIFICATION_ID, notification);
      }
    }
  }
}
