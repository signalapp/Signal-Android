package org.signal.paging;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Describes various properties of how you'd like paging to be handled.
 */
public final class PagingConfig {

  private final int bufferPages;
  private final int startIndex;
  private final int pageSize;

  private PagingConfig(@NonNull Builder builder) {
    this.bufferPages = builder.bufferPages;
    this.startIndex  = builder.startIndex;
    this.pageSize    = builder.pageSize;
  }

  /**
   * @return How many pages of 'buffer' you want ahead of and behind the active position. i.e. if
   *         the {@code pageSize()} is 10 and you specify 2 buffer pages, then there will always be
   *         at least 20 items ahead of and behind the current position.
   */
  int bufferPages() {
    return bufferPages;
  }

  /**
   * @return How much data to load at a time when paging data.
   */
  int pageSize() {
    return pageSize;
  }

  /**
   * @return What position to start loading at
   */
  int startIndex() {
    return startIndex;
  }

  public static class Builder {
    private int bufferPages = 1;
    private int startIndex  = 0;
    private int pageSize    = 50;

    public @NonNull Builder setBufferPages(int bufferPages) {
      if (bufferPages < 1) {
        throw new IllegalArgumentException("You must have at least one buffer page! Requested: " + bufferPages);
      }

      this.bufferPages = bufferPages;
      return this;
    }

    public @NonNull Builder setPageSize(int pageSize) {
      if (pageSize < 1) {
        throw new IllegalArgumentException("You must have a page size of at least one! Requested: " + pageSize);
      }

      this.pageSize = pageSize;
      return this;
    }

    public @NonNull Builder setStartIndex(int startIndex) {
      if (startIndex < 0) {
        throw new IndexOutOfBoundsException("Requested: " + startIndex);
      }

      this.startIndex = startIndex;
      return this;
    }

    public @NonNull PagingConfig build() {
      return new PagingConfig(this);
    }
  }
}
