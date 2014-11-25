package org.thoughtcrime.securesms;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;

import org.thoughtcrime.securesms.util.DynamicTheme;

public class CountrySelectionActivity extends FragmentActivity
    implements CountrySelectionFragment.CountrySelectedListener

{

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.setContentView(R.layout.country_selection);
  }

  @Override
  public void countrySelected(String countryName, int countryCode) {
    Intent result = getIntent();
    result.putExtra("country_name", countryName);
    result.putExtra("country_code", countryCode);

    this.setResult(RESULT_OK, result);
    this.finish();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && "LGE".equalsIgnoreCase(Build.BRAND)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && "LGE".equalsIgnoreCase(Build.BRAND)) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }
}
