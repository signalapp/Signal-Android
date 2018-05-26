package org.thoughtcrime.securesms.contactshare;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import static org.thoughtcrime.securesms.contactshare.Contact.*;

public class ContactNameEditViewModel extends ViewModel {

  private final MutableLiveData<String> displayName;

  private String givenName;
  private String familyName;
  private String middleName;
  private String prefix;
  private String suffix;

  public ContactNameEditViewModel() {
    this.displayName = new MutableLiveData<>();
  }

  void setName(@NonNull Name name) {
    givenName  = name.getGivenName();
    familyName = name.getFamilyName();
    middleName = name.getMiddleName();
    prefix     = name.getPrefix();
    suffix     = name.getSuffix();

    displayName.postValue(buildDisplayName());
  }

  Name getName() {
    return new Name(displayName.getValue(), givenName, familyName, prefix, suffix, middleName);
  }

  LiveData<String> getDisplayName() {
    return displayName;
  }

  void updateGivenName(@NonNull String givenName) {
    this.givenName = givenName;
    displayName.postValue(buildDisplayName());
  }

  void updateFamilyName(@NonNull String familyName) {
    this.familyName = familyName;
    displayName.postValue(buildDisplayName());
  }

  void updatePrefix(@NonNull String prefix) {
    this.prefix = prefix;
    displayName.postValue(buildDisplayName());
  }

  void updateSuffix(@NonNull String suffix) {
    this.suffix = suffix;
    displayName.postValue(buildDisplayName());
  }

  void updateMiddleName(@NonNull String middleName) {
    this.middleName = middleName;
    displayName.postValue(buildDisplayName());
  }

  private String buildDisplayName() {
    boolean isCJKV = isCJKV(givenName) && isCJKV(middleName) && isCJKV(familyName) && isCJKV(prefix) && isCJKV(suffix);
    if (isCJKV) {
      return joinString(familyName, givenName, prefix, suffix, middleName);
    }
    return joinString(prefix, givenName, middleName, familyName, suffix);
  }

  private String joinString(String... values) {
    StringBuilder builder = new StringBuilder();

    for (String value : values) {
      if (!TextUtils.isEmpty(value)) {
        builder.append(value).append(' ');
      }
    }

    return builder.toString().trim();
  }

  private boolean isCJKV(@Nullable String value) {
    if  (TextUtils.isEmpty(value)) {
      return true;
    }

    for (int offset = 0; offset < value.length(); ) {
      int codepoint = Character.codePointAt(value, offset);

      if (!isCodepointCJKV(codepoint)) {
        return false;
      }

      offset += Character.charCount(codepoint);
    }

    return true;
  }

  private boolean isCodepointCJKV(int codepoint) {
    if (codepoint == (int)' ') return true;
    
    Character.UnicodeBlock block = Character.UnicodeBlock.of(codepoint);

    boolean isCJKV = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)                  ||
                     Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)      ||
                     Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B.equals(block)      ||
                     Character.UnicodeBlock.CJK_COMPATIBILITY.equals(block)                       ||
                     Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS.equals(block)                 ||
                     Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block)            ||
                     Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT.equals(block) ||
                     Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT.equals(block)                 ||
                     Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.equals(block)             ||
                     Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS.equals(block)         ||
                     Character.UnicodeBlock.KANGXI_RADICALS.equals(block)                         ||
                     Character.UnicodeBlock.IDEOGRAPHIC_DESCRIPTION_CHARACTERS.equals(block)      ||
                     Character.UnicodeBlock.HIRAGANA.equals(block)                                ||
                     Character.UnicodeBlock.KATAKANA.equals(block)                                ||
                     Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS.equals(block)            ||
                     Character.UnicodeBlock.HANGUL_JAMO.equals(block)                             ||
                     Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO.equals(block)               ||
                     Character.UnicodeBlock.HANGUL_SYLLABLES.equals(block);

    if (Build.VERSION.SDK_INT >= 19) {
      isCJKV |= Character.isIdeographic(codepoint);
    }

    return isCJKV;
  }
}
