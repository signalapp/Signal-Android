/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

public class PduComposer {
    /**
     * Address type.
     */
    static private final int PDU_PHONE_NUMBER_ADDRESS_TYPE = 1;
    static private final int PDU_EMAIL_ADDRESS_TYPE = 2;
    static private final int PDU_IPV4_ADDRESS_TYPE = 3;
    static private final int PDU_IPV6_ADDRESS_TYPE = 4;
    static private final int PDU_UNKNOWN_ADDRESS_TYPE = 5;

    /**
     * Address regular expression string.
     */
    static final String REGEXP_PHONE_NUMBER_ADDRESS_TYPE = "\\+?[0-9|\\.|\\-]+";
    static final String REGEXP_EMAIL_ADDRESS_TYPE = "[a-zA-Z| ]*\\<{0,1}[a-zA-Z| ]+@{1}" +
            "[a-zA-Z| ]+\\.{1}[a-zA-Z| ]+\\>{0,1}";
    static final String REGEXP_IPV6_ADDRESS_TYPE =
        "[a-fA-F]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}" +
        "[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}" +
        "[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}";
    static final String REGEXP_IPV4_ADDRESS_TYPE = "[0-9]{1,3}\\.{1}[0-9]{1,3}\\.{1}" +
            "[0-9]{1,3}\\.{1}[0-9]{1,3}";

    /**
     * The postfix strings of address.
     */
    static final String STRING_PHONE_NUMBER_ADDRESS_TYPE = "/TYPE=PLMN";
    static final String STRING_IPV4_ADDRESS_TYPE = "/TYPE=IPV4";
    static final String STRING_IPV6_ADDRESS_TYPE = "/TYPE=IPV6";

    /**
     * Error values.
     */
    static private final int PDU_COMPOSE_SUCCESS = 0;
    static private final int PDU_COMPOSE_CONTENT_ERROR = 1;
    static private final int PDU_COMPOSE_FIELD_NOT_SET = 2;
    static private final int PDU_COMPOSE_FIELD_NOT_SUPPORTED = 3;

    /**
     * WAP values defined in WSP spec.
     */
    static private final int QUOTED_STRING_FLAG = 34;
    static private final int END_STRING_FLAG = 0;
    static private final int LENGTH_QUOTE = 31;
    static private final int TEXT_MAX = 127;
    static private final int SHORT_INTEGER_MAX = 127;
    static private final int LONG_INTEGER_LENGTH_MAX = 8;

    /**
     * Block size when read data from InputStream.
     */
    static private final int PDU_COMPOSER_BLOCK_SIZE = 1024;

    /**
     * The output message.
     */
    protected ByteArrayOutputStream mMessage = null;

    /**
     * The PDU.
     */
    private GenericPdu mPdu = null;

    /**
     * Current visiting position of the mMessage.
     */
    protected int mPosition = 0;

    /**
     * Message compose buffer stack.
     */
    private BufferStack mStack = null;

    /**
     * Content resolver.
     */
    private final ContentResolver mResolver;

    /**
     * Header of this pdu.
     */
    private PduHeaders mPduHeader = null;

    /**
     * Map of all content type
     */
    private static HashMap<String, Integer> mContentTypeMap = null;

    static {
        mContentTypeMap = new HashMap<String, Integer>();

        int i;
        for (i = 0; i < PduContentTypes.contentTypes.length; i++) {
            mContentTypeMap.put(PduContentTypes.contentTypes[i], i);
        }
    }

    /**
     * Constructor.
     *
     * @param context the context
     * @param pdu the pdu to be composed
     */
    public PduComposer(Context context, GenericPdu pdu) {
        mPdu = pdu;
        mResolver = context.getContentResolver();
        mPduHeader = pdu.getPduHeaders();
        mStack = new BufferStack();
        mMessage = new ByteArrayOutputStream();
        mPosition = 0;
    }

