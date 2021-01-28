package org.thoughtcrime.securesms.logsubmit;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.signal.core.util.tracing.Tracer;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;

public class SubmitDebugLogViewModel extends ViewModel {

  private final SubmitDebugLogRepository            repo;
  private final DefaultValueLiveData<List<LogLine>> lines;
  private final MutableLiveData<Mode>               mode;

  private List<LogLine> sourceLines;
  private byte[]        trace;

  private SubmitDebugLogViewModel() {
    this.repo  = new SubmitDebugLogRepository();
    this.lines = new DefaultValueLiveData<>(Collections.emptyList());
    this.mode  = new MutableLiveData<>();
    this.trace = Tracer.getInstance().serialize();

    repo.getLogLines(result -> {
      sourceLines = result;
      mode.postValue(Mode.NORMAL);
      lines.postValue(sourceLines);
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

    repo.submitLog(lines.getValue(), trace, value -> {
      mode.postValue(Mode.NORMAL);
      result.postValue(value);
    });

    return result;
  }

  void onQueryUpdated(@NonNull String query) {
    if (TextUtils.isEmpty(query)) {
      lines.postValue(sourceLines);
    } else {
      List<LogLine> filtered = Stream.of(sourceLines)
                                     .filter(l -> l.getText().toLowerCase().contains(query.toLowerCase()))
                                     .toList();
      lines.postValue(filtered);
    }
  }

  void onSearchClosed() {
    lines.postValue(sourceLines);
  }

  void onEditButtonPressed() {
    mode.setValue(Mode.EDIT);
  }

  void onDoneEditingButtonPressed() {
    mode.setValue(Mode.NORMAL);
  }

  void onLogDeleted(@NonNull LogLine line) {
    sourceLines.remove(line);

    List<LogLine> logs = lines.getValue();
    logs.remove(line);

    lines.postValue(logs);
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

  public static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new SubmitDebugLogViewModel());
    }
  }
}
