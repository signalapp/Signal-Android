package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ListenableHorizontalScrollView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SubmitDebugLogAdapter extends RecyclerView.Adapter<SubmitDebugLogAdapter.LineViewHolder> {

  private static final int LINE_LENGTH = 500;

  private static final int TYPE_LOG         = 1;
  private static final int TYPE_PLACEHOLDER = 2;

  private final ScrollManager    scrollManager;
  private final Listener         listener;
  private final PagingController pagingController;
  private final List<LogLine>    lines;

  private boolean editing;

  public SubmitDebugLogAdapter(@NonNull Listener listener, @NonNull PagingController pagingController) {
    this.listener         = listener;
    this.pagingController = pagingController;
    this.scrollManager    = new ScrollManager();
    this.lines            = new ArrayList<>();

    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    LogLine item = getItem(position);
    return item != null ? getItem(position).getId() : -1;
  }

  @Override
  public int getItemViewType(int position) {
    return getItem(position) == null ? TYPE_PLACEHOLDER : TYPE_LOG;
  }

  protected LogLine getItem(int position) {
    pagingController.onDataNeededAroundIndex(position);
    return lines.get(position);
  }

  public void submitList(@NonNull List<LogLine> list) {
    this.lines.clear();
    this.lines.addAll(list);
    notifyDataSetChanged();
  }

  @Override
  public int getItemCount() {
    return lines.size();
  }

  @Override
  public @NonNull LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new LineViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.submit_debug_log_line_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
    LogLine item = getItem(position);

    if (item == null) {
      item = SimpleLogLine.EMPTY;
    }

    holder.bind(item, LINE_LENGTH, editing, scrollManager, listener);
  }

  @Override
  public void onViewRecycled(@NonNull LineViewHolder holder) {
    holder.unbind(scrollManager);
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

      if (line.getText().length() > longestLine) {
        text.setText(line.getText().substring(0, longestLine));
      } else if (line.getText().length() < longestLine) {
        text.setText(padRight(line.getText(), longestLine));
      } else {
        text.setText(line.getText());
      }

      switch (line.getStyle()) {
        case NONE:    text.setTextColor(ContextCompat.getColor(context, R.color.debuglog_color_none));    break;
        case VERBOSE: text.setTextColor(ContextCompat.getColor(context, R.color.debuglog_color_verbose)); break;
        case DEBUG:   text.setTextColor(ContextCompat.getColor(context, R.color.debuglog_color_debug));   break;
        case INFO:    text.setTextColor(ContextCompat.getColor(context, R.color.debuglog_color_info));    break;
        case WARNING: text.setTextColor(ContextCompat.getColor(context, R.color.debuglog_color_warn));    break;
        case ERROR:   text.setTextColor(ContextCompat.getColor(context, R.color.debuglog_color_error));   break;
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
