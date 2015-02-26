package org.thoughtcrime.securesms;

import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.EncryptedSmsCharacterCalculator;
import org.thoughtcrime.securesms.util.PushCharacterCalculator;
import org.thoughtcrime.securesms.util.SmsCharacterCalculator;

public class TransportOption {
  public final int           drawableButtonIcon;
  public final int           drawableSendButtonIcon;
  public String              text;
  public String              key;
  public String              composeHint;
  public CharacterCalculator characterCalculator;

  public TransportOption(String key, int drawableButtonIcon, int drawableSendButtonIcon, String text, String composeHint) {
    this.key                    = key;
    this.drawableButtonIcon     = drawableButtonIcon;
    this.drawableSendButtonIcon = drawableSendButtonIcon;
    this.text                   = text;
    this.composeHint            = composeHint;

    if (isPlaintext() && isSms()) {
      this.characterCalculator = new SmsCharacterCalculator();
    } else if (isSms()) {
      this.characterCalculator = new EncryptedSmsCharacterCalculator();
    } else {
      this.characterCalculator = new PushCharacterCalculator();
    }
  }

  public boolean isPlaintext() {
    return key.equals("insecure_sms");
  }

  public boolean isSms() {
    return key.equals("insecure_sms") || key.equals("secure_sms");
  }

  public CharacterState calculateCharacters(int charactersSpent) {
    return characterCalculator.calculateCharacters(charactersSpent);
  }
}
