/*
 * Copyright (C) 2012-2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Attempts to substitute characters that cannot be encoded in the limited
 * GSM 03.38 character set. In many cases this will prevent sending a message
 * containing characters that would switch the message from 7-bit GSM
 * encoding (160 char limit) to 16-bit Unicode encoding (70 char limit).
 */
public class UnicodeFilter {
    private static final Pattern DIACRITICS_PATTERN =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}");

    public static String filter(String source) {
        StringBuilder output = new StringBuilder();
        final int sourceLength = source.length();

        for (int i = 0; i < sourceLength; i++) {
            String s = String.valueOf(source.charAt(i));

            // Try normalizing the character into Unicode NFKD form and
            // stripping out diacritic mark characters.
            s = Normalizer.normalize(s, Normalizer.Form.NFKD);
            s = DIACRITICS_PATTERN.matcher(s).replaceAll("");

            // Special case characters that don't get stripped by the
            // above technique.
            s = s.replace("Œ", "OE");
            s = s.replace("œ", "oe");
            s = s.replace("Ł", "L");
            s = s.replace("ł", "l");
            s = s.replace("Đ", "Dj");
            s = s.replace("đ", "dj");
            s = s.replace("Α", "A");
            s = s.replace("Β", "B");
            s = s.replace("Ε", "E");
            s = s.replace("Ζ", "Z");
            s = s.replace("Η", "H");
            s = s.replace("Ι", "I");
            s = s.replace("Κ", "K");
            s = s.replace("Μ", "M");
            s = s.replace("Ν", "N");
            s = s.replace("Ο", "O");
            s = s.replace("Ρ", "P");
            s = s.replace("Τ", "T");
            s = s.replace("Υ", "Y");
            s = s.replace("Χ", "X");
            s = s.replace("α", "A");
            s = s.replace("β", "B");
            s = s.replace("γ", "Γ");
            s = s.replace("δ", "Δ");
            s = s.replace("ε", "E");
            s = s.replace("ζ", "Z");
            s = s.replace("η", "H");
            s = s.replace("θ", "Θ");
            s = s.replace("ι", "I");
            s = s.replace("κ", "K");
            s = s.replace("λ", "Λ");
            s = s.replace("μ", "M");
            s = s.replace("ν", "N");
            s = s.replace("ξ", "Ξ");
            s = s.replace("ο", "O");
            s = s.replace("π", "Π");
            s = s.replace("ρ", "P");
            s = s.replace("σ", "Σ");
            s = s.replace("τ", "T");
            s = s.replace("υ", "Y");
            s = s.replace("φ", "Φ");
            s = s.replace("χ", "X");
            s = s.replace("ψ", "Ψ");
            s = s.replace("ω", "Ω");
            s = s.replace("ς", "Σ");
            output.append(s);
        }

        return output.toString();
    }
}
