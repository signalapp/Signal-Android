package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ListenableHorizontalScrollView;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SubmitDebugLogAdapter extends RecyclerView.Adapter<SubmitDebugLogAdapter.LineViewHolder> {

  private final List<LogLine> lines;
  private final ScrollManager scrollManager;
  private final Listener      listener;

  private boolean editing;
  private int     longestLine;

  public SubmitDebugLogAdapter(@NonNull Listener listener) {
    this.listener      = listener;
    this.lines         = new ArrayList<>();
    this.scrollManager = new ScrollManager();

    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    return lines.get(position).getId();
  }

  @Override
  public @NonNull LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new LineViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.submit_debug_log_line_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
    holder.bind(lines.get(position), longestLine, editing, scrollManager, listener);
  }

  @Override
  public void onViewRecycled(@NonNull LineViewHolder holder) {
    holder.unbind(scrollManager);
  }

  @Override
  public int getItemCount() {
    return lines.size();
  }

  public void setLines(@NonNull List<LogLine> lines) {
    this.lines.clear();
    this.lines.addAll(lines);

    this.longestLine = Stream.of(lines).reduce(0, (currentMax, line) -> Math.max(currentMax, line.getText().length()));

    notifyDataSetChanged();
  }

  public void setEditing(boolean editing) {
    this.editing = editing;
    notifyDataSetChanged();
  }

  private static class ScrollManager {
    private final List<ScrollObserver> listeners = new CopyOnWriteArrayList<>();

    private int currentPosition;

    void subscribe(@NonNull ScrollObserver observer) {
      listeners.add(observer);
      observer.onScrollChanged(currentPosition);
    }

    void unsubscribe(@NonNull ScrollObserver observer) {
      listeners.remove(observer);
    }

    void notify(int position) {
      currentPosition = position;

      for (ScrollObserver listener : listeners) {
        listener.onScrollChanged(position);
      }
    }
  }

  private interface ScrollObserver {
    void onScrollChanged(int position);
  }

  interface Listener {
    void onLogDeleted(@NonNull LogLine logLine);
  }

  static class LineViewHolder extends RecyclerView.ViewHolder implements ScrollObserver {

    private final TextView                       text;
    private final ListenableHorizontalScrollView scrollView;

    LineViewHolder(@NonNull View itemView) {
      super(itemView);
      this.text       = itemView.findViewById(R.id.log_item_text);
      this.scrollView = itemView.findViewById(R.id.log_item_scroll);
    }

    void bind(@NonNull LogLine line, int longestLine, boolean editing, @NonNull ScrollManager scrollManager, @NonNull Listener listener) {
      Context context = itemView.getContext();

      if (line.getText().length() < longestLine) {
        text.setText(padRight(line.getText(), longestLine));
      } else {
        text.setText(line.getText());
      }

      switch (line.getStyle()) {
        case NONE:    text.setTextColor(ThemeUtil.getThemedColor(context, R.attr.debuglog_color_none));    break;
        case VERBOSE: text.setTextColor(ThemeUtil.getThemedColor(context, R.attr.debuglog_color_verbose)); break;
        case DEBUG:   text.setTextColor(ThemeUtil.getThemedColor(context, R.attr.debuglog_color_debug));   break;
        case INFO:    text.setTextColor(ThemeUtil.getThemedColor(context, R.attr.debuglog_color_info));    break;
        case WARNING: text.setTextColor(ThemeUtil.getThemedColor(context, R.attr.debuglog_color_warn));    break;
        case ERROR:   text.setTextColor(ThemeUtil.getThemedColor(context, R.attr.debuglog_color_error));   break;
      }

      scrollView.setOnScrollListener((newLeft, oldLeft) -> {
        if (oldLeft - newLeft != 0) {
          scrollManager.notify(newLeft);
        }
      });

      scrollManager.subscribe(this);

      if (editing) {
        text.setOnClickListener(v -> listener.onLogDeleted(line));
      } else {
        text.setOnClickListener(null);
      }
    }

    void unbind(@NonNull ScrollManager scrollManager) {
      text.setOnClickListener(null);
      scrollManager.unsubscribe(this);
    }

    @Override
    public void onScrollChanged(int position) {
      scrollView.scrollTo(position, 0);
    }

    private static String padRight(String s, int n) {
      return String.format("%-" + n + "s", s);
    }
  }
}
