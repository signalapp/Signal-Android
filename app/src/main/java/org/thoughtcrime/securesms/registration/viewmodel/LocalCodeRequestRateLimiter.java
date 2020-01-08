package org.thoughtcrime.securesms.registration.viewmodel;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class LocalCodeRequestRateLimiter implements Parcelable {

  private final long                                    timePeriod;
  private final Map<RegistrationCodeRequest.Mode, Data> dataMap;

  public LocalCodeRequestRateLimiter(long timePeriod) {
    this.timePeriod = timePeriod;
    this.dataMap    = new HashMap<>();
  }

  @MainThread
  public boolean canRequest(@NonNull RegistrationCodeRequest.Mode mode, @NonNull String e164Number, long currentTime) {
    Data data = dataMap.get(mode);

    return data == null || !data.limited(e164Number, currentTime);
  }

  /**
   * Call this when the server has returned that it was successful in requesting a code via the specified mode.
   */
  @MainThread
  public void onSuccessfulRequest(@NonNull RegistrationCodeRequest.Mode mode, @NonNull String e164Number, long currentTime) {
    dataMap.put(mode, new Data(e164Number, currentTime + timePeriod));
  }

  /**
   * Call this if a mode was unsuccessful in sending.
   */
  @MainThread
  public void onUnsuccessfulRequest() {
    dataMap.clear();
  }

  static class Data {

    final String e164Number;
    final long   limitedUntil;

    Data(@NonNull String e164Number, long limitedUntil) {
      this.e164Number   = e164Number;
      this.limitedUntil = limitedUntil;
    }

    boolean limited(String e164Number, long currentTime) {
      return this.e164Number.equals(e164Number) && currentTime < limitedUntil;
    }
  }

  public static final Creator<LocalCodeRequestRateLimiter> CREATOR = new Creator<LocalCodeRequestRateLimiter>() {
    @Override
    public LocalCodeRequestRateLimiter createFromParcel(Parcel in) {
      long timePeriod         = in.readLong();
      int  numberOfMapEntries = in.readInt();

      LocalCodeRequestRateLimiter localCodeRequestRateLimiter = new LocalCodeRequestRateLimiter(timePeriod);

      for (int i = 0; i < numberOfMapEntries; i++) {
        RegistrationCodeRequest.Mode mode         = RegistrationCodeRequest.Mode.values()[in.readInt()];
        String                       e164Number   = in.readString();
        long                         limitedUntil = in.readLong();

        localCodeRequestRateLimiter.dataMap.put(mode, new Data(Objects.requireNonNull(e164Number), limitedUntil));
      }
      return localCodeRequestRateLimiter;
    }

    @Override
    public LocalCodeRequestRateLimiter[] newArray(int size) {
      return new LocalCodeRequestRateLimiter[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(timePeriod);
    dest.writeInt(dataMap.size());

    for (Map.Entry<RegistrationCodeRequest.Mode, Data> a : dataMap.entrySet()) {
      dest.writeInt(a.getKey().ordinal());
      dest.writeString(a.getValue().e164Number);
      dest.writeLong(a.getValue().limitedUntil);
    }
  }
}

