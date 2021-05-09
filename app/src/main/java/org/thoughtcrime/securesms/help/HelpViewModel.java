package org.thoughtcrime.securesms.help;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.logsubmit.LogLine;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository;
import org.thoughtcrime.securesms.util.livedata.LiveDataPair;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class HelpViewModel extends ViewModel {

  private static final int MINIMUM_PROBLEM_CHARS = 10;

  private MutableLiveData<Boolean> problemMeetsLengthRequirements = new MutableLiveData<>();
  private MutableLiveData<Boolean> hasLines                       = new MutableLiveData<>(false);
  private MutableLiveData<Integer> categoryIndex                  = new MutableLiveData<>(0);
  private LiveData<Boolean>        isFormValid                    = Transformations.map(new LiveDataPair<>(problemMeetsLengthRequirements, hasLines), this::transformValidationData);

  private final SubmitDebugLogRepository submitDebugLogRepository;

  private List<LogLine> logLines;

  public HelpViewModel() {
    submitDebugLogRepository = new SubmitDebugLogRepository();

    submitDebugLogRepository.getLogLines(lines -> {
      logLines = lines;
      hasLines.postValue(true);
    });

    LiveData<Boolean> firstValid = LiveDataUtil.combineLatest(problemMeetsLengthRequirements, hasLines, (validLength, validLines) -> {
      return validLength == Boolean.TRUE && validLines == Boolean.TRUE;
    });

    isFormValid = LiveDataUtil.combineLatest(firstValid, categoryIndex, (valid, index) -> {
      return  valid == Boolean.TRUE && index > 0;
    });
  }

  LiveData<Boolean> isFormValid() {
    return isFormValid;
  }

  void onProblemChanged(@NonNull String problem) {
    problemMeetsLengthRequirements.setValue(problem.length() >= MINIMUM_PROBLEM_CHARS);
  }

  void onCategorySelected(int index) {
    this.categoryIndex.setValue(index);
  }

  int getCategoryIndex() {
    return Optional.fromNullable(this.categoryIndex.getValue()).or(0);
  }

  LiveData<SubmitResult> onSubmitClicked(boolean includeDebugLogs) {
    MutableLiveData<SubmitResult> resultLiveData = new MutableLiveData<>();

    if (includeDebugLogs) {
      submitDebugLogRepository.submitLog(logLines, result -> resultLiveData.postValue(new SubmitResult(result, result.isPresent())));
    } else {
      resultLiveData.postValue(new SubmitResult(Optional.absent(), false));
    }

    return resultLiveData;
  }

  private boolean transformValidationData(Pair<Boolean, Boolean> validationData) {
    return validationData.first() == Boolean.TRUE && validationData.second() == Boolean.TRUE;
  }

  static class SubmitResult {
    private final Optional<String> debugLogUrl;
    private final boolean          isError;

    private SubmitResult(@NonNull Optional<String> debugLogUrl, boolean isError) {
      this.debugLogUrl = debugLogUrl;
      this.isError     = isError;
    }

    @NonNull Optional<String> getDebugLogUrl() {
      return debugLogUrl;
    }

    boolean isError() {
      return isError;
    }
  }
}
