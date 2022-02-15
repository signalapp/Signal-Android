package org.thoughtcrime.securesms.pin;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.KbsEnclave;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

public class TokenData implements Parcelable {
  private final KbsEnclave    enclave;
  private final String        basicAuth;
  private final TokenResponse tokenResponse;

  TokenData(@NonNull KbsEnclave enclave, @NonNull String basicAuth, @NonNull TokenResponse tokenResponse) {
    this.enclave       = enclave;
    this.basicAuth     = basicAuth;
    this.tokenResponse = tokenResponse;
  }

  private TokenData(Parcel in) {
    this.enclave   = new KbsEnclave(in.readString(), in.readString(), in.readString());
    this.basicAuth = in.readString();

    byte[] backupId = in.createByteArray();
    byte[] token    = in.createByteArray();

    this.tokenResponse = new TokenResponse(backupId, token, in.readInt());
  }

  public static @NonNull TokenData withResponse(@NonNull TokenData data, @NonNull TokenResponse response) {
    return new TokenData(data.getEnclave(), data.getBasicAuth(), response);
  }

  public int getTriesRemaining() {
    return tokenResponse.getTries();
  }

  public @NonNull String getBasicAuth() {
    return basicAuth;
  }

  public @NonNull TokenResponse getTokenResponse() {
    return tokenResponse;
  }

  public @NonNull KbsEnclave getEnclave() {
    return enclave;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(enclave.getEnclaveName());
    dest.writeString(enclave.getServiceId());
    dest.writeString(enclave.getMrEnclave());

    dest.writeString(basicAuth);

    dest.writeByteArray(tokenResponse.getBackupId());
    dest.writeByteArray(tokenResponse.getToken());
    dest.writeInt(tokenResponse.getTries());
  }

  public static final Creator<TokenData> CREATOR = new Creator<TokenData>() {
    @Override
    public TokenData createFromParcel(Parcel in) {
      return new TokenData(in);
    }

    @Override
    public TokenData[] newArray(int size) {
      return new TokenData[size];
    }
  };

}