    /**
     * Make the message. No need to check whether mandatory fields are set,
     * because the constructors of outgoing pdus are taking care of this.
     *
     * @return OutputStream of maked message. Return null if
     *         the PDU is invalid.
     */
    public byte[] make() {
        // Get Message-type.
        int type = mPdu.getMessageType();

        /* make the message */
        switch (type) {
            case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                if (makeSendReqPdu() != PDU_COMPOSE_SUCCESS) {
                    return null;
                }
                break;
            case PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND:
                if (makeNotifyResp() != PDU_COMPOSE_SUCCESS) {
                    return null;
                }
                break;
            case PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND:
                if (makeAckInd() != PDU_COMPOSE_SUCCESS) {
                    return null;
                }
                break;
            case PduHeaders.MESSAGE_TYPE_READ_REC_IND:
                if (makeReadRecInd() != PDU_COMPOSE_SUCCESS) {
                    return null;
                }
                break;
            default:
                return null;
        }

        Log.w("PduComposer", "Returning: " + mMessage.size() + " bytes...");

        return mMessage.toByteArray();
    }

    /**
     *  Copy buf to mMessage.
     */
    protected void arraycopy(byte[] buf, int pos, int length) {
        mMessage.write(buf, pos, length);
        mPosition = mPosition + length;
    }

    /**
     * Append a byte to mMessage.
     */
    protected void append(int value) {
        mMessage.write(value);
        mPosition ++;
    }

