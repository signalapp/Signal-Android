package org.thoughtcrime.securesms.ringrtc;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrap turn server info so it can be sent via an intent.
 */
public class TurnServerInfoParcel implements Parcelable {

  private final String       username;
  private final String       password;
  private final List<String> urls;

  public TurnServerInfoParcel(@NonNull TurnServerInfo turnServerInfo) {
    urls     = new ArrayList<>(turnServerInfo.getUrls());
    username = turnServerInfo.getUsername();
    password = turnServerInfo.getPassword();
  }

  private TurnServerInfoParcel(@NonNull Parcel in) {
    username = in.readString();
    password = in.readString();
    urls     = in.createStringArrayList();
  }

  public @Nullable String getUsername() {
    return username;
  }

  public @Nullable String getPassword() {
    return password;
  }

  public @NonNull List<String> getUrls() {
    return urls;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(username);
    dest.writeString(password);
    dest.writeStringList(urls);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<TurnServerInfoParcel> CREATOR = new Creator<TurnServerInfoParcel>() {
    @Override
    public TurnServerInfoParcel createFromParcel(Parcel in) {
      return new TurnServerInfoParcel(in);
    }

    @Override
    public TurnServerInfoParcel[] newArray(int size) {
      return new TurnServerInfoParcel[size];
    }
  };
}
