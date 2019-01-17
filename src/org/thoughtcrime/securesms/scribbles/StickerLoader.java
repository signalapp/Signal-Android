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


import android.content.Context;
import android.support.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.util.AsyncLoader;

import java.io.IOException;

class StickerLoader extends AsyncLoader<String[]> {

  private static final String TAG = StickerLoader.class.getName();

  private final String assetDirectory;

  StickerLoader(Context context, String assetDirectory) {
    super(context);
    this.assetDirectory = assetDirectory;
  }

  @Override
  public @NonNull
  String[] loadInBackground() {
    try {
      String[] files = getContext().getAssets().list(assetDirectory);

      for (int i=0;i<files.length;i++) {
        files[i] = assetDirectory + "/" + files[i];
      }

      return files;
    } catch (IOException e) {
      Log.w(TAG, e);
    }

    return new String[0];
  }
}
