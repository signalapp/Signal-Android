package org.thoughtcrime.securesms.util;

import android.os.Parcel;

public class MmsCharacterCalculator extends CharacterCalculator {

  private static final int MAX_SIZE = 5000;

  @Override
  public CharacterState calculateCharacters(String messageBody) {
    return new CharacterState(1, MAX_SIZE - messageBody.length(), MAX_SIZE, MAX_SIZE);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
  }

  public static final Creator<MmsCharacterCalculator> CREATOR = new Creator<MmsCharacterCalculator>() {
    @Override
    public MmsCharacterCalculator createFromParcel(Parcel in) {
      return new MmsCharacterCalculator();
    }

    @Override
    public MmsCharacterCalculator[] newArray(int size) {
      return new MmsCharacterCalculator[size];
    }
  };
}
