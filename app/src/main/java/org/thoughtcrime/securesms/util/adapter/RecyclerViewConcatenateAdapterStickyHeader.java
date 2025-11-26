package org.thoughtcrime.securesms.util.adapter;

import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;

import java.util.Optional;

import kotlin.Pair;


public final class RecyclerViewConcatenateAdapterStickyHeader extends    RecyclerViewConcatenateAdapter
                                                              implements StickyHeaderDecoration.StickyHeaderAdapter,
                                                                         RecyclerViewFastScroller.FastScrollAdapter
{

  @Override
  public long getHeaderId(int position) {
    return getForPosition(position).map(p -> p.getFirst().getHeaderId(p.getSecond())).orElse(-1L);
  }

  @Override
  public RecyclerView.ViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position, int type) {
    return getForPosition(position).map(p -> p.getFirst().onCreateHeaderViewHolder(parent, p.getSecond(), type)).orElse(null);
  }

  @Override
  public void onBindHeaderViewHolder(RecyclerView.ViewHolder viewHolder, int position, int type) {
    Optional<Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer>> forPosition = getForPosition(position);

    if (forPosition.isPresent()) {
      Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer> stickyHeaderAdapterIntegerPair = forPosition.get();
      //noinspection unchecked
      stickyHeaderAdapterIntegerPair.getFirst().onBindHeaderViewHolder(viewHolder, stickyHeaderAdapterIntegerPair.getSecond(), type);
    }
  }

  @Override
  public CharSequence getBubbleText(int position) {
    Optional<Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer>> forPosition = getForPosition(position);

    return forPosition.map(a -> {
      if (a.getFirst() instanceof RecyclerViewFastScroller.FastScrollAdapter) {
        return ((RecyclerViewFastScroller.FastScrollAdapter) a.getFirst()).getBubbleText(a.getSecond());
      } else {
        return "";
      }
    }).orElse("");
  }

  private Optional<Pair<StickyHeaderDecoration.StickyHeaderAdapter, Integer>> getForPosition(int position) {
    ChildAdapterPositionPair                                localAdapterPosition = getLocalPosition(position);
    RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter              = localAdapterPosition.getAdapter();

    if (adapter instanceof StickyHeaderDecoration.StickyHeaderAdapter) {
      StickyHeaderDecoration.StickyHeaderAdapter sticky = (StickyHeaderDecoration.StickyHeaderAdapter) adapter;
      return Optional.of(new Pair<>(sticky, localAdapterPosition.localPosition));
    }
    return Optional.empty();
  }
}
