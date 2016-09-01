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

package org.thoughtcrime.securesms.util;

import android.content.ActivityNotFoundException;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;

public class SafeLinkMovementMethod extends LinkMovementMethod {
  private static SafeLinkMovementMethod sInstance;
  private static final String TAG = SafeLinkMovementMethod.class.getSimpleName();

  public static MovementMethod getInstance() {
    if (sInstance == null)
      sInstance = new SafeLinkMovementMethod();
      return sInstance;
  }

  @Override
  public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
    try {
      return super.onTouchEvent(widget,buffer,event);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(widget.getContext(), R.string.SafeLinkMovementMethod_no_handler_app, Toast.LENGTH_LONG).show();
      return false;
    }
  }
}
