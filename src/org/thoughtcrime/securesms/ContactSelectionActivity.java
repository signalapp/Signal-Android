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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.ActionBar.TabListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.ArrayList;
import java.util.List;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;

/**
 * Activity container for selecting a list of contacts.  Provides a tab frame for
 * contact, group, and "recent contact" activity tabs.  Used by ComposeMessageActivity
 * when selecting a list of contacts to address a message to.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionActivity extends PassphraseRequiredSherlockFragmentActivity {

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  private ViewPager viewPager;
  private ContactSelectionListFragment contactsFragment;
  private ContactSelectionGroupsFragment groupsFragment;
  private ContactSelectionRecentFragment recentFragment;

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    super.onCreate(icicle);

    final ActionBar actionBar = this.getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    actionBar.setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.contact_selection_activity);

    setupFragments();
    setupViewPager();
    setupTabs();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.contact_selection, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_selection_finished:
    case android.R.id.home:
      handleSelectionFinished(); return true;
    }

    return false;
  }

  private void handleSelectionFinished() {
    List<ContactData> contacts = contactsFragment.getSelectedContacts();
    contacts.addAll(recentFragment.getSelectedContacts());
    contacts.addAll(groupsFragment.getSelectedContacts(this));

    Intent resultIntent = getIntent();
    resultIntent.putParcelableArrayListExtra("contacts", new ArrayList<ContactData>(contacts));

    setResult(RESULT_OK, resultIntent);

    finish();
  }

  private void setupViewPager() {
    viewPager = (ViewPager) findViewById(R.id.pager);
    viewPager.setAdapter(new SelectionPagerAdapter());
    viewPager.setOnPageChangeListener(new TabSwitchingPageListener());
  }

  private void setupTabs() {
    int[] icons = new int[] { R.drawable.ic_tab_contacts, R.drawable.ic_tab_groups, R.drawable.ic_tab_recent };

    for (int i = 0; i < icons.length; i++) {
      ActionBar.Tab tab = getSupportActionBar().newTab();
      tab.setIcon(icons[i]);
      tab.setTabListener(new ViewPagerTabListener(i));
      getSupportActionBar().addTab(tab);
    }
  }

  private void setupFragments() {
    contactsFragment = new ContactSelectionListFragment();
    groupsFragment = new ContactSelectionGroupsFragment();
    recentFragment = new ContactSelectionRecentFragment();
  }

  private class SelectionPagerAdapter extends FragmentPagerAdapter {

    public SelectionPagerAdapter() {
      super(getSupportFragmentManager());
    }

    @Override
    public Fragment getItem(int i) {
      switch (i) {
        case 0:
          return contactsFragment;
        case 1:
          return groupsFragment;
        case 2:
        default:
          return recentFragment;
      }
    }

    @Override
    public int getCount() {
      return 3;
    }

  }

  private class ViewPagerTabListener implements TabListener {

    private int tabIndex;

    public ViewPagerTabListener(int index) {
      tabIndex = index;
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction fragmentTransaction) {
      viewPager.setCurrentItem(tabIndex);
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction fragmentTransaction) {

    }

  }

  private class TabSwitchingPageListener implements ViewPager.OnPageChangeListener {

    @Override
    public void onPageScrolled(int i, float v, int i2) {

    }

    @Override
    public void onPageSelected(int i) {
      getSupportActionBar().setSelectedNavigationItem(i);
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }

  }

}
