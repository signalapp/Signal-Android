/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.SharedPreferences;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflection utils to call SharedPreferences$Editor.apply when possible,
 * falling back to commit when apply isn't available.
 *
 * https://code.google.com/p/zippy-android/source/browse/trunk/examples/SharedPreferencesCompat.java
 */
public class SharedPreferencesCompat {
  private static final Method sApplyMethod = findApplyMethod();

  private static Method findApplyMethod() {
    try {
      Class cls = SharedPreferences.Editor.class;
      return cls.getMethod("apply");
    } catch (NoSuchMethodException unused) {
      // fall through
    }
    return null;
  }

  public static void apply(SharedPreferences.Editor editor) {
    if (sApplyMethod != null) {
      try {
        sApplyMethod.invoke(editor);
        return;
      } catch (InvocationTargetException unused) {
        // fall through
      } catch (IllegalAccessException unused) {
        // fall through
      }
    }
    editor.commit();
  }
}