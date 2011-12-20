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

import java.io.ByteArrayOutputStream;

public class QuotedPrintable {
    private static byte ESCAPE_CHAR = '=';

    /**
     * Decodes an array quoted-printable characters into an array of original bytes.
     * Escaped characters are converted back to their original representation.
     *
     * <p>
     * This function implements a subset of
     * quoted-printable encoding specification (rule #1 and rule #2)
     * as defined in RFC 1521.
     * </p>
     *
     * @param bytes array of quoted-printable characters
     * @return array of original bytes,
     *         null if quoted-printable decoding is unsuccessful.
     */
    public static final byte[] decodeQuotedPrintable(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            if (b == ESCAPE_CHAR) {
                try {
                    if('\r' == (char)bytes[i + 1] &&
                            '\n' == (char)bytes[i + 2]) {
                        i += 2;
                        continue;
                    }
                    int u = Character.digit((char) bytes[++i], 16);
                    int l = Character.digit((char) bytes[++i], 16);
                    if (u == -1 || l == -1) {
                        return null;
                    }
                    buffer.write((char) ((u << 4) + l));
                } catch (ArrayIndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                buffer.write(b);
            }
        }
        return buffer.toByteArray();
    }
}
