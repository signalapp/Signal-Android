package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.MenuItem;

import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;


public class ImportExportActivity extends PassphraseRequiredActionBarActivity {

  private DynamicTheme    dynamicTheme    = new DynamicTheme();
  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    initFragment(android.R.id.content, new ImportExportFragment(), dynamicLanguage.getCurrentLocale());
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
}
