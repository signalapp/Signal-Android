/**
 * Copyright (C) 2015 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.os.Parcel;
import androidx.annotation.NonNull;

public abstract class CharacterCalculator {

  public abstract CharacterState calculateCharacters(String messageBody);

  public static CharacterCalculator readFromParcel(@NonNull Parcel in) {
    switch (in.readInt()) {
      case 1:  return new SmsCharacterCalculator();
      case 2:  return new MmsCharacterCalculator();
      case 3:  return new PushCharacterCalculator();
      default: throw new IllegalArgumentException("Read an unsupported value for a calculator.");
    }
  }

  public static void writeToParcel(@NonNull Parcel dest, @NonNull CharacterCalculator calculator) {
    if (calculator instanceof SmsCharacterCalculator) {
      dest.writeInt(1);
    } else if (calculator instanceof MmsCharacterCalculator) {
      dest.writeInt(2);
    } else if (calculator instanceof PushCharacterCalculator) {
      dest.writeInt(3);
    } else {
      throw new IllegalArgumentException("Tried to write an unsupported calculator to a parcel.");
    }
  }

  public static class CharacterState {
    public final int charactersRemaining;
    public final int messagesSpent;
    public final int maxTotalMessageSize;
    public final int maxPrimaryMessageSize;

    public CharacterState(int messagesSpent, int charactersRemaining, int maxTotalMessageSize, int maxPrimaryMessageSize) {
      this.messagesSpent         = messagesSpent;
      this.charactersRemaining   = charactersRemaining;
      this.maxTotalMessageSize   = maxTotalMessageSize;
      this.maxPrimaryMessageSize = maxPrimaryMessageSize;
    }
  }
}

