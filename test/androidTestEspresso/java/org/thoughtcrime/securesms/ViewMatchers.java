/**
 * Copyright (C) 2015 Open Whisper Systems
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

import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ViewMatchers {

  public static Matcher<View> withRecyclerItem(final Matcher<Object> itemMatcher) {
    return new TypeSafeMatcher<View>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("with recycler item: ");
        itemMatcher.describeTo(description);
      }

      @Override
      public boolean matchesSafely(View view) {
        if (!(view instanceof RecyclerView)) {
          return false;
        }

        RecyclerView recyclerView = ((RecyclerView) view);
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
          if (itemMatcher.matches(recyclerView.getChildAt(i))) {
            return true;
          }
        }
        return false;
      }
    };
  }

  public static Matcher<View> withRecyclerItemCount(final long itemCount) {
    return new TypeSafeMatcher<View>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("with recycler item count: " + itemCount);
      }

      @Override
      public boolean matchesSafely(View view) {
        if (!(view instanceof RecyclerView)) {
          return false;
        }

        RecyclerView recyclerView = ((RecyclerView) view);
        return recyclerView.getChildCount() == itemCount;
      }
    };
  }

}
