package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Maintains a list of "blessed" sticker packs that essentially serve as defaults.
 */
public final class BlessedPacks {

  public static final Pack ZOZO          = new Pack("fb535407d2f6497ec074df8b9c51dd1d", "17e971c134035622781d2ee249e6473b774583750b68c11bb82b7509c68b6dfd");
  public static final Pack BANDIT        = new Pack("9acc9e8aba563d26a4994e69263e3b25", "5a6dff3948c28efb9b7aaf93ecc375c69fc316e78077ed26867a14d10a0f6a12");
  public static final Pack SWOON_HANDS   = new Pack("e61fa0867031597467ccc036cc65d403", "13ae7b1a7407318280e9b38c1261ded38e0e7138b9f964a6ccbb73e40f737a9b");
  public static final Pack SWOON_FACES   = new Pack("cca32f5b905208b7d0f1e17f23fdc185", "8bf8e95f7a45bdeafe0c8f5b002ef01ab95b8f1b5baac4019ccd6b6be0b1837a");
  public static final Pack DAY_BY_DAY    = new Pack("cfc50156556893ef9838069d3890fe49", "5f5beab7d382443cb00a1e48eb95297b6b8cadfd0631e5d0d9dc949e6999ff4b");
  public static final Pack MY_DAILY_LIFE = new Pack("ccc89a05dc077856b57351e90697976c", "45730e60f09d5566115223744537a6b7d9ea99ceeacb77a1fbd6801b9607fbcf");

  private static final Set<String> BLESSED_PACK_IDS = new HashSet<String>() {{
    add(ZOZO.getPackId());
    add(BANDIT.getPackId());
    add(SWOON_HANDS.getPackId());
    add(SWOON_FACES.getPackId());
    add(DAY_BY_DAY.getPackId());
    add(MY_DAILY_LIFE.getPackId());
  }};

  public static boolean contains(@NonNull String packId) {
    return BLESSED_PACK_IDS.contains(packId);
  }

  public static class Pack {
    @JsonProperty private final String packId;
    @JsonProperty private final String packKey;

    public Pack(@NonNull @JsonProperty("packId") String packId,
                @NonNull @JsonProperty("packKey") String packKey)
    {
      this.packId  = packId;
      this.packKey = packKey;
    }

    public @NonNull String getPackId() {
      return packId;
    }

    public @NonNull String getPackKey() {
      return packKey;
    }

    public @NonNull String toJson() {
      return JsonUtil.toJson(this);
    }

    public static @NonNull Pack fromJson(@NonNull String json) {
      try {
        return JsonUtil.fromJson(json, Pack.class);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}
