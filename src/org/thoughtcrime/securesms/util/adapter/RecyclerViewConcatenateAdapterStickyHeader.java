package org.thoughtcrime.securesms.util.adapter;

import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

public final class RecyclerViewConcatenateAdapterStickyHeader extends    RecyclerViewConcatenateAdapter
                                                              implements StickyHeaderDecoration.StickyHeaderAdapter,
                                                                         RecyclerViewFastScroller.FastScrollAdapter
{

  @Override
  public long getHeaderId(int position) {
    return getForPosition(position).transform(p -> p.first().getHeaderId(p.second())).or(-1L);
  }

  @Override
  public RecyclerView.ViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position) {
    return getForPosition(position).transform(p -> p.first().onCreateHeaderViewHolder(parent, p.second())).orNull();
  }

  @Override
  public void onBindHeaderViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
    Optional<Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer>> forPosition = getForPosition(position);

    if (forPosition.isPresent()) {
      Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer> stickyHeaderAdapterIntegerPair = forPosition.get();
      //noinspection unchecked
      stickyHeaderAdapterIntegerPair.first().onBindHeaderViewHolder(viewHolder, stickyHeaderAdapterIntegerPair.second());
    }
  }

  @Override
  public CharSequence getBubbleText(int position) {
    Optional<Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer>> forPosition = getForPosition(position);

    return forPosition.transform(a -> {
      if (a.first() instanceof RecyclerViewFastScroller.FastScrollAdapter) {
        return ((RecyclerViewFastScroller.FastScrollAdapter) a.first()).getBubbleText(a.second());
      } else {
        return "";
      }
    }).or("");
  }

  private Optional<Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer>> getForPosition(int position) {
    ChildAdapterPositionPair                                localAdapterPosition = getLocalPosition(position);
    RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter              = localAdapterPosition.getAdapter();

    if (adapter instanceof StickyHeaderDecoration.StickyHeaderAdapter) {
      StickyHeaderDecoration.StickyHeaderAdapter sticky = (StickyHeaderDecoration.StickyHeaderAdapter) adapter;
      return Optional.of(new Pair<>(sticky, localAdapterPosition.localPosition));
    }
    return Optional.absent();
  }
}
