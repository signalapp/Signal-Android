package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiFilter;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

/**
 * Custom styled search view that we can insert into ActionBar menus
 */
public class SearchView extends androidx.appcompat.widget.SearchView {
  public SearchView(@NonNull Context context) {
    this(context, null);
  }

  public SearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.search_view_style);
  }

  public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initEmojiFilter();
  }

  private void initEmojiFilter() {
    if (!isInEditMode() && !SignalStore.settings().isPreferSystemEmoji()) {
      TextView searchText = findViewById(androidx.appcompat.R.id.search_src_text);
      if (searchText != null) {
        searchText.setFilters(appendEmojiFilter(searchText));
      }
    }
  }

  private InputFilter[] appendEmojiFilter(@NonNull TextView view) {
    InputFilter[] originalFilters = view.getFilters();
    InputFilter[] result;

    if (originalFilters != null) {
      result = new InputFilter[originalFilters.length + 1];
      System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
    } else {
      result = new InputFilter[1];
    }

    result[0] = new EmojiFilter(view, false);

    return result;
  }
}
