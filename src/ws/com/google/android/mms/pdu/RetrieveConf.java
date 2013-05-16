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

/**
 * M-Retrive.conf Pdu.
 */
public class RetrieveConf extends MultimediaMessagePdu {
    /**
     * Empty constructor.
     * Since the Pdu corresponding to this class is constructed
     * by the Proxy-Relay server, this class is only instantiated
     * by the Pdu Parser.
     *
     * @throws InvalidHeaderValueException if error occurs.
     */
    public RetrieveConf() throws InvalidHeaderValueException {
        super();
        setMessageType(PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
    }

    /**
     * Constructor with given headers.
     *
     * @param headers Headers for this PDU.
     */
    RetrieveConf(PduHeaders headers) {
        super(headers);
    }

    /**
     * Constructor with given headers and body
     *
     * @param headers Headers for this PDU.
     * @param body Body of this PDu.
     */
    public RetrieveConf(PduHeaders headers, PduBody body) {
        super(headers, body);
    }

    /**
     * Get CC value.
     *
     * @return the value
     */
    public EncodedStringValue[] getCc() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.CC);
    }

    /**
     * Add a "CC" value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void addCc(EncodedStringValue value) {
        mPduHeaders.appendEncodedStringValue(value, PduHeaders.CC);
    }

    /**
     * Get Content-type value.
     *
     * @return the value
     */
    public byte[] getContentType() {
        return mPduHeaders.getTextString(PduHeaders.CONTENT_TYPE);
    }

    /**
     * Set Content-type value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setContentType(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.CONTENT_TYPE);
    }

    /**
     * Get X-Mms-Delivery-Report value.
     *
     * @return the value
     */
    public int getDeliveryReport() {
        return mPduHeaders.getOctet(PduHeaders.DELIVERY_REPORT);
    }

    /**
     * Set X-Mms-Delivery-Report value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     */
    public void setDeliveryReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.DELIVERY_REPORT);
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
     * Get X-Mms-Message-Class value.
     * Message-class-value = Class-identifier | Token-text
     * Class-identifier = Personal | Advertisement | Informational | Auto
     *
     * @return the value
     */
    public byte[] getMessageClass() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_CLASS);
    }

    /**
     * Set X-Mms-Message-Class value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setMessageClass(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.MESSAGE_CLASS);
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
     * Get X-Mms-Read-Report value.
     *
     * @return the value
     */
    public int getReadReport() {
        return mPduHeaders.getOctet(PduHeaders.READ_REPORT);
    }

    /**
     * Set X-Mms-Read-Report value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     */
    public void setReadReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.READ_REPORT);
    }

    /**
     * Get X-Mms-Retrieve-Status value.
     *
     * @return the value
     */
    public int getRetrieveStatus() {
        return mPduHeaders.getOctet(PduHeaders.RETRIEVE_STATUS);
    }

    /**
     * Set X-Mms-Retrieve-Status value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     */
    public void setRetrieveStatus(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.RETRIEVE_STATUS);
    }

    /**
     * Get X-Mms-Retrieve-Text value.
     *
     * @return the value
     */
    public EncodedStringValue getRetrieveText() {
        return mPduHeaders.getEncodedStringValue(PduHeaders.RETRIEVE_TEXT);
    }

    /**
     * Set X-Mms-Retrieve-Text value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setRetrieveText(EncodedStringValue value) {
        mPduHeaders.setEncodedStringValue(value, PduHeaders.RETRIEVE_TEXT);
    }

    /**
     * Get X-Mms-Transaction-Id.
     *
     * @return the value
     */
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }

    /**
     * Set X-Mms-Transaction-Id.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setTransactionId(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
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
     *     public byte getContentClass() {return 0x00;}
     *     public void setApplicId(byte value) {}
     *
     *     public byte getDrmContent() {return 0x00;}
     *     public void setDrmContent(byte value) {}
     *
     *     public byte getDistributionIndicator() {return 0x00;}
     *     public void setDistributionIndicator(byte value) {}
     *
     *     public PreviouslySentByValue getPreviouslySentBy() {return null;}
     *     public void setPreviouslySentBy(PreviouslySentByValue value) {}
     *
     *     public PreviouslySentDateValue getPreviouslySentDate() {}
     *     public void setPreviouslySentDate(PreviouslySentDateValue value) {}
     *
     *     public MmFlagsValue getMmFlags() {return null;}
     *     public void setMmFlags(MmFlagsValue value) {}
     *
     *     public MmStateValue getMmState() {return null;}
     *     public void getMmState(MmStateValue value) {}
     *
     *     public byte[] getReplaceId() {return 0x00;}
     *     public void setReplaceId(byte[] value) {}
     *
     *     public byte[] getReplyApplicId() {return 0x00;}
     *     public void setReplyApplicId(byte[] value) {}
     *
     *     public byte getReplyCharging() {return 0x00;}
     *     public void setReplyCharging(byte value) {}
     *
     *     public byte getReplyChargingDeadline() {return 0x00;}
     *     public void setReplyChargingDeadline(byte value) {}
     *
     *     public byte[] getReplyChargingId() {return 0x00;}
     *     public void setReplyChargingId(byte[] value) {}
     *
     *     public long getReplyChargingSize() {return 0;}
     *     public void setReplyChargingSize(long value) {}
     */
}
