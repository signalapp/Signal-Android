package org.signal.pagingtest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.paging.PagedDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

class MainDataSource implements PagedDataSource<String, Item> {

  private final List<Item> items = new ArrayList<>();

  MainDataSource(int size) {
    buildItems(size);
  }

  @Override
  public int size() {
    return items.size();
  }

  @Override
  public @NonNull List<Item> load(int start, int length, int totalSize, @NonNull CancellationSignal cancellationSignal) {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    return items.subList(start, start + length);
  }

  @Override
  public @Nullable Item load(String key) {
    return items.stream().filter(item -> item.key.equals(key)).findFirst().orElse(null);
  }

  @Override
  public @NonNull String getKey(@NonNull Item item) {
    return item.key;
  }

  public void updateItem(@NonNull String key) {
    ListIterator<Item> iter = items.listIterator();
    while (iter.hasNext()) {
      if (iter.next().key.equals(key)) {
        iter.set(new Item(key, System.currentTimeMillis()));
        break;
      }
    }
  }

  public @NonNull String prepend() {
    Item item = new Item(UUID.randomUUID().toString(), System.currentTimeMillis());
    items.add(0, item);
    return item.key;
  }

  private void buildItems(int size) {
    items.clear();

    for (int i = 0; i < size; i++) {
      items.add(new Item(UUID.randomUUID().toString(), System.currentTimeMillis()));
    }
  }
}
