package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel.OnModelChangedListener;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.InvalidatingPageLoadedListener;

public class EmojiPageFragment extends Fragment {
  private static final String TAG = EmojiPageFragment.class.getSimpleName();

  private EmojiPageModel         model;
  private EmojiSelectionListener listener;

  public static EmojiPageFragment newInstance(@NonNull EmojiPageModel model,
                                              @Nullable EmojiSelectionListener listener)
  {
    EmojiPageFragment fragment = new EmojiPageFragment();
    fragment.setModel(model);
    fragment.setEmojiSelectedListener(listener);
    return fragment;
  }

  @Nullable @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                               Bundle savedInstanceState)
  {
    final View     view = inflater.inflate(R.layout.emoji_grid_layout, container, false);
    final GridView grid = (GridView) view.findViewById(R.id.emoji);
    grid.setColumnWidth(getResources().getDimensionPixelSize(R.dimen.emoji_drawer_size) + 2 * getResources().getDimensionPixelSize(R.dimen.emoji_drawer_item_padding));
    grid.setOnItemClickListener(new OnItemClickListener() {
      @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (listener != null) listener.onEmojiSelected((Integer)view.getTag());
      }
    });
    grid.setAdapter(new EmojiGridAdapter(getActivity(), model));
    return view;
  }

  public void setModel(EmojiPageModel model) {
    this.model = model;
  }

  public void setEmojiSelectedListener(EmojiSelectionListener listener) {
    this.listener = listener;
  }

  private static class EmojiGridAdapter extends BaseAdapter {

    protected final Context        context;
    private   final int            emojiSize;
    private   final EmojiPageModel model;

    public EmojiGridAdapter(Context context, EmojiPageModel model) {
      this.context   = context;
      this.emojiSize = (int) context.getResources().getDimension(R.dimen.emoji_drawer_size);
      this.model     = model;
    }

    @Override public int getCount() {
      return model.getCodePoints().length;
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
      final ImageView view;
      final int pad = context.getResources().getDimensionPixelSize(R.dimen.emoji_drawer_item_padding);
      if (convertView != null && convertView instanceof ImageView) {
        view = (ImageView)convertView;
      } else {
        ImageView imageView = new ImageView(context);
        imageView.setPadding(pad, pad, pad, pad);
        imageView.setLayoutParams(new AbsListView.LayoutParams(emojiSize + 2 * pad, emojiSize + 2 * pad));
        view = imageView;
      }

      model.setOnModelChangedListener(new OnModelChangedListener() {
        @Override public void onModelChanged() {
          notifyDataSetChanged();
        }
      });

      final Integer       unicodeTag = model.getCodePoints()[position];
      final EmojiProvider provider   = EmojiProvider.getInstance(context);
      final Drawable      drawable   = provider.getEmojiDrawable(unicodeTag,
                                                                 EmojiProvider.EMOJI_HUGE,
                                                                 new InvalidatingPageLoadedListener(view));

      view.setImageDrawable(drawable);
      view.setPadding(pad, pad, pad, pad);
      view.setTag(unicodeTag);
      return view;
    }
  }

  public interface EmojiSelectionListener {
    void onEmojiSelected(int emojiCode);
  }
}
