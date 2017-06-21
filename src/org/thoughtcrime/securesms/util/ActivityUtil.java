/*
 * Copyright (C) 2017 Fernando Garcia Alvarez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thoughtcrime.securesms.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;

/**
 * Class used to perform actions
 * on Activities in a compatible way
 *
 * @author fercarcedo
 */
public class ActivityUtil {
  public static void recreateActivity(Activity activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      recreateHoneycomb(activity);
    } else {
      recreatePreHoneycomb(activity);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private static void recreateHoneycomb(Activity activity) {
    activity.recreate();
  }

  private static void recreatePreHoneycomb(Activity activity) {
    Intent intent = activity.getIntent();
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    activity.finish();
    activity.overridePendingTransition(0, 0);
    activity.startActivity(intent);
    activity.overridePendingTransition(0, 0);
  }
}
