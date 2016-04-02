package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.DynamicFlatActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;


public class ImportExportActivity extends PassphraseRequiredActionBarActivity {

  private MasterSecret    masterSecret;
  private TabPagerAdapter tabPagerAdapter;
  private ViewPager       viewPager;
  private TabLayout       tabLayout;

  private DynamicTheme dynamicTheme = new DynamicFlatActionBarTheme();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    setContentView(R.layout.import_export_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    initializeResources();
    initializeViewPager();
    initializeTabs();
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
      case android.R.id.home:  finish();  return true;
    }

    return false;
  }

  private void initializeResources() {
    this.viewPager       = (ViewPager) findViewById(R.id.import_export_pager);
    this.tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());
    this.tabLayout       = (TabLayout) findViewById(R.id.tab_layout);
  }

  private void initializeViewPager() {
    viewPager.setAdapter(tabPagerAdapter);
    viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
  }

  private void initializeTabs() {
    tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition());
      }

      @Override
      public void onTabUnselected(TabLayout.Tab tab) {}

      @Override
      public void onTabReselected(TabLayout.Tab tab) {}
    });

    tabLayout.addTab(tabLayout.newTab().setText(R.string.ImportExportActivity_import));
    tabLayout.addTab(tabLayout.newTab().setText(R.string.ImportExportActivity_export));
  }

  private class TabPagerAdapter extends FragmentStatePagerAdapter {
    private final ImportFragment importFragment;
    private final ExportFragment exportFragment;

    public TabPagerAdapter(FragmentManager fragmentManager) {
      super(fragmentManager);

      this.importFragment = new ImportFragment();
      this.exportFragment = new ExportFragment();
      this.importFragment.setMasterSecret(masterSecret);
      this.exportFragment.setMasterSecret(masterSecret);
    }

    @Override
    public Fragment getItem(int i) {
      if (i == 0) return importFragment;
      else        return exportFragment;
    }

    @Override
    public int getCount() {
      return 2;
    }

    @Override
    public CharSequence getPageTitle(int i) {
      if (i == 0) return getString(R.string.ImportExportActivity_import);
      else        return getString(R.string.ImportExportActivity_export);
    }
  }

}
