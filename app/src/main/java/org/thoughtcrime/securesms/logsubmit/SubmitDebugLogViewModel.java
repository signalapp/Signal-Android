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
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;
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
  private final List<LogLine>                   staticLines;
  private final MediatorLiveData<List<LogLine>> lines;
  private final SingleLiveEvent<Event>          event;
  private final long                            firstViewTime;
  private final byte[]                          trace;
  private final List<LogLine>                   allLines;

  private SubmitDebugLogViewModel() {
    this.repo             = new SubmitDebugLogRepository();
    this.mode             = new MutableLiveData<>();
    this.trace            = Tracer.getInstance().serialize();
    this.firstViewTime    = System.currentTimeMillis();
    this.staticLines      = new ArrayList<>();
    this.lines            = new MediatorLiveData<>();
    this.event            = new SingleLiveEvent<>();
    this.allLines         = new ArrayList<>();

    repo.getPrefixLogLines(staticLines -> {
      this.staticLines.addAll(staticLines);

      Log.blockUntilAllWritesFinished();
      LogDatabase.getInstance(AppDependencies.getApplication()).logs().trimToSize();
      SignalExecutors.UNBOUNDED.execute(() -> {
        allLines.clear();
        allLines.addAll(staticLines);

        try (LogDatabase.LogTable.CursorReader logReader = (LogDatabase.LogTable.CursorReader) LogDatabase.getInstance(AppDependencies.getApplication()).logs().getAllBeforeTime(firstViewTime)) {
          while (logReader.hasNext()) {
            String next = logReader.next();
            allLines.add(new SimpleLogLine(next, LogStyleParser.parseStyle(next), LogLine.Placeholder.NONE));
          }
        }

        ThreadUtil.runOnMain(() -> {
          lines.setValue(allLines);
          mode.setValue(Mode.NORMAL);
        });
      });
    });
  }

  @NonNull LiveData<List<LogLine>> getLines() {
    return lines;
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