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

package org.thoughtcrime.securesms.dom.smil;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.smil.Time;

public class TimeImpl implements Time {
    static final int ALLOW_INDEFINITE_VALUE = (1 << 0);
    static final int ALLOW_OFFSET_VALUE     = (1 << 1);
    static final int ALLOW_SYNCBASE_VALUE   = (1 << 2);
    static final int ALLOW_SYNCTOPREV_VALUE = (1 << 3);
    static final int ALLOW_EVENT_VALUE      = (1 << 4);
    static final int ALLOW_MARKER_VALUE     = (1 << 5);
    static final int ALLOW_WALLCLOCK_VALUE  = (1 << 6);
    static final int ALLOW_NEGATIVE_VALUE   = (1 << 7);
    static final int ALLOW_ALL              = 0xFF;

    short mTimeType;
    boolean mResolved;
    double mResolvedOffset;

    /**
     * Creates a TimeImpl representation of a time-value represented as a String.
     * Time-values have the following syntax:
     * <p>
     * <pre>
     * Time-val ::= ( smil-1.0-syncbase-value
     *                          | "indefinite"
     *                          | offset-value
     *                          | syncbase-value
     *                          | syncToPrev-value
     *                          | event-value
     *                          | media-marker-value
     *                          | wallclock-sync-value )
     * Smil-1.0-syncbase-value ::=
     *          "id(" id-ref ")" ( "(" ( "begin" | "end" | clock-value ) ")" )?
     * Offset-value         ::= ( "+" | "-" )? clock-value
     * Syncbase-value       ::= ( id-ref "." ( "begin" | "end" ) ) ( ( "+" | "-" ) clock-value )?
     * SyncToPrev-value     ::= ( "prev.begin" | "prev.end" ) ( ( "+" | "-" ) clock-value )?
     * Event-value          ::= ( id-ref "." )? ( event-ref  ) ( ( "+" | "-" ) clock-value )?
     * Media-marker-value   ::= id-ref ".marker(" marker-name ")"
     * Wallclock-sync-value ::= "wallclock(" wallclock-value ")"
     * </pre>
     *
     * @param timeValue A String in the representation specified above
     * @param constraints Any combination of the #ALLOW_* flags
     * @return  A TimeImpl instance representing
     * @exception java.lang.IllegalArgumentException if the timeValue input
     *          parameter does not comply with the defined syntax
     * @exception java.lang.NullPointerException if the timekValue string is
     *          <code>null</code>
     */
    TimeImpl(String timeValue, int constraints) {
        /*
         * We do not support yet:
         *      - smil-1.0-syncbase-value
         *      - syncbase-value
         *      - syncToPrev-value
         *      - event-value
         *      - Media-marker-value
         *      - Wallclock-sync-value
         */
        // Will throw NullPointerException if timeValue is null
        if (timeValue.equals("indefinite")
                && ((constraints & ALLOW_INDEFINITE_VALUE) != 0) ) {
            mTimeType = SMIL_TIME_INDEFINITE;
        } else if ((constraints & ALLOW_OFFSET_VALUE) != 0) {
            int sign = 1;
            if (timeValue.startsWith("+")) {
                timeValue = timeValue.substring(1);
            } else if (timeValue.startsWith("-")) {
                timeValue = timeValue.substring(1);
                sign = -1;
            }
            mResolvedOffset = sign*parseClockValue(timeValue)/1000.0;
            mResolved = true;
            mTimeType = SMIL_TIME_OFFSET;
        } else {
            throw new IllegalArgumentException("Unsupported time value");
        }
    }

