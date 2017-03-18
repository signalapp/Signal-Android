/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

package ws.com.google.android.mms.pdu;

public class Base64 {
    /**
     * Used to get the number of Quadruples.
     */
    static final int FOURBYTE = 4;

    /**
     * Byte used to pad output.
     */
    static final byte PAD = (byte) '=';

    /**
     * The base length.
     */
    static final int BASELENGTH = 255;

    // Create arrays to hold the base64 characters
    private static byte[] base64Alphabet = new byte[BASELENGTH];

    // Populating the character arrays
    static {
        for (int i = 0; i < BASELENGTH; i++) {
            base64Alphabet[i] = (byte) -1;
        }
        for (int i = 'Z'; i >= 'A'; i--) {
            base64Alphabet[i] = (byte) (i - 'A');
        }
        for (int i = 'z'; i >= 'a'; i--) {
            base64Alphabet[i] = (byte) (i - 'a' + 26);
        }
        for (int i = '9'; i >= '0'; i--) {
            base64Alphabet[i] = (byte) (i - '0' + 52);
        }

        base64Alphabet['+'] = 62;
        base64Alphabet['/'] = 63;
    }

    /**
     * Decodes Base64 data into octects
     *
     * @param base64Data Byte array containing Base64 data
     * @return Array containing decoded data.
     */
    public static byte[] decodeBase64(byte[] base64Data) {
        // RFC 2045 requires that we discard ALL non-Base64 characters
        base64Data = discardNonBase64(base64Data);

        // handle the edge case, so we don't have to worry about it later
        if (base64Data.length == 0) {
            return new byte[0];
        }

        int numberQuadruple = base64Data.length / FOURBYTE;
        byte decodedData[] = null;
        byte b1 = 0, b2 = 0, b3 = 0, b4 = 0, marker0 = 0, marker1 = 0;

        // Throw away anything not in base64Data

        int encodedIndex = 0;
        int dataIndex = 0;
        {
            // this sizes the output array properly - rlw
            int lastData = base64Data.length;
            // ignore the '=' padding
            while (base64Data[lastData - 1] == PAD) {
                if (--lastData == 0) {
                    return new byte[0];
                }
            }
            decodedData = new byte[lastData - numberQuadruple];
        }

        for (int i = 0; i < numberQuadruple; i++) {
            dataIndex = i * 4;
            marker0 = base64Data[dataIndex + 2];
            marker1 = base64Data[dataIndex + 3];

            b1 = base64Alphabet[base64Data[dataIndex]];
            b2 = base64Alphabet[base64Data[dataIndex + 1]];

            if (marker0 != PAD && marker1 != PAD) {
                //No PAD e.g 3cQl
                b3 = base64Alphabet[marker0];
                b4 = base64Alphabet[marker1];

                decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
                decodedData[encodedIndex + 1] =
                    (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
                decodedData[encodedIndex + 2] = (byte) (b3 << 6 | b4);
            } else if (marker0 == PAD) {
                //Two PAD e.g. 3c[Pad][Pad]
                decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
            } else if (marker1 == PAD) {
                //One PAD e.g. 3cQ[Pad]
                b3 = base64Alphabet[marker0];

                decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
                decodedData[encodedIndex + 1] =
                    (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
            }
            encodedIndex += 3;
        }
        return decodedData;
    }

    /**
     * Check octect whether it is a base64 encoding.
     *
     * @param octect to be checked byte
     * @return ture if it is base64 encoding, false otherwise.
     */
    private static boolean isBase64(byte octect) {
        if (octect == PAD) {
            return true;
        } else if (base64Alphabet[octect] == -1) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Discards any characters outside of the base64 alphabet, per
     * the requirements on page 25 of RFC 2045 - "Any characters
     * outside of the base64 alphabet are to be ignored in base64
     * encoded data."
     *
     * @param data The base-64 encoded data to groom
     * @return The data, less non-base64 characters (see RFC 2045).
     */
    static byte[] discardNonBase64(byte[] data) {
        byte groomedData[] = new byte[data.length];
        int bytesCopied = 0;

        for (int i = 0; i < data.length; i++) {
            if (isBase64(data[i])) {
                groomedData[bytesCopied++] = data[i];
            }
        }

        byte packedData[] = new byte[bytesCopied];

        System.arraycopy(groomedData, 0, packedData, 0, bytesCopied);

        return packedData;
    }
}
