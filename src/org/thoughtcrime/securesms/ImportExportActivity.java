package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.DynamicTheme;


public class ImportExportActivity extends PassphraseRequiredActionBarActivity {

  private TabPagerAdapter tabPagerAdapter;
  private ViewPager viewPager;
  private MasterSecret masterSecret;

  private DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    dynamicTheme.onCreate(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.import_export_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    initializeResources();
    initializeViewPager();
    initializeTabs();
  }

  @Override
  public void onResume() {
      dynamicTheme.onResume(this);
      initializeResources();
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
    this.masterSecret    = getIntent().getParcelableExtra("master_secret");
    this.viewPager       = (ViewPager) findViewById(R.id.import_export_pager);
    this.tabPagerAdapter = new TabPagerAdapter(getSupportFragmentManager());

    viewPager.setAdapter(tabPagerAdapter);
  }

  private void initializeViewPager() {
    viewPager.setAdapter(tabPagerAdapter);
    viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageSelected(int position) {
        getSupportActionBar().setSelectedNavigationItem(position);
      }
    });
  }

  private void initializeTabs() {
    final ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

    ActionBar.TabListener tabListener = new ActionBar.TabListener() {
      public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        viewPager.setCurrentItem(tab.getPosition());
      }

      public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {}
      public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {}
    };

    actionBar.addTab(actionBar.newTab().setText(R.string.ImportExportActivity_import).setTabListener(tabListener));
    actionBar.addTab(actionBar.newTab().setText(R.string.ImportExportActivity_export).setTabListener(tabListener));
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