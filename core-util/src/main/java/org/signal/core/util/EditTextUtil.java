package org.signal.core.util;

import android.text.InputFilter;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EditTextUtil {

  private EditTextUtil() {
  }

  public static void addGraphemeClusterLimitFilter(EditText text, int maximumGraphemes) {
    List<InputFilter> filters = new ArrayList<>(Arrays.asList(text.getFilters()));
    filters.add(new GraphemeClusterLimitFilter(maximumGraphemes));
    text.setFilters(filters.toArray(new InputFilter[0]));
  }
}
