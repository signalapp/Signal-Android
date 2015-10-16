package org.thoughtcrime.securesms;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.SparseArrayCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thoughtcrime.securesms.util.DateUtils;

import java.util.Locale;

public class DateSeparatorItemDecoration extends RecyclerView.ItemDecoration {

  private final ConversationAdapter adapter;
  private final LayoutInflater      inflater;
  private final Locale              locale;

  private final SparseArrayCompat<SeparatorViewHolder> separators         = new SparseArrayCompat<>();
  private final LongSparseArray<SeparatorViewHolder>   recycledSeparators = new LongSparseArray<>();

  private Boolean isReverseLayout = null;

  public DateSeparatorItemDecoration(ConversationAdapter adapter, RecyclerView parent, Locale locale) {
    this.adapter = adapter;
    inflater = LayoutInflater.from(adapter.getContext());
    this.locale = locale;

    parent.setRecyclerListener(new RecyclerView.RecyclerListener() {
      @Override
      public void onViewRecycled(RecyclerView.ViewHolder holder) {
        int                 hashCode   = holder.itemView.hashCode();
        SeparatorViewHolder viewHolder = separators.get(hashCode);
        separators.delete(hashCode);
        if (viewHolder != null) recycledSeparators.put(viewHolder.datestamp, viewHolder);
      }
    });
  }

  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
    super.getItemOffsets(outRect, view, parent, state);

    int position = parent.getChildPosition(view);
    if (needsSeparator(parent, position)) {
      View separator = getSeparator(parent, view, position);
      Rect separatorMargins = getMargins(separator);
      outRect.top = separator.getHeight() + separatorMargins.top + separatorMargins.bottom;
    }
  }

  @Override
  public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
    if (parent.getChildCount() <= 0 || adapter.getItemCount() <= 0) {
      return;
    }

    for (int i = 0; i < parent.getChildCount(); i++) {
      View itemView = parent.getChildAt(i);
      int position = parent.getChildPosition(itemView);
      if (position == RecyclerView.NO_POSITION) {
        continue;
      }

      if (needsSeparator(parent, position)) {
        View separator = getSeparator(parent, itemView, position);
        Rect margins = getMargins(separator);
        int translationX = itemView.getLeft() + margins.left;
        int translationY = itemView.getTop() - separator.getHeight() - margins.bottom;
        Rect offset = new Rect(translationX, translationY, translationX + separator.getWidth(),
                              translationY + separator.getHeight());

        c.save();

        if (parent.getLayoutManager().getClipToPadding()) {
          Rect clipRect = new Rect(parent.getPaddingLeft(),
                                  parent.getPaddingTop(),
                                  parent.getWidth() - parent.getPaddingRight() - margins.right,
                                  parent.getHeight() - parent.getPaddingBottom());
          c.clipRect(clipRect);
        }

        c.translate(offset.left, offset.top);

        separator.draw(c);
        c.restore();
      }
    }
  }

  private boolean needsSeparator(RecyclerView parent, int position) {
    int prevPos           = position + (getReverseLayout(parent) ? 1 : -1);
    int firstItemPosition = getReverseLayout(parent) ? adapter.getItemCount() - 1 : 0;
    return position == firstItemPosition || adapter.getDatestamp(position) != adapter.getDatestamp(prevPos);
  }

  private View getSeparator(RecyclerView parent, View childView, int position) {
    SeparatorViewHolder separator = separators.get(childView.hashCode());

    if (separator == null) {
      long datestamp = adapter.getDatestamp(position);
      separator = recycledSeparators.get(datestamp);
      if (separator == null) {
        if (recycledSeparators.size() > 0) {
          separator = recycledSeparators.valueAt(0);
          recycledSeparators.removeAt(0);
        } else {
          separator = new SeparatorViewHolder(inflater.inflate(R.layout.conversation_item_date_separator, parent, false));
        }

        separator.setDatestamp(datestamp);

        View separatorView = separator.itemView;
        if (separatorView.getLayoutParams() == null) {
          separatorView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);
        int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
                                                      parent.getPaddingLeft() + parent.getPaddingRight(),
                                                      separatorView.getLayoutParams().width);
        int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                                                       parent.getPaddingTop() + parent.getPaddingBottom(),
                                                       separatorView.getLayoutParams().height);
        separatorView.measure(childWidth, childHeight);
        separatorView.layout(0, 0, separatorView.getMeasuredWidth(), separatorView.getMeasuredHeight());
      } else {
        recycledSeparators.remove(datestamp);
      }

      separators.put(childView.hashCode(), separator);
    }

    return separator.itemView;
  }

  private boolean getReverseLayout(RecyclerView recyclerView) {
    if (isReverseLayout == null) {
      RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
      if (lm instanceof LinearLayoutManager) {
        isReverseLayout = ((LinearLayoutManager) lm).getReverseLayout();
      } else {
        throw new IllegalStateException("The LayoutManager " + lm + " is not " + LinearLayoutManager.class.getSimpleName());
      }
    }
    return isReverseLayout;
  }

  private Rect getMargins(View view) {
    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();

    if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
      return new Rect(marginLayoutParams.leftMargin,
                     marginLayoutParams.topMargin,
                     marginLayoutParams.rightMargin,
                     marginLayoutParams.bottomMargin);
    } else {
      return new Rect();
    }
  }


  private class SeparatorViewHolder extends RecyclerView.ViewHolder {
    final TextView text;
    long datestamp;

    public SeparatorViewHolder(View itemView) {
      super(itemView);
      text = (TextView) itemView.findViewById(R.id.conversation_item_date_separator);
    }

    public void setDatestamp(long datestamp) {
      this.datestamp = datestamp;
      text.setText(DateUtils.getDateSeparatorString(locale, datestamp));
    }
  }

}
