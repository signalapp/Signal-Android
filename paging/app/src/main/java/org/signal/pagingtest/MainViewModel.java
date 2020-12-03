package org.signal.pagingtest;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.signal.paging.PagedDataSource;
import org.signal.paging.PagingController;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagedData;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends ViewModel {

  private final PagedData<String> pagedData;
  private final MyDataSource      dataSource;

  public MainViewModel() {
    this.dataSource = new MyDataSource(1000);
    this.pagedData = PagedData.create(dataSource, new PagingConfig.Builder().setBufferPages(3)
                                                                            .setPageSize(25)
                                                                            .build());
  }

  public @NonNull LiveData<List<String>> getList() {
    return pagedData.getData();
  }

  public @NonNull PagingController getPagingController() {
    return pagedData.getController();
  }

  public void appendItems() {
    dataSource.setSize(dataSource.size() + 1);
    pagedData.getController().onDataInvalidated();
  }

  private static class MyDataSource implements PagedDataSource<String> {

    private int size;

    MyDataSource(int size) {
      this.size = size;
    }

    public void setSize(int size) {
      this.size = size;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public List<String> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      List<String> data = new ArrayList<>(length);

      for (int i = 0; i < length; i++) {
        data.add(String.valueOf(start + i) + "     (" + System.currentTimeMillis() + ")");
      }

      return data;
    }
  }
}