    /**
     * Converts a String representation of a clock value into the float
     * representation used in this API.
     * <p>
     * Clock values have the following syntax:
     * </p>
     * <p>
     * <pre>
     * Clock-val         ::= ( Full-clock-val | Partial-clock-val | Timecount-val )
     * Full-clock-val    ::= Hours ":" Minutes ":" Seconds ("." Fraction)?
     * Partial-clock-val ::= Minutes ":" Seconds ("." Fraction)?
     * Timecount-val     ::= Timecount ("." Fraction)? (Metric)?
     * Metric            ::= "h" | "min" | "s" | "ms"
     * Hours             ::= DIGIT+; any positive number
     * Minutes           ::= 2DIGIT; range from 00 to 59
     * Seconds           ::= 2DIGIT; range from 00 to 59
     * Fraction          ::= DIGIT+
     * Timecount         ::= DIGIT+
     * 2DIGIT            ::= DIGIT DIGIT
     * DIGIT             ::= [0-9]
     * </pre>
     *
     * @param clockValue A String in the representation specified above
     * @return  A float value in milliseconds that matches the string
     *          representation given as the parameter
     * @exception java.lang.IllegalArgumentException if the clockValue input
     *          parameter does not comply with the defined syntax
     * @exception java.lang.NullPointerException if the clockValue string is
     *          <code>null</code>
     */
    public static float parseClockValue(String clockValue) {
        try {
            float result = 0;

            // Will throw NullPointerException if clockValue is null
            clockValue = clockValue.trim();

            // Handle first 'Timecount-val' cases with metric
            if (clockValue.endsWith("ms")) {
                result = parseFloat(clockValue, 2, true);
            } else if (clockValue.endsWith("s")) {
                result = 1000*parseFloat(clockValue, 1, true);
            } else if (clockValue.endsWith("min")) {
                result = 60000*parseFloat(clockValue, 3, true);
            } else if (clockValue.endsWith("h")) {
                result = 3600000*parseFloat(clockValue, 1, true);
            } else {
                // Handle Timecount-val without metric
                try {
                    return parseFloat(clockValue, 0, true) * 1000;
                } catch (NumberFormatException e) {
                    // Ignore
                }

                // Split in {[Hours], Minutes, Seconds}
                String[] timeValues = clockValue.split(":");

                // Read Hours if present and remember location of Minutes
                int indexOfMinutes;
                if (timeValues.length == 2) {
                    indexOfMinutes = 0;
                } else if (timeValues.length == 3) {
                    result = 3600000*(int)parseFloat(timeValues[0], 0, false);
                    indexOfMinutes = 1;
                } else {
                    throw new IllegalArgumentException();
                }

                // Read Minutes
                int minutes = (int)parseFloat(timeValues[indexOfMinutes], 0, false);
                if ((minutes >= 00) && (minutes <= 59)) {
                    result += 60000*minutes;
                } else {
                    throw new IllegalArgumentException();
                }

                // Read Seconds
                float seconds = parseFloat(timeValues[indexOfMinutes + 1], 0, true);
                if ((seconds >= 00) && (seconds < 60)) {
                    result += 60000*seconds;
                } else {
                    throw new IllegalArgumentException();
                }

            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Parse a value formatted as follows:
     * <p>
     * <pre>
     * Value    ::= Number ("." Decimal)? (Text)?
     * Number   ::= DIGIT+; any positive number
     * Decimal  ::= DIGIT+; any positive number
     * Text     ::= CHAR*;   any sequence of chars
     * DIGIT    ::= [0-9]
     * </pre>
     * @param value The Value to parse
     * @param ignoreLast The size of Text to ignore
     * @param parseDecimal Whether Decimal is expected
     * @return The float value without Text, rounded to 3 digits after '.'
     * @throws IllegalArgumentException if Decimal was not expected but encountered
     */
    private static float parseFloat(String value, int ignoreLast, boolean parseDecimal) {
        // Ignore last characters
        value = value.substring(0, value.length() - ignoreLast);

        float result;
        int indexOfComma = value.indexOf('.');
        if (indexOfComma != -1) {
            if (!parseDecimal) {
                throw new IllegalArgumentException("int value contains decimal");
            }
            // Ensure that there are at least 3 decimals
            value = value + "000";
            // Read value up to 3 decimals and cut the rest
            result = Float.parseFloat(value.substring(0, indexOfComma));
            result += Float.parseFloat(
                    value.substring(indexOfComma + 1, indexOfComma + 4))/1000;
        } else {
            result = Integer.parseInt(value);
        }

        return result;
    }

    /*
     * Time Interface
     */

    public boolean getBaseBegin() {
        // TODO Auto-generated method stub
        return false;
    }

    public Element getBaseElement() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getEvent() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getMarker() {
        // TODO Auto-generated method stub
        return null;
    }

    public double getOffset() {
        // TODO Auto-generated method stub
        return 0;
    }

    public boolean getResolved() {
        return mResolved;
    }

    public double getResolvedOffset() {
        return mResolvedOffset;
    }

    public short getTimeType() {
        return mTimeType;
    }

    public void setBaseBegin(boolean baseBegin) throws DOMException {
        // TODO Auto-generated method stub

    }

    public void setBaseElement(Element baseElement) throws DOMException {
        // TODO Auto-generated method stub

    }

    public void setEvent(String event) throws DOMException {
        // TODO Auto-generated method stub

    }

    public void setMarker(String marker) throws DOMException {
        // TODO Auto-generated method stub

    }

    public void setOffset(double offset) throws DOMException {
        // TODO Auto-generated method stub

    }
}
