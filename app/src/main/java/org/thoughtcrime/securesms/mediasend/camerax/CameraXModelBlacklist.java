package org.thoughtcrime.securesms.mediasend.camerax;

import android.os.Build;

import java.util.HashMap;

public final class CameraXModelBlacklist {
  // we list here the name of the phone and the maximum sdk version to be blacklisted
  // if the device is listed here and has a sdk version <= the value listed, it will be blacklisted
  private static Integer ALL_SDK_VERSION = Integer.MAX_VALUE;
  private static final HashMap<String, Integer> BLACKLIST = new HashMap<String, Integer>() {{
    // Pixel 4
    put("Pixel 4", ALL_SDK_VERSION);
    put("Pixel 4 XL", ALL_SDK_VERSION);

    // Huawei Mate 10
    put("ALP-L29",ALL_SDK_VERSION);
    put("ALP-L09",ALL_SDK_VERSION);
    put("ALP-AL00",ALL_SDK_VERSION);

    // Huawei Mate 10 Pro
    put("BLA-L29",ALL_SDK_VERSION);
    put("BLA-L09",ALL_SDK_VERSION);
    put("BLA-AL00",ALL_SDK_VERSION);
    put("BLA-A09",ALL_SDK_VERSION);

    // Huawei Mate 20
    put("HMA-L29",ALL_SDK_VERSION);
    put("HMA-L09",ALL_SDK_VERSION);
    put("HMA-LX9",ALL_SDK_VERSION);
    put("HMA-AL00",ALL_SDK_VERSION);

    // Huawei Mate 20 Pro
    put("LYA-L09",ALL_SDK_VERSION);
    put("LYA-L29",ALL_SDK_VERSION);
    put("LYA-AL00",ALL_SDK_VERSION);
    put("LYA-AL10",ALL_SDK_VERSION);
    put("LYA-TL00",ALL_SDK_VERSION);
    put("LYA-L0C",ALL_SDK_VERSION);

    // Huawei Mate 20 X
    put("EVR-L29",ALL_SDK_VERSION);
    put("EVR-AL00",ALL_SDK_VERSION);
    put("EVR-TL00",ALL_SDK_VERSION);

    // Huawei P20
    put("EML-L29C",ALL_SDK_VERSION);
    put("EML-L09C",ALL_SDK_VERSION);
    put("EML-AL00",ALL_SDK_VERSION);
    put("EML-TL00",ALL_SDK_VERSION);
    put("EML-L29",ALL_SDK_VERSION);
    put("EML-L09",ALL_SDK_VERSION);

    // Huawei P20 Pro
    put("CLT-L29C",ALL_SDK_VERSION);
    put("CLT-L29",ALL_SDK_VERSION);
    put("CLT-L09C",ALL_SDK_VERSION);
    put("CLT-L09",ALL_SDK_VERSION);
    put("CLT-AL00",ALL_SDK_VERSION);
    put("CLT-AL01",ALL_SDK_VERSION);
    put("CLT-TL01",ALL_SDK_VERSION);
    put("CLT-AL00L",ALL_SDK_VERSION);
    put("CLT-L04",ALL_SDK_VERSION);
    put("HW-01K",ALL_SDK_VERSION);

    // Huawei P30
    put("ELE-L29",ALL_SDK_VERSION);
    put("ELE-L09",ALL_SDK_VERSION);
    put("ELE-AL00",ALL_SDK_VERSION);
    put("ELE-TL00",ALL_SDK_VERSION);
    put("ELE-L04",ALL_SDK_VERSION);

    // Huawei P30 Pro
    put("VOG-L29",ALL_SDK_VERSION);
    put("VOG-L09",ALL_SDK_VERSION);
    put("VOG-AL00",ALL_SDK_VERSION);
    put("VOG-TL00",ALL_SDK_VERSION);
    put("VOG-L04",ALL_SDK_VERSION);
    put("VOG-AL10",ALL_SDK_VERSION);

    // Huawei Honor 10
    put("COL-AL10",ALL_SDK_VERSION);
    put("COL-L29",ALL_SDK_VERSION);
    put("COL-L19",ALL_SDK_VERSION);

    // Huawei Honor 20
    put("YAL-L21",ALL_SDK_VERSION);
    put("YAL-AL00",ALL_SDK_VERSION);
    put("YAL-TL00",ALL_SDK_VERSION);

    // Samsung Galaxy S6
    put("SM-G920F",ALL_SDK_VERSION);

    // Honor View 10
    put("BKL-AL20",ALL_SDK_VERSION);
    put("BKL-L04",ALL_SDK_VERSION);
    put("BKL-L09",ALL_SDK_VERSION);
    put("BKL-AL00",ALL_SDK_VERSION);

    // Honor View 20
    put("PCT-AL10",ALL_SDK_VERSION);
    put("PCT-TL10",ALL_SDK_VERSION);
    put("PCT-L29",ALL_SDK_VERSION);

    // Honor Play
    put("COR-L29",ALL_SDK_VERSION);
    put("COR-L09",ALL_SDK_VERSION);
    put("COR-AL00",ALL_SDK_VERSION);
    put("COR-AL10",ALL_SDK_VERSION);
    put("COR-TL10",ALL_SDK_VERSION);
  }};

  private CameraXModelBlacklist() {
  }

  public static boolean isBlacklisted() {
    final Integer max_invalid_sdk = BLACKLIST.get(Build.MODEL);
    return max_invalid_sdk != null && Build.VERSION.SDK_INT <= max_invalid_sdk;
  }
}
