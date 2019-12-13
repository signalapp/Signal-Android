package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Maintains a list of "blessed" sticker packs that essentially serve as defaults.
 */
public final class BlessedPacks {

  public static final Pack ZOZO   = new Pack("fb535407d2f6497ec074df8b9c51dd1d", "17e971c134035622781d2ee249e6473b774583750b68c11bb82b7509c68b6dfd");
  public static final Pack BANDIT = new Pack("9acc9e8aba563d26a4994e69263e3b25", "5a6dff3948c28efb9b7aaf93ecc375c69fc316e78077ed26867a14d10a0f6a12");

  private static final Set<String> BLESSED_PACK_IDS = new HashSet<String>() {{
    add(ZOZO.getPackId());
    add(BANDIT.getPackId());
  }};

  public static boolean contains(@NonNull String packId) {
    return BLESSED_PACK_IDS.contains(packId);
  }

  public static class Pack {
    private final String packId;
    private final String packKey;

    public Pack(@NonNull String packId, @NonNull String packKey) {
      this.packId  = packId;
      this.packKey = packKey;
    }

    public @NonNull String getPackId() {
      return packId;
    }

    public @NonNull String getPackKey() {
      return packKey;
    }
  }
}
