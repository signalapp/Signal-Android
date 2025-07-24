package org.thoughtcrime.securesms.logsubmit;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;
import org.signal.paging.LivePagedData;
import org.signal.paging.PagedData;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagingController;
import org.signal.paging.ProxyPagingController;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubmitDebugLogViewModel extends ViewModel {

  private static final String TAG = Log.tag(SubmitDebugLogViewModel.class);

  private final SubmitDebugLogRepository        repo;
  private final MutableLiveData<Mode>           mode;
  private final ProxyPagingController<Long>     pagingController;
  private final List<LogLine>                   staticLines;
  private final MediatorLiveData<List<LogLine>> lines;
  private final SingleLiveEvent<Event>          event;
  private final long                            firstViewTime;
  private final byte[]                          trace;


  private SubmitDebugLogViewModel() {
    this.repo             = new SubmitDebugLogRepository();
    this.mode             = new MutableLiveData<>();
    this.trace            = Tracer.getInstance().serialize();
    this.pagingController = new ProxyPagingController<>();
    this.firstViewTime    = System.currentTimeMillis();
    this.staticLines      = new ArrayList<>();
    this.lines            = new MediatorLiveData<>();
    this.event            = new SingleLiveEvent<>();

    repo.getPrefixLogLines(staticLines -> {
      this.staticLines.addAll(staticLines);

      Log.blockUntilAllWritesFinished();
      LogDatabase.getInstance(AppDependencies.getApplication()).logs().trimToSize();

      LogDataSource dataSource = new LogDataSource(AppDependencies.getApplication(), staticLines, firstViewTime);
      PagingConfig  config     = new PagingConfig.Builder().setPageSize(100)
                                                           .setBufferPages(3)
                                                           .setStartIndex(0)
                                                           .build();

      LivePagedData<Long, LogLine> pagedData = PagedData.createForLiveData(dataSource, config);

      ThreadUtil.runOnMain(() -> {
        pagingController.set(pagedData.getController());
        lines.addSource(pagedData.getData(), lines::setValue);
        mode.setValue(Mode.NORMAL);
      });
    });
  }

  @NonNull LiveData<List<LogLine>> getLines() {
    return lines;
  }

  @NonNull PagingController getPagingController() {
    return pagingController;
  }

  @NonNull LiveData<Mode> getMode() {
    return mode;
  }

  @NonNull LiveData<Optional<String>> onSubmitClicked() {
    mode.postValue(Mode.SUBMITTING);

    MutableLiveData<Optional<String>> result = new MutableLiveData<>();

    repo.submitLogWithPrefixLines(firstViewTime, staticLines, trace, value -> {
      mode.postValue(Mode.NORMAL);
      result.postValue(value);
    });

    return result;
  }

  @NonNull LiveData<Event> getEvents() {
    return event;
  }

  void onDiskSaveLocationReady(@Nullable Uri uri) {
    if (uri == null) {
      Log.w(TAG, "Null URI!");
      event.postValue(Event.FILE_SAVE_ERROR);
      return;
    }

    repo.writeLogToDisk(uri, firstViewTime, success -> {
      if (success) {
        event.postValue(Event.FILE_SAVE_SUCCESS);
      } else {
        event.postValue(Event.FILE_SAVE_ERROR);
      }
    });
  }

  void onQueryUpdated(@NonNull String query) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onSearchClosed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onEditButtonPressed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onDoneEditingButtonPressed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onLogDeleted(@NonNull LogLine line) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  boolean onBackPressed() {
    if (mode.getValue() == Mode.EDIT) {
      mode.setValue(Mode.NORMAL);
      return true;
    } else {
      return false;
    }
  }

  enum Mode {
    NORMAL, EDIT, SUBMITTING
  }

  enum Event {
    FILE_SAVE_SUCCESS, FILE_SAVE_ERROR
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new SubmitDebugLogViewModel());
    }
  }
}
