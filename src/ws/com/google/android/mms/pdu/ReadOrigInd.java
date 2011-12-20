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

import ws.com.google.android.mms.InvalidHeaderValueException;

public class ReadOrigInd extends GenericPdu {
    /**
     * Empty constructor.
     * Since the Pdu corresponding to this class is constructed
     * by the Proxy-Relay server, this class is only instantiated
     * by the Pdu Parser.
     *
     * @throws InvalidHeaderValueException if error occurs.
     */
    public ReadOrigInd() throws InvalidHeaderValueException {
        super();
        setMessageType(PduHeaders.MESSAGE_TYPE_READ_ORIG_IND);
    }

    /**
     * Constructor with given headers.
     *
     * @param headers Headers for this PDU.
     */
    ReadOrigInd(PduHeaders headers) {
        super(headers);
    }

    /**
     * Get Date value.
     *
     * @return the value
     */
    public long getDate() {
        return mPduHeaders.getLongInteger(PduHeaders.DATE);
    }

    /**
     * Set Date value.
     *
     * @param value the value
     */
    public void setDate(long value) {
        mPduHeaders.setLongInteger(value, PduHeaders.DATE);
    }

    /**
     * Get From value.
     * From-value = Value-length
     *      (Address-present-token Encoded-string-value | Insert-address-token)
     *
     * @return the value
     */
    public EncodedStringValue getFrom() {
       return mPduHeaders.getEncodedStringValue(PduHeaders.FROM);
    }

    /**
     * Set From value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setFrom(EncodedStringValue value) {
        mPduHeaders.setEncodedStringValue(value, PduHeaders.FROM);
    }

    /**
     * Get Message-ID value.
     *
     * @return the value
     */
    public byte[] getMessageId() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_ID);
    }

    /**
     * Set Message-ID value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setMessageId(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.MESSAGE_ID);
    }

    /**
     * Get X-MMS-Read-status value.
     *
     * @return the value
     */
    public int getReadStatus() {
        return mPduHeaders.getOctet(PduHeaders.READ_STATUS);
    }

    /**
     * Set X-MMS-Read-status value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     */
    public void setReadStatus(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.READ_STATUS);
    }

    /**
     * Get To value.
     *
     * @return the value
     */
    public EncodedStringValue[] getTo() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.TO);
    }

    /**
     * Set To value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setTo(EncodedStringValue[] value) {
        mPduHeaders.setEncodedStringValues(value, PduHeaders.TO);
    }

    /*
     * Optional, not supported header fields:
     *
     *     public byte[] getApplicId() {return null;}
     *     public void setApplicId(byte[] value) {}
     *
     *     public byte[] getAuxApplicId() {return null;}
     *     public void getAuxApplicId(byte[] value) {}
     *
     *     public byte[] getReplyApplicId() {return 0x00;}
     *     public void setReplyApplicId(byte[] value) {}
     */
}
