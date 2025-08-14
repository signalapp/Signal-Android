package org.thoughtcrime.securesms.logsubmit;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;
import org.signal.debuglogsviewer.DebugLogsViewer;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SubmitDebugLogViewModel extends ViewModel {

  private static final String TAG = Log.tag(SubmitDebugLogViewModel.class);

  private static final int CHUNK_SIZE = 10_000;

  private final SubmitDebugLogRepository    repo;
  private final MutableLiveData<Mode>       mode;
  private final SingleLiveEvent<Event>      event;
  private final long                        firstViewTime;
  private final byte[]                      trace;

  private SubmitDebugLogViewModel() {
    this.repo          = new SubmitDebugLogRepository();
    this.mode          = new MutableLiveData<>();
    this.trace         = Tracer.getInstance().serialize();
    this.firstViewTime = System.currentTimeMillis();
    this.event         = new SingleLiveEvent<>();
  }

  @NonNull Observable<List<String>> getLogLinesObservable() {
    return Observable.<List<String>>create(emitter -> {
      Stopwatch stopwatch = new Stopwatch("log-loading");
      try {
        mode.postValue(Mode.LOADING);

        repo.getPrefixLogLines(prefixLines -> {
          try {
            List<String> prefixStrings = new ArrayList<>();
            for (LogLine line : prefixLines) {
              prefixStrings.add(line.getText());
            }
            stopwatch.split("prefix");

            Log.blockUntilAllWritesFinished();
            stopwatch.split("flush");

            LogDatabase.getInstance(AppDependencies.getApplication()).logs().trimToSize();
            stopwatch.split("trim-old");

            if (!emitter.isDisposed()) {
              emitter.onNext(new ArrayList<>(prefixStrings));
            }

            List<String> currentChunk = new ArrayList<>();

            try (LogDatabase.LogTable.CursorReader logReader = (LogDatabase.LogTable.CursorReader) LogDatabase.getInstance(AppDependencies.getApplication()).logs().getAllBeforeTime(firstViewTime)) {
              stopwatch.split("initial-query");

              int count = 0;
              while (logReader.hasNext() && !emitter.isDisposed()) {
                String next = logReader.next();
                currentChunk.add(next);
                count++;

                if (count >= CHUNK_SIZE) {
                  emitter.onNext(currentChunk);
                  count = 0;
                  currentChunk = new ArrayList<>();
                }
              }

              // Send final chunk if any remaining
              if (!emitter.isDisposed() && count > 0) {
                emitter.onNext(currentChunk);
              }

              if (!emitter.isDisposed()) {
                mode.postValue(Mode.NORMAL);
                emitter.onComplete();
              }

              stopwatch.split("lines");
              stopwatch.stop(TAG);
            }
          } catch (Exception e) {
            if (!emitter.isDisposed()) {
              Log.e(TAG, "Error loading log lines", e);
              emitter.onError(e);
            }
          }
        });
      } catch (Exception e) {
        if (!emitter.isDisposed()) {
          Log.e(TAG, "Error creating log lines observable", e);
          emitter.onError(e);
        }
      }
    }).subscribeOn(Schedulers.io());
  }

  @NonNull LiveData<Mode> getMode() {
    return mode;
  }

  @NonNull LiveData<Optional<String>> onSubmitClicked(DebugLogsViewer.LogReader logReader) {
    mode.postValue(Mode.SUBMITTING);

    MutableLiveData<Optional<String>> result = new MutableLiveData<>();

    repo.submitLogFromReader(logReader, trace, value -> {
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

  boolean onBackPressed() {
    return false;
  }

  enum Mode {
    NORMAL, LOADING, SUBMITTING
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