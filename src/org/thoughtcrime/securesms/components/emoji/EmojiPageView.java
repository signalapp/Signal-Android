package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;

import org.thoughtcrime.securesms.R;

public class EmojiPageView extends FrameLayout {
  private static final String TAG = EmojiPageView.class.getSimpleName();

  private EmojiPageModel         model;
  private EmojiSelectionListener listener;
  private GridView               grid;

  public EmojiPageView(Context context) {
    this(context, null);
  }

  public EmojiPageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiPageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    final View view = LayoutInflater.from(getContext()).inflate(R.layout.emoji_grid_layout, this, true);
    grid = (GridView) view.findViewById(R.id.emoji);
    grid.setColumnWidth(getResources().getDimensionPixelSize(R.dimen.emoji_drawer_size) + 2 * getResources().getDimensionPixelSize(R.dimen.emoji_drawer_item_padding));
    grid.setOnTouchListener(new OnTouchListener() {
      @Override public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          EmojiView emojiView = (EmojiView)grid.getChildAt(grid.pointToPosition((int)event.getX(), (int)event.getY()));
          if (listener != null && emojiView != null) listener.onEmojiSelected(emojiView.getEmoji());
        }
        return false;
      }
    });
  }

  public void onSelected() {
    if (model.isDynamic() && grid != null && grid.getAdapter() != null) {
      ((EmojiGridAdapter)grid.getAdapter()).notifyDataSetChanged();
    }
  }

  public void setModel(EmojiPageModel model) {
    this.model = model;
    grid.setAdapter(new EmojiGridAdapter(getContext(), model));
  }

  public void setEmojiSelectedListener(EmojiSelectionListener listener) {
    this.listener = listener;
  }

  private static class EmojiGridAdapter extends BaseAdapter {

    protected final Context                context;
    private   final int                    emojiSize;
    private   final EmojiPageModel         model;

    public EmojiGridAdapter(Context context, EmojiPageModel model) {
      this.context   = context;
      this.emojiSize = (int) context.getResources().getDimension(R.dimen.emoji_drawer_size);
      this.model     = model;
    }

    @Override public int getCount() {
      return model.getEmoji().length;
    }

    @Override
    public Object getItem(int position) {
      return null;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
      final EmojiView view;
      final int pad = context.getResources().getDimensionPixelSize(R.dimen.emoji_drawer_item_padding);
      if (convertView != null && convertView instanceof EmojiView) {
        view = (EmojiView)convertView;
      } else {
        final EmojiView emojiView = new EmojiView(context);
        emojiView.setPadding(pad, pad, pad, pad);
        emojiView.setLayoutParams(new AbsListView.LayoutParams(emojiSize + 2 * pad, emojiSize + 2 * pad));
        view = emojiView;
      }

      view.setEmoji(model.getEmoji()[position]);
      return view;
    }
  }

  public interface EmojiSelectionListener {
    void onEmojiSelected(String emoji);
  }
}
