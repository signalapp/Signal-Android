package org.thoughtcrime.securesms.help;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

public class HelpViewModel extends ViewModel {

  private static final int MINIMUM_PROBLEM_CHARS = 10;

  private final MutableLiveData<Boolean> problemMeetsLengthRequirements;
  private final MutableLiveData<Integer> categoryIndex;
  private final LiveData<Boolean>        isFormValid;

  private final SubmitDebugLogRepository submitDebugLogRepository;

  public HelpViewModel() {
    submitDebugLogRepository       = new SubmitDebugLogRepository();
    problemMeetsLengthRequirements = new MutableLiveData<>();
    categoryIndex                  = new MutableLiveData<>(0);

    isFormValid = LiveDataUtil.combineLatest(problemMeetsLengthRequirements, categoryIndex, (meetsLengthRequirements, index) -> {
      return meetsLengthRequirements == Boolean.TRUE && index > 0;
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
      submitDebugLogRepository.buildAndSubmitLog(result -> resultLiveData.postValue(new SubmitResult(result, result.isPresent())));
    } else {
      resultLiveData.postValue(new SubmitResult(Optional.absent(), false));
    }

    return resultLiveData;
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
