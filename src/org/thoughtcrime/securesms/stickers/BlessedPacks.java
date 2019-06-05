package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Maintains a list of "blessed" sticker packs that essentially serve as defaults.
 */
public final class BlessedPacks {

  private static final Set<String> BLESSED_PACK_IDS = new HashSet<>();

  public static boolean contains(@NonNull String packId) {
    return BLESSED_PACK_IDS.contains(packId);
  }
}
