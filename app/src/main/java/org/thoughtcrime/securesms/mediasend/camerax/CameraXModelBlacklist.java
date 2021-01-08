package org.thoughtcrime.securesms.mediasend.camerax;

import android.os.Build;

import java.util.HashSet;
import java.util.Set;

public final class CameraXModelBlacklist {
  private static final Set<String> BLACKLIST = new HashSet<String>() {{
    // Pixel 4
    add("Pixel 4");
    add("Pixel 4 XL");

    // Huawei Mate 10
    add("ALP-L29");
    add("ALP-L09");
    add("ALP-AL00");

    // Huawei Mate 10 Pro
    add("BLA-L29");
    add("BLA-L09");
    add("BLA-AL00");
    add("BLA-A09");

    // Huawei Mate 20
    add("HMA-L29");
    add("HMA-L09");
    add("HMA-LX9");
    add("HMA-AL00");

    // Huawei Mate 20 Pro
    add("LYA-L09");
    add("LYA-L29");
    add("LYA-AL00");
    add("LYA-AL10");
    add("LYA-TL00");
    add("LYA-L0C");

    // Huawei Mate 20 X
    add("EVR-L29");
    add("EVR-AL00");
    add("EVR-TL00");

    // Huawei P20
    add("EML-L29C");
    add("EML-L09C");
    add("EML-AL00");
    add("EML-TL00");
    add("EML-L29");
    add("EML-L09");

    // Huawei P20 Pro
    add("CLT-L29C");
    add("CLT-L29");
    add("CLT-L09C");
    add("CLT-L09");
    add("CLT-AL00");
    add("CLT-AL01");
    add("CLT-TL01");
    add("CLT-AL00L");
    add("CLT-L04");
    add("HW-01K");

    // Huawei P30
    add("ELE-L29");
    add("ELE-L09");
    add("ELE-AL00");
    add("ELE-TL00");
    add("ELE-L04");

    // Huawei P30 Pro
    add("VOG-L29");
    add("VOG-L09");
    add("VOG-AL00");
    add("VOG-TL00");
    add("VOG-L04");
    add("VOG-AL10");

    // Huawei Honor 10
    add("COL-AL10");
    add("COL-L29");
    add("COL-L19");

    // Huawei Honor 20
    add("YAL-L21");
    add("YAL-AL00");
    add("YAL-TL00");

    // Samsung Galaxy S6
    add("SM-G920F");

    // Honor View 10
    add("BKL-AL20");
    add("BKL-L04");
    add("BKL-L09");
    add("BKL-AL00");

    // Honor View 20
    add("PCT-AL10");
    add("PCT-TL10");
    add("PCT-L29");

    // Honor Play
    add("COR-L29");
    add("COR-L09");
    add("COR-AL00");
    add("COR-AL10");
    add("COR-TL10");
  }};

  private CameraXModelBlacklist() {
  }

  public static boolean isBlacklisted() {
    return BLACKLIST.contains(Build.MODEL);
  }
}
