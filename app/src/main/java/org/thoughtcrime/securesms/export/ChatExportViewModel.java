package org.thoughtcrime.securesms.export;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class ChatExportViewModel extends ViewModel {

    private static final Boolean INITIAL_HTML_VIEWER_STATE = false;
    private static final Boolean INITIAL_MEDIA_STATE = false;
    private static final String INITIAL_TIME_PERIOD     = "Default (whole chat)";

    private final  MutableLiveData<Optional<Date>>      startDateControls          = new MutableLiveData<>();
    private final MutableLiveData<Optional<Date>>       endDateControls            = new MutableLiveData<>();

    private final MutableLiveData<String>         selectedTimePeriod         = new MutableLiveData<>(INITIAL_TIME_PERIOD);
    private final MutableLiveData<Boolean>        enableIncludeMediaControls = new MutableLiveData<>(INITIAL_MEDIA_STATE);
    private final        MutableLiveData<Boolean> enableHTMLViewerControls = new MutableLiveData<>(INITIAL_HTML_VIEWER_STATE);
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat         dateFormatter            = new SimpleDateFormat("EEE, dd MMM yyyy");



    public ChatExportViewModel () {
        this.startDateControls.setValue(Optional.fromNullable (new Date(0L)));
        this.endDateControls.setValue(Optional.fromNullable (new Date()));
        this.enableIncludeMediaControls.setValue(INITIAL_MEDIA_STATE);
        this.enableHTMLViewerControls.setValue(INITIAL_HTML_VIEWER_STATE);
        this.selectedTimePeriod.setValue(INITIAL_TIME_PERIOD);

    }

    public void setDateFrom(Date fromDate) {
        this.startDateControls.setValue(Optional.fromNullable (fromDate));
    }
    public void setDateUntil (Date endDate) {
        this.endDateControls.setValue(Optional.fromNullable ( endDate));
    }

    public Date getInitialDateFrom() {
        return new Date(0);
    }

    public LiveData<Optional<Date>> getDateFrom() {
        return startDateControls;
    }

    public LiveData<Optional<Date>> getDateUntil() {
        return endDateControls;
    }

    public String getCurrentSelectedTimePeriod(LiveData<Optional<Date>> from, LiveData<Optional<Date>> until){
        if (from == null) {
            return INITIAL_TIME_PERIOD;
        }
        else{
            String fromTimePeriod = dateFormatter.format (Objects.requireNonNull (from.getValue ()).get ());
            String untilTimePeriod = dateFormatter.format (Objects.requireNonNull (until.getValue ()).get ());
            return "From " + fromTimePeriod + " to " + untilTimePeriod;
        }

    }

    void setAllMedia (boolean shouldIncludeAllMedia) {
        enableIncludeMediaControls.setValue (shouldIncludeAllMedia);
    }

    void setViewer (boolean shouldIncludeViewer) {
        enableHTMLViewerControls.setValue (shouldIncludeViewer);
    }

    @NonNull LiveData<Boolean> getAllMedia() {

        return enableIncludeMediaControls;
    }

    @NonNull LiveData<Boolean> getViewer () {

        return enableHTMLViewerControls;
    }

    @NonNull LiveData<String> getSelectedTimePeriod () {
        if((getDateFrom ().getValue ().get ()).equals (getInitialDateFrom ())) selectedTimePeriod.setValue (INITIAL_TIME_PERIOD);
        else selectedTimePeriod.setValue (getCurrentSelectedTimePeriod (getDateFrom (),getDateUntil ()));
        return selectedTimePeriod;
    }

    boolean getCurrentSelectionAllMedia() {
        Boolean value = enableIncludeMediaControls.getValue ();
        if (value == null) {
            return INITIAL_MEDIA_STATE;
        }
        return value;
    }

    boolean getCurrentSelectionViewer () {
        Boolean value = enableHTMLViewerControls.getValue ();
        if (value == null) {
            return INITIAL_HTML_VIEWER_STATE;
        }
        return value;
    }
    void toggleSelectAllMedia() {
        enableIncludeMediaControls.postValue(!getCurrentSelectionAllMedia ());
    }


    void toggleSelectHTMLViewer() {
        enableHTMLViewerControls.postValue(!getCurrentSelectionViewer ());
    }

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(@Nullable RecipientId recipientId) {
        }

        @Override
        public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return Objects.requireNonNull(modelClass.cast(new ChatExportViewModel()));
        }
    }



}