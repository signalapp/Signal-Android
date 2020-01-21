package org.thoughtcrime.securesms.util.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import java.util.List;

/**
 * A {@link RecyclerView.Adapter} subclass that makes it easier to have sectioned content, where
 * you have header rows and content rows.
 *
 * @param <IdType> The type you'll use to generate stable IDs.
 * @param <SectionImpl> The subclass of {@link Section} you're using.
 */
public abstract class SectionedRecyclerViewAdapter<IdType, SectionImpl extends SectionedRecyclerViewAdapter.Section<IdType>> extends RecyclerView.Adapter {

  private static final int TYPE_HEADER  = 1;
  private static final int TYPE_CONTENT = 2;
  private static final int TYPE_EMPTY   = 3;

  private final StableIdGenerator<IdType> stableIdGenerator;

  public SectionedRecyclerViewAdapter() {
    this.stableIdGenerator = new StableIdGenerator<>();
    setHasStableIds(true);
  }

  protected @NonNull abstract List<SectionImpl> getSections();
  protected @NonNull abstract RecyclerView.ViewHolder createHeaderViewHolder(@NonNull ViewGroup parent);
  protected @NonNull abstract RecyclerView.ViewHolder createContentViewHolder(@NonNull ViewGroup parent);
  protected @Nullable abstract RecyclerView.ViewHolder createEmptyViewHolder(@NonNull ViewGroup viewGroup);
  protected abstract void bindViewHolder(@NonNull RecyclerView.ViewHolder holder, @NonNull SectionImpl section, int localPosition);

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
    switch (viewType) {
      case TYPE_HEADER:
        return createHeaderViewHolder(viewGroup);
      case TYPE_CONTENT:
        return createContentViewHolder(viewGroup);
      case TYPE_EMPTY:
        RecyclerView.ViewHolder holder = createEmptyViewHolder(viewGroup);
        if (holder == null) {
          throw new IllegalStateException("Expected an empty view holder, but got none!");
        }
        return holder;
      default:
        throw new AssertionError("Unexpected viewType! " + viewType);
    }
  }

  @Override
  public long getItemId(int globalPosition) {
    for (SectionImpl section: getSections()) {
      if (section.handles(globalPosition)) {
        return section.getItemId(stableIdGenerator, globalPosition);
      }
    }
    throw new NoSectionException();
  }

  @Override
  public int getItemViewType(int globalPosition) {
    for (SectionImpl section : getSections()) {
      if (section.handles(globalPosition)) {
        return section.getViewType(globalPosition);
      }
    }
    throw new NoSectionException();
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int globalPosition) {
    for (SectionImpl section : getSections()) {
      if (section.handles(globalPosition)) {
        bindViewHolder(holder, section, section.getLocalPosition(globalPosition));
        return;
      }
    }
    throw new NoSectionException();
  }

  @Override
  public int getItemCount() {
    return Stream.of(getSections()).reduce(0, (sum, section) -> sum + section.size());
  }

  /**
   * Represents a section of content in the adapter. Has a header and content.
   * @param <E> The type you'll use to generate stable IDs.
   */
  public static abstract class Section<E> {

    private final int offset;

    public Section(int offset) {
      this.offset = offset;
    }

    public abstract boolean hasEmptyState();
    public abstract int getContentSize();
    public abstract long getItemId(@NonNull StableIdGenerator<E> idGenerator, int globalPosition);

    protected final int getLocalPosition(int globalPosition) {
      return globalPosition - offset;
    }

    final int getViewType(int globalPosition) {
      int localPosition = getLocalPosition(globalPosition);

      if (localPosition == 0) {
        return TYPE_HEADER;
      } else if (getContentSize() == 0) {
        return TYPE_EMPTY;
      } else {
        return TYPE_CONTENT;
      }
    }

    final boolean handles(int globalPosition) {
      int localPosition = getLocalPosition(globalPosition);
      return localPosition >= 0 && localPosition < size();
    }

    public boolean isContent(int globalPosition) {
      return handles(globalPosition) && getViewType(globalPosition) == TYPE_CONTENT;
    }

    public final int size() {
      if (getContentSize() == 0 && hasEmptyState()) {
        return 2;
      } else if (getContentSize() == 0) {
        return 0;
      } else {
        return getContentSize() + 1;
      }
    }
  }

  private static class NoSectionException extends IllegalStateException {}
}
