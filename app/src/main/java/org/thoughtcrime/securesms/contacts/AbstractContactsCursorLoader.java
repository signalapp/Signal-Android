package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.CursorLoader;

import java.util.List;

public abstract class AbstractContactsCursorLoader extends CursorLoader {

  private final String filter;

  protected AbstractContactsCursorLoader(@NonNull Context context, @Nullable String filter) {
    super(context);

    this.filter = sanitizeFilter(filter);
  }

  @Override
  public final Cursor loadInBackground() {
    List<Cursor> cursorList = TextUtils.isEmpty(filter) ? getUnfilteredResults()
                                                        : getFilteredResults();
    if (cursorList.size() > 0) {
      return new MergeCursor(cursorList.toArray(new Cursor[0]));
    }
    return null;
  }

  protected final String getFilter() {
    return filter;
  }

  protected abstract List<Cursor> getUnfilteredResults();

  protected abstract List<Cursor> getFilteredResults();

  private static @NonNull String sanitizeFilter(@Nullable String filter) {
    if (filter == null) {
      return "";
    } else if (filter.startsWith("@")) {
      return filter.substring(1);
    } else {
      return filter;
    }
  }

  public interface Factory {
    @NonNull AbstractContactsCursorLoader create();
  }
}