    /**
     * Append short integer value to mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendShortInteger(int value) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Short-integer = OCTET
         * ; Integers in range 0-127 shall be encoded as a one octet value
         * ; with the most significant bit set to one (1xxx xxxx) and with
         * ; the value in the remaining least significant bits.
         * In our implementation, only low 7 bits are stored and otherwise
         * bits are ignored.
         */
        append((value | 0x80) & 0xff);
    }

    /**
     * Append an octet number between 128 and 255 into mMessage.
     * NOTE:
     * A value between 0 and 127 should be appended by using appendShortInteger.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendOctet(int number) {
        append(number);
    }

    /**
     * Append a short length into mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendShortLength(int value) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Short-length = <Any octet 0-30>
         */
        append(value);
    }

    /**
     * Append long integer into mMessage. it's used for really long integers.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendLongInteger(long longInt) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Long-integer = Short-length Multi-octet-integer
         * ; The Short-length indicates the length of the Multi-octet-integer
         * Multi-octet-integer = 1*30 OCTET
         * ; The content octets shall be an unsigned integer value with the
         * ; most significant octet encoded first (big-endian representation).
         * ; The minimum number of octets must be used to encode the value.
         */
        int size;
        long temp = longInt;

        // Count the length of the long integer.
        for(size = 0; (temp != 0) && (size < LONG_INTEGER_LENGTH_MAX); size++) {
            temp = (temp >>> 8);
        }

        // Set Length.
        appendShortLength(size);

        // Count and set the long integer.
        int i;
        int shift = (size -1) * 8;

        for (i = 0; i < size; i++) {
            append((int)((longInt >>> shift) & 0xff));
            shift = shift - 8;
        }
    }

    /**
     * Append text string into mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendTextString(byte[] text) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Text-string = [Quote] *TEXT End-of-string
         * ; If the first character in the TEXT is in the range of 128-255,
         * ; a Quote character must precede it. Otherwise the Quote character
         * ;must be omitted. The Quote is not part of the contents.
         */
        if (((text[0])&0xff) > TEXT_MAX) { // No need to check for <= 255
            append(TEXT_MAX);
        }

        arraycopy(text, 0, text.length);
        append(0);
    }

    /**
     * Append text string into mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendTextString(String str) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Text-string = [Quote] *TEXT End-of-string
         * ; If the first character in the TEXT is in the range of 128-255,
         * ; a Quote character must precede it. Otherwise the Quote character
         * ;must be omitted. The Quote is not part of the contents.
         */
        appendTextString(str.getBytes());
    }

    /**
     * Append encoded string value to mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendEncodedString(EncodedStringValue enStr) {
        /*
         * From OMA-TS-MMS-ENC-V1_3-20050927-C:
         * Encoded-string-value = Text-string | Value-length Char-set Text-string
         */
        assert(enStr != null);

        int charset = enStr.getCharacterSet();
        byte[] textString = enStr.getTextString();
        if (null == textString) {
            return;
        }

        /*
         * In the implementation of EncodedStringValue, the charset field will
         * never be 0. It will always be composed as
         * Encoded-string-value = Value-length Char-set Text-string
         */
        mStack.newbuf();
        PositionMarker start = mStack.mark();

        appendShortInteger(charset);
        appendTextString(textString);

        int len = start.getLength();
        mStack.pop();
        appendValueLength(len);
        mStack.copy();
    }

    /**
     * Append uintvar integer into mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendUintvarInteger(long value) {
        /*
         * From WAP-230-WSP-20010705-a:
         * To encode a large unsigned integer, split it into 7-bit fragments
         * and place them in the payloads of multiple octets. The most significant
         * bits are placed in the first octets with the least significant bits
         * ending up in the last octet. All octets MUST set the Continue bit to 1
         * except the last octet, which MUST set the Continue bit to 0.
         */
        int i;
        long max = SHORT_INTEGER_MAX;

        for (i = 0; i < 5; i++) {
            if (value < max) {
                break;
            }

            max = (max << 7) | 0x7fl;
        }

        while(i > 0) {
            long temp = value >>> (i * 7);
            temp = temp & 0x7f;

            append((int)((temp | 0x80) & 0xff));

            i--;
        }

        append((int)(value & 0x7f));
    }

    /**
     * Append date value into mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendDateValue(long date) {
        /*
         * From OMA-TS-MMS-ENC-V1_3-20050927-C:
         * Date-value = Long-integer
         */
        appendLongInteger(date);
    }

    /**
     * Append value length to mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendValueLength(long value) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Value-length = Short-length | (Length-quote Length)
         * ; Value length is used to indicate the length of the value to follow
         * Short-length = <Any octet 0-30>
         * Length-quote = <Octet 31>
         * Length = Uintvar-integer
         */
        if (value < LENGTH_QUOTE) {
            appendShortLength((int) value);
            return;
        }

        append(LENGTH_QUOTE);
        appendUintvarInteger(value);
    }

    /**
     * Append quoted string to mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendQuotedString(byte[] text) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Quoted-string = <Octet 34> *TEXT End-of-string
         * ;The TEXT encodes an RFC2616 Quoted-string with the enclosing
         * ;quotation-marks <"> removed.
         */
        append(QUOTED_STRING_FLAG);
        arraycopy(text, 0, text.length);
        append(END_STRING_FLAG);
    }

    /**
     * Append quoted string to mMessage.
     * This implementation doesn't check the validity of parameter, since it
     * assumes that the values are validated in the GenericPdu setter methods.
     */
    protected void appendQuotedString(String str) {
        /*
         * From WAP-230-WSP-20010705-a:
         * Quoted-string = <Octet 34> *TEXT End-of-string
         * ;The TEXT encodes an RFC2616 Quoted-string with the enclosing
         * ;quotation-marks <"> removed.
         */
        appendQuotedString(str.getBytes());
    }

    private EncodedStringValue appendAddressType(EncodedStringValue address) {
        EncodedStringValue temp = null;

        try {
            int addressType = checkAddressType(address.getString());
            temp = EncodedStringValue.copy(address);
            if (PDU_PHONE_NUMBER_ADDRESS_TYPE == addressType) {
                // Phone number.
                temp.appendTextString(STRING_PHONE_NUMBER_ADDRESS_TYPE.getBytes());
            } else if (PDU_IPV4_ADDRESS_TYPE == addressType) {
                // Ipv4 address.
                temp.appendTextString(STRING_IPV4_ADDRESS_TYPE.getBytes());
            } else if (PDU_IPV6_ADDRESS_TYPE == addressType) {
                // Ipv6 address.
                temp.appendTextString(STRING_IPV6_ADDRESS_TYPE.getBytes());
            }
        } catch (NullPointerException e) {
            return null;
        }

        return temp;
    }

    /**
     * Append header to mMessage.
     */
    private int appendHeader(int field) {
        switch (field) {
            case PduHeaders.MMS_VERSION:
                appendOctet(field);

                int version = mPduHeader.getOctet(field);
                if (0 == version) {
                    appendShortInteger(PduHeaders.CURRENT_MMS_VERSION);
                } else {
                    appendShortInteger(version);
                }

                break;

            case PduHeaders.MESSAGE_ID:
            case PduHeaders.TRANSACTION_ID:
                byte[] textString = mPduHeader.getTextString(field);
                if (null == textString) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                appendOctet(field);
                appendTextString(textString);
                break;

            case PduHeaders.TO:
            case PduHeaders.BCC:
            case PduHeaders.CC:
                EncodedStringValue[] addr = mPduHeader.getEncodedStringValues(field);

                if (null == addr) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                EncodedStringValue temp;
                for (int i = 0; i < addr.length; i++) {
                    temp = appendAddressType(addr[i]);
                    if (temp == null) {
                        return PDU_COMPOSE_CONTENT_ERROR;
                    }

                    appendOctet(field);
                    appendEncodedString(temp);
                }
                break;

            case PduHeaders.FROM:
                // Value-length (Address-present-token Encoded-string-value | Insert-address-token)
                appendOctet(field);

                EncodedStringValue from = mPduHeader.getEncodedStringValue(field);
                if ((from == null)
                        || TextUtils.isEmpty(from.getString())
                        || new String(from.getTextString()).equals(
                                PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)) {
                    // Length of from = 1
                    append(1);
                    // Insert-address-token = <Octet 129>
                    append(PduHeaders.FROM_INSERT_ADDRESS_TOKEN);
                } else {
                    mStack.newbuf();
                    PositionMarker fstart = mStack.mark();

                    // Address-present-token = <Octet 128>
                    append(PduHeaders.FROM_ADDRESS_PRESENT_TOKEN);

                    temp = appendAddressType(from);
                    if (temp == null) {
                        return PDU_COMPOSE_CONTENT_ERROR;
                    }

                    appendEncodedString(temp);

                    int flen = fstart.getLength();
                    mStack.pop();
                    appendValueLength(flen);
                    mStack.copy();
                }
                break;

            case PduHeaders.READ_STATUS:
            case PduHeaders.STATUS:
            case PduHeaders.REPORT_ALLOWED:
            case PduHeaders.PRIORITY:
            case PduHeaders.DELIVERY_REPORT:
            case PduHeaders.READ_REPORT:
                int octet = mPduHeader.getOctet(field);
                if (0 == octet) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                appendOctet(field);
                appendOctet(octet);
                break;

            case PduHeaders.DATE:
                long date = mPduHeader.getLongInteger(field);
                if (-1 == date) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                appendOctet(field);
                appendDateValue(date);
                break;

            case PduHeaders.SUBJECT:
                EncodedStringValue enString =
                    mPduHeader.getEncodedStringValue(field);
                if (null == enString) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                appendOctet(field);
                appendEncodedString(enString);
                break;

            case PduHeaders.MESSAGE_CLASS:
                byte[] messageClass = mPduHeader.getTextString(field);
                if (null == messageClass) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                appendOctet(field);
                if (Arrays.equals(messageClass,
                        PduHeaders.MESSAGE_CLASS_ADVERTISEMENT_STR.getBytes())) {
                    appendOctet(PduHeaders.MESSAGE_CLASS_ADVERTISEMENT);
                } else if (Arrays.equals(messageClass,
                        PduHeaders.MESSAGE_CLASS_AUTO_STR.getBytes())) {
                    appendOctet(PduHeaders.MESSAGE_CLASS_AUTO);
                } else if (Arrays.equals(messageClass,
                        PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes())) {
                    appendOctet(PduHeaders.MESSAGE_CLASS_PERSONAL);
                } else if (Arrays.equals(messageClass,
                        PduHeaders.MESSAGE_CLASS_INFORMATIONAL_STR.getBytes())) {
                    appendOctet(PduHeaders.MESSAGE_CLASS_INFORMATIONAL);
                } else {
                    appendTextString(messageClass);
                }
                break;

            case PduHeaders.EXPIRY:
                long expiry = mPduHeader.getLongInteger(field);
                if (-1 == expiry) {
                    return PDU_COMPOSE_FIELD_NOT_SET;
                }

                appendOctet(field);

                mStack.newbuf();
                PositionMarker expiryStart = mStack.mark();

                append(PduHeaders.VALUE_RELATIVE_TOKEN);
                appendLongInteger(expiry);

                int expiryLength = expiryStart.getLength();
                mStack.pop();
                appendValueLength(expiryLength);
                mStack.copy();
                break;

            default:
                return PDU_COMPOSE_FIELD_NOT_SUPPORTED;
        }

        return PDU_COMPOSE_SUCCESS;
    }

    /**
     * Make ReadRec.Ind.
     */
    private int makeReadRecInd() {
        if (mMessage == null) {
            mMessage = new ByteArrayOutputStream();
            mPosition = 0;
        }

        // X-Mms-Message-Type
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_READ_REC_IND);

        // X-Mms-MMS-Version
        if (appendHeader(PduHeaders.MMS_VERSION) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // Message-ID
        if (appendHeader(PduHeaders.MESSAGE_ID) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // To
        if (appendHeader(PduHeaders.TO) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // From
        if (appendHeader(PduHeaders.FROM) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // Date Optional
        appendHeader(PduHeaders.DATE);

        // X-Mms-Read-Status
        if (appendHeader(PduHeaders.READ_STATUS) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // X-Mms-Applic-ID Optional(not support)
        // X-Mms-Reply-Applic-ID Optional(not support)
        // X-Mms-Aux-Applic-Info Optional(not support)

        return PDU_COMPOSE_SUCCESS;
    }

    /**
     * Make NotifyResp.Ind.
     */
    private int makeNotifyResp() {
        if (mMessage == null) {
            mMessage = new ByteArrayOutputStream();
            mPosition = 0;
        }

        //    X-Mms-Message-Type
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND);

        // X-Mms-Transaction-ID
        if (appendHeader(PduHeaders.TRANSACTION_ID) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // X-Mms-MMS-Version
        if (appendHeader(PduHeaders.MMS_VERSION) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        //  X-Mms-Status
        if (appendHeader(PduHeaders.STATUS) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // X-Mms-Report-Allowed Optional (not support)
        return PDU_COMPOSE_SUCCESS;
    }

    /**
     * Make Acknowledge.Ind.
     */
    private int makeAckInd() {
        if (mMessage == null) {
            mMessage = new ByteArrayOutputStream();
            mPosition = 0;
        }

        //    X-Mms-Message-Type
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND);

        // X-Mms-Transaction-ID
        if (appendHeader(PduHeaders.TRANSACTION_ID) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        //     X-Mms-MMS-Version
        if (appendHeader(PduHeaders.MMS_VERSION) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // X-Mms-Report-Allowed Optional
        appendHeader(PduHeaders.REPORT_ALLOWED);

        return PDU_COMPOSE_SUCCESS;
    }

    /**
     * Make Send.req.
     */
    private int makeSendReqPdu() {
    	Log.w("PduComposer", "Making send request...");

        if (mMessage == null) {
            mMessage = new ByteArrayOutputStream();
            mPosition = 0;
        }

        // X-Mms-Message-Type
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_SEND_REQ);

        // X-Mms-Transaction-ID
        appendOctet(PduHeaders.TRANSACTION_ID);

        byte[] trid = mPduHeader.getTextString(PduHeaders.TRANSACTION_ID);
        if (trid == null) {
            // Transaction-ID should be set(by Transaction) before make().
            throw new IllegalArgumentException("Transaction-ID is null.");
        }
        appendTextString(trid);

        //  X-Mms-MMS-Version
        if (appendHeader(PduHeaders.MMS_VERSION) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // Date Date-value Optional.
        appendHeader(PduHeaders.DATE);

        // From
        if (appendHeader(PduHeaders.FROM) != PDU_COMPOSE_SUCCESS) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        boolean recipient = false;

        // To
        if (appendHeader(PduHeaders.TO) != PDU_COMPOSE_CONTENT_ERROR) {
            recipient = true;
        }

        // Cc
        if (appendHeader(PduHeaders.CC) != PDU_COMPOSE_CONTENT_ERROR) {
            recipient = true;
        }

        // Bcc
        if (appendHeader(PduHeaders.BCC) != PDU_COMPOSE_CONTENT_ERROR) {
            recipient = true;
        }

        // Need at least one of "cc", "bcc" and "to".
        if (false == recipient) {
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        // Subject Optional
        appendHeader(PduHeaders.SUBJECT);

        // X-Mms-Message-Class Optional
        // Message-class-value = Class-identifier | Token-text
        appendHeader(PduHeaders.MESSAGE_CLASS);

        // X-Mms-Expiry Optional
        appendHeader(PduHeaders.EXPIRY);

        // X-Mms-Priority Optional
        appendHeader(PduHeaders.PRIORITY);

        // X-Mms-Delivery-Report Optional
        appendHeader(PduHeaders.DELIVERY_REPORT);

        // X-Mms-Read-Report Optional
        appendHeader(PduHeaders.READ_REPORT);

        //    Content-Type
        appendOctet(PduHeaders.CONTENT_TYPE);

        //  Message body
        return makeMessageBody();
    }

    /**
     * Make message body.
     */
    private int makeMessageBody() {
    	Log.w("PduComposer", "Making message body...");
        // 1. add body informations
        mStack.newbuf();  // Switching buffer because we need to

        PositionMarker ctStart = mStack.mark();

        // This contentTypeIdentifier should be used for type of attachment...
        String contentType = new String(mPduHeader.getTextString(PduHeaders.CONTENT_TYPE));
        Integer contentTypeIdentifier = mContentTypeMap.get(contentType);
        if (contentTypeIdentifier == null) {
            // content type is mandatory
            return PDU_COMPOSE_CONTENT_ERROR;
        }

        appendShortInteger(contentTypeIdentifier.intValue());

        // content-type parameter: start
        PduBody body = ((SendReq) mPdu).getBody();
        if (null == body || body.getPartsNum() == 0) {
            // empty message
            appendUintvarInteger(0);
            mStack.pop();
            mStack.copy();
            return PDU_COMPOSE_SUCCESS;
        }

        PduPart part;
        try {
            part = body.getPart(0);

            byte[] start = part.getContentId();
            if (start != null) {
                appendOctet(PduPart.P_DEP_START);
                if (('<' == start[0]) && ('>' == start[start.length - 1])) {
                    appendTextString(start);
                } else {
                    appendTextString("<" + new String(start) + ">");
                }
            }

            // content-type parameter: type
            appendOctet(PduPart.P_CT_MR_TYPE);
            appendTextString(part.getContentType());
        }
        catch (ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
        }

        int ctLength = ctStart.getLength();
        mStack.pop();
        appendValueLength(ctLength);
        mStack.copy();

        // 3. add content
        int partNum = body.getPartsNum();
        appendUintvarInteger(partNum);
        for (int i = 0; i < partNum; i++) {
            part = body.getPart(i);
            mStack.newbuf();  // Leaving space for header length and data length
            PositionMarker attachment = mStack.mark();

            mStack.newbuf();  // Leaving space for Content-Type length
            PositionMarker contentTypeBegin = mStack.mark();

            byte[] partContentType = part.getContentType();

            if (partContentType == null) {
                // content type is mandatory
                return PDU_COMPOSE_CONTENT_ERROR;
            }

            // content-type value
            Integer partContentTypeIdentifier =
                mContentTypeMap.get(new String(partContentType));
            if (partContentTypeIdentifier == null) {
                appendTextString(partContentType);
            } else {
                appendShortInteger(partContentTypeIdentifier.intValue());
            }

            /* Content-type parameter : name.
             * The value of name, filename, content-location is the same.
             * Just one of them is enough for this PDU.
             */
            byte[] name = part.getName();

            if (null == name) {
                name = part.getFilename();

                if (null == name) {
                    name = part.getContentLocation();

                    if (null == name) {
                        /* at lease one of name, filename, Content-location
                         * should be available.
                         */
                        return PDU_COMPOSE_CONTENT_ERROR;
                    }
                }
            }
            appendOctet(PduPart.P_DEP_NAME);
            appendTextString(name);

            // content-type parameter : charset
            int charset = part.getCharset();
            if (charset != 0) {
                appendOctet(PduPart.P_CHARSET);
                appendShortInteger(charset);
            }

            int contentTypeLength = contentTypeBegin.getLength();
            mStack.pop();
            appendValueLength(contentTypeLength);
            mStack.copy();

            // content id
            byte[] contentId = part.getContentId();

            if (null != contentId) {
                appendOctet(PduPart.P_CONTENT_ID);
                if (('<' == contentId[0]) && ('>' == contentId[contentId.length - 1])) {
                    appendQuotedString(contentId);
                } else {
                    appendQuotedString("<" + new String(contentId) + ">");
                }
            }

            // content-location
            byte[] contentLocation = part.getContentLocation();
            if (null != contentLocation) {
            	appendOctet(PduPart.P_CONTENT_LOCATION);
            	appendTextString(contentLocation);
            }

            // content
            int headerLength = attachment.getLength();

            int dataLength = 0; // Just for safety...
            byte[] partData = part.getData();

            if (partData != null) {
                arraycopy(partData, 0, partData.length);
                dataLength = partData.length;
            } else {
                InputStream cr;
                try {
                    byte[] buffer = new byte[PDU_COMPOSER_BLOCK_SIZE];
                    cr = mResolver.openInputStream(part.getDataUri());
                    int len = 0;
                    while ((len = cr.read(buffer)) != -1) {
                        mMessage.write(buffer, 0, len);
                        mPosition += len;
                        dataLength += len;
                    }
                } catch (FileNotFoundException e) {
                    return PDU_COMPOSE_CONTENT_ERROR;
                } catch (IOException e) {
                    return PDU_COMPOSE_CONTENT_ERROR;
                } catch (RuntimeException e) {
                    return PDU_COMPOSE_CONTENT_ERROR;
                }
            }

            if (dataLength != (attachment.getLength() - headerLength)) {
                throw new RuntimeException("BUG: Length sanity check failed");
            }

            mStack.pop();
            appendUintvarInteger(headerLength);
            appendUintvarInteger(dataLength);
            mStack.copy();
        }

        return PDU_COMPOSE_SUCCESS;
    }

    /**
     *  Record current message informations.
     */
    static private class LengthRecordNode {
        ByteArrayOutputStream currentMessage = null;
        public int currentPosition = 0;

        public LengthRecordNode next = null;
    }

    /**
     * Mark current message position and stact size.
     */
    private class PositionMarker {
        private int c_pos;   // Current position
        private int currentStackSize;  // Current stack size

        int getLength() {
            // If these assert fails, likely that you are finding the
            // size of buffer that is deep in BufferStack you can only
            // find the length of the buffer that is on top
            if (currentStackSize != mStack.stackSize) {
                throw new RuntimeException("BUG: Invalid call to getLength()");
            }

            return mPosition - c_pos;
        }
    }

    /**
     * This implementation can be OPTIMIZED to use only
     * 2 buffers. This optimization involves changing BufferStack
     * only... Its usage (interface) will not change.
     */
    private class BufferStack {
        private LengthRecordNode stack = null;
        private LengthRecordNode toCopy = null;

        int stackSize = 0;

        /**
         *  Create a new message buffer and push it into the stack.
         */
        void newbuf() {
            // You can't create a new buff when toCopy != null
            // That is after calling pop() and before calling copy()
            // If you do, it is a bug
            if (toCopy != null) {
                throw new RuntimeException("BUG: Invalid newbuf() before copy()");
            }

            LengthRecordNode temp = new LengthRecordNode();

            temp.currentMessage = mMessage;
            temp.currentPosition = mPosition;

            temp.next = stack;
            stack = temp;

            stackSize = stackSize + 1;

            mMessage = new ByteArrayOutputStream();
            mPosition = 0;
        }

        /**
         *  Pop the message before and record current message in the stack.
         */
        void pop() {
            ByteArrayOutputStream currentMessage = mMessage;
            int currentPosition = mPosition;

            mMessage = stack.currentMessage;
            mPosition = stack.currentPosition;

            toCopy = stack;
            // Re using the top element of the stack to avoid memory allocation

            stack = stack.next;
            stackSize = stackSize - 1;

            toCopy.currentMessage = currentMessage;
            toCopy.currentPosition = currentPosition;
        }

        /**
         *  Append current message to the message before.
         */
        void copy() {
            arraycopy(toCopy.currentMessage.toByteArray(), 0,
                    toCopy.currentPosition);

            toCopy = null;
        }

        /**
         *  Mark current message position
         */
        PositionMarker mark() {
            PositionMarker m = new PositionMarker();

            m.c_pos = mPosition;
            m.currentStackSize = stackSize;

            return m;
        }
    }

    /**
     * Check address type.
     *
     * @param address address string without the postfix stinng type,
     *        such as "/TYPE=PLMN", "/TYPE=IPv6" and "/TYPE=IPv4"
     * @return PDU_PHONE_NUMBER_ADDRESS_TYPE if it is phone number,
     *         PDU_EMAIL_ADDRESS_TYPE if it is email address,
     *         PDU_IPV4_ADDRESS_TYPE if it is ipv4 address,
     *         PDU_IPV6_ADDRESS_TYPE if it is ipv6 address,
     *         PDU_UNKNOWN_ADDRESS_TYPE if it is unknown.
     */
    protected static int checkAddressType(String address) {
        /**
         * From OMA-TS-MMS-ENC-V1_3-20050927-C.pdf, section 8.
         * address = ( e-mail / device-address / alphanum-shortcode / num-shortcode)
         * e-mail = mailbox; to the definition of mailbox as described in
         * section 3.4 of [RFC2822], but excluding the
         * obsolete definitions as indicated by the "obs-" prefix.
         * device-address = ( global-phone-number "/TYPE=PLMN" )
         * / ( ipv4 "/TYPE=IPv4" ) / ( ipv6 "/TYPE=IPv6" )
         * / ( escaped-value "/TYPE=" address-type )
         *
         * global-phone-number = ["+"] 1*( DIGIT / written-sep )
         * written-sep =("-"/".")
         *
         * ipv4 = 1*3DIGIT 3( "." 1*3DIGIT ) ; IPv4 address value
         *
         * ipv6 = 4HEXDIG 7( ":" 4HEXDIG ) ; IPv6 address per RFC 2373
         */

        if (null == address) {
            return PDU_UNKNOWN_ADDRESS_TYPE;
        }

        if (address.matches(REGEXP_IPV4_ADDRESS_TYPE)) {
            // Ipv4 address.
            return PDU_IPV4_ADDRESS_TYPE;
        }else if (address.matches(REGEXP_PHONE_NUMBER_ADDRESS_TYPE)) {
            // Phone number.
            return PDU_PHONE_NUMBER_ADDRESS_TYPE;
        } else if (address.matches(REGEXP_EMAIL_ADDRESS_TYPE)) {
            // Email address.
            return PDU_EMAIL_ADDRESS_TYPE;
        } else if (address.matches(REGEXP_IPV6_ADDRESS_TYPE)) {
            // Ipv6 address.
            return PDU_IPV6_ADDRESS_TYPE;
        } else {
            // Unknown address.
            return PDU_UNKNOWN_ADDRESS_TYPE;
        }
    }
}
