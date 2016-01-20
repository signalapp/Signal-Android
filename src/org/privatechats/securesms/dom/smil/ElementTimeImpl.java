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

package org.privatechats.securesms.dom.smil;

import java.util.ArrayList;

import org.w3c.dom.DOMException;
import org.w3c.dom.smil.ElementTime;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.Time;
import org.w3c.dom.smil.TimeList;

import android.util.Log;

public abstract class ElementTimeImpl implements ElementTime {
    private static final String TAG = "ElementTimeImpl";

    private static final String FILL_REMOVE_ATTRIBUTE = "remove";
    private static final String FILL_FREEZE_ATTRIBUTE = "freeze";
    private static final String FILL_HOLD_ATTRIBUTE = "hold";
    private static final String FILL_TRANSITION_ATTRIBUTE = "transition";
    private static final String FILL_AUTO_ATTRIBUTE   = "auto";
    private static final String FILL_ATTRIBUTE_NAME   = "fill";
    private static final String FILLDEFAULT_ATTRIBUTE_NAME   = "fillDefault";

    final SMILElement mSmilElement;

    /*
     * Internal Interface
     */
    ElementTimeImpl(SMILElement element) {
        mSmilElement = element;
    }

    // Default implementation. Override if required.
    int getBeginConstraints() {
        return TimeImpl.ALLOW_ALL;
    }

    // Default implementation. Override if required
    int getEndConstraints() {
        return TimeImpl.ALLOW_ALL;
    }

    /**
     * To get the parent node on the ElementTime tree. It is in opposition to getTimeChildren.
     * @return the parent ElementTime. Returns <code>null</code> if there is no parent.
     */
    abstract ElementTime getParentElementTime();

    /*
     * ElementTime Interface
     */

    public TimeList getBegin() {
        String[] beginTimeStringList = mSmilElement.getAttribute("begin").split(";");

        // TODO: Check other constraints on parsed values, e.g., "single, non-negative offset values
        ArrayList<Time> beginTimeList = new ArrayList<Time>();
        // Initialize Time instances and add them to Vector
        for (int i = 0; i < beginTimeStringList.length; i++) {
            try {
                beginTimeList.add(new TimeImpl(beginTimeStringList[i], getBeginConstraints()));
            } catch (IllegalArgumentException e) {
                // Ignore badly formatted times
            }
        }
        if (beginTimeList.size() == 0) {
            /*
             * What is the right default value?
             *
             * In MMS SMIL, this method may be called either on an instance of:
             *
             * 1 - ElementSequentialTimeContainer (The SMILDocument)
             * 2 - ElementParallelTimeContainer (A Time-Child of the SMILDocument, which is a seq)
             * 3 - ElementTime (A SMILMediaElement).
             *
             * 1 - In the first case, the default start time is obviously 0.
             * 2 - In the second case, the specifications mentions that
             *      "For children of a sequence, the only legal value for begin is
             *      a (single) non-negative offset value. The default begin value is 0."
             * 3 - In the third case, the specification mentions that
             *      "The default value of begin for children of a par is 0."
             *
             * In short, if no value is specified, the default is always 0.
             */

            beginTimeList.add(new TimeImpl("0", TimeImpl.ALLOW_ALL));
        }
        return new TimeListImpl(beginTimeList);
    }

    public float getDur() {
        float dur = 0;
        try {
            String durString = mSmilElement.getAttribute("dur");
            if (durString != null) {
                dur = TimeImpl.parseClockValue(durString) / 1000f;
            }
        } catch (IllegalArgumentException e) {
            // Do nothing and return the minimum value
        }

        return dur;
    }

    public TimeList getEnd() {
        ArrayList<Time> endTimeList = new ArrayList<Time>();

        String[] endTimeStringList = mSmilElement.getAttribute("end").split(";");
        int len = endTimeStringList.length;
        if (!((len == 1) && (endTimeStringList[0].length() == 0))) {  // Ensure the end field is set.
            // Initialize Time instances and add them to Vector
            for (int i = 0; i < len; i++) {
                try {
                    endTimeList.add(new TimeImpl(endTimeStringList[i],
                            getEndConstraints()));
                } catch (IllegalArgumentException e) {
                    // Ignore badly formatted times
                    Log.e(TAG, "Malformed time value.", e);
                }
            }
        }

        // "end" time is not specified
        if (endTimeList.size() == 0) {
            // Get duration
            float duration = getDur();

            if (duration < 0) {
                endTimeList.add(new TimeImpl("indefinite", getEndConstraints()));
            } else {
                // Get begin
                TimeList begin = getBegin();
                for (int i = 0; i < begin.getLength(); i++) {
                    endTimeList.add(new TimeImpl(
                            // end = begin + dur
                            begin.item(i).getResolvedOffset() + duration + "s",
                            getEndConstraints()));
                }
            }
        }

        return new TimeListImpl(endTimeList);
    }

    private boolean beginAndEndAreZero() {
        TimeList begin = getBegin();
        TimeList end = getEnd();
        if (begin.getLength() == 1 && end.getLength() == 1) {
            Time beginTime = begin.item(0);
            Time endTime = end.item(0);
            return beginTime.getOffset() == 0. && endTime.getOffset() == 0.;
        }
        return false;
    }

    public short getFill() {
        String fill = mSmilElement.getAttribute(FILL_ATTRIBUTE_NAME);
        if (fill.equalsIgnoreCase(FILL_FREEZE_ATTRIBUTE)) {
            return FILL_FREEZE;
        } else if (fill.equalsIgnoreCase(FILL_REMOVE_ATTRIBUTE)) {
            return FILL_REMOVE;
        } else if (fill.equalsIgnoreCase(FILL_HOLD_ATTRIBUTE)) {
            // FIXME handle it as freeze for now
            return FILL_FREEZE;
        } else if (fill.equalsIgnoreCase(FILL_TRANSITION_ATTRIBUTE)) {
            // FIXME handle it as freeze for now
            return FILL_FREEZE;
        } else if (!fill.equalsIgnoreCase(FILL_AUTO_ATTRIBUTE)) {
            /*
             * fill = default
             * The fill behavior for the element is determined by the value of the fillDefault
             * attribute.  This is the default value.
             */
            short fillDefault = getFillDefault();
            if (fillDefault != FILL_AUTO) {
                return fillDefault;
            }
        }

        /*
         * fill = auto
         * The fill behavior for this element depends on whether the element specifies any of
         * the attributes that define the simple or active duration:
         *  - If none of the attributes dur, end, repeatCount or repeatDur are specified on
         *    the element, then the element will have a fill behavior identical to that if it were
         *    specified as "freeze".
         *  - Otherwise, the element will have a fill behavior identical to that if it were
         *    specified as "remove".
         */
        if (((mSmilElement.getAttribute("dur").length() == 0) &&
                (mSmilElement.getAttribute("end").length() == 0) &&
                (mSmilElement.getAttribute("repeatCount").length() == 0) &&
                (mSmilElement.getAttribute("repeatDur").length() == 0)) ||
                beginAndEndAreZero()) {
            return FILL_FREEZE;
        } else {
            return FILL_REMOVE;
        }
    }

    public short getFillDefault() {
        String fillDefault = mSmilElement.getAttribute(FILLDEFAULT_ATTRIBUTE_NAME);
        if (fillDefault.equalsIgnoreCase(FILL_REMOVE_ATTRIBUTE)) {
            return FILL_REMOVE;
        } else if (fillDefault.equalsIgnoreCase(FILL_FREEZE_ATTRIBUTE)) {
            return FILL_FREEZE;
        } else if (fillDefault.equalsIgnoreCase(FILL_AUTO_ATTRIBUTE)) {
            return FILL_AUTO;
        } else if (fillDefault.equalsIgnoreCase(FILL_HOLD_ATTRIBUTE)) {
            // FIXME handle it as freeze for now
            return FILL_FREEZE;
        } else if (fillDefault.equalsIgnoreCase(FILL_TRANSITION_ATTRIBUTE)) {
            // FIXME handle it as freeze for now
            return FILL_FREEZE;
        } else {
            /*
             * fillDefault = inherit
             * Specifies that the value of this attribute (and of the fill behavior) are
             * inherited from the fillDefault value of the parent element.
             * This is the default value.
             */
            ElementTime parent = getParentElementTime();
            if (parent == null) {
                /*
                 * fillDefault = auto
                 * If there is no parent element, the value is "auto".
                 */
                return FILL_AUTO;
            } else {
                return ((ElementTimeImpl) parent).getFillDefault();
            }
        }
    }

    public float getRepeatCount() {
        String repeatCount = mSmilElement.getAttribute("repeatCount");
        try {
            float value = Float.parseFloat(repeatCount);
            if (value > 0) {
                return value;
            } else {
                return 0; // default
            }
        } catch (NumberFormatException e) {
            return 0; // default
        }
    }

    public float getRepeatDur() {
        try {
            float repeatDur =
                TimeImpl.parseClockValue(mSmilElement.getAttribute("repeatDur"));
            if (repeatDur > 0) {
                return repeatDur;
            } else {
                return 0; // default
            }
        } catch (IllegalArgumentException e) {
            return 0; // default
        }
    }

    public short getRestart() {
        String restart = mSmilElement.getAttribute("restart");
        if (restart.equalsIgnoreCase("never")) {
            return RESTART_NEVER;
        } else if (restart.equalsIgnoreCase("whenNotActive")) {
            return RESTART_WHEN_NOT_ACTIVE;
        } else {
            return RESTART_ALWAYS; // default
        }
    }

    public void setBegin(TimeList begin) throws DOMException {
        // TODO Implement this
        mSmilElement.setAttribute("begin", "indefinite");
    }

    public void setDur(float dur) throws DOMException {
        // In SMIL 3.0, the dur could be a timecount-value which may contain fractions.
        // However, in MMS 1.3, the dur SHALL be expressed in integer milliseconds.
        mSmilElement.setAttribute("dur", Integer.toString((int)(dur * 1000)) + "ms");
    }

    public void setEnd(TimeList end) throws DOMException {
        // TODO Implement this
        mSmilElement.setAttribute("end", "indefinite");
    }

    public void setFill(short fill) throws DOMException {
        if (fill == FILL_FREEZE) {
            mSmilElement.setAttribute(FILL_ATTRIBUTE_NAME, FILL_FREEZE_ATTRIBUTE);
        } else {
            mSmilElement.setAttribute(FILL_ATTRIBUTE_NAME, FILL_REMOVE_ATTRIBUTE); // default
        }
    }

    public void setFillDefault(short fillDefault) throws DOMException {
        if (fillDefault == FILL_FREEZE) {
            mSmilElement.setAttribute(FILLDEFAULT_ATTRIBUTE_NAME, FILL_FREEZE_ATTRIBUTE);
        } else {
            mSmilElement.setAttribute(FILLDEFAULT_ATTRIBUTE_NAME, FILL_REMOVE_ATTRIBUTE);
        }
    }

    public void setRepeatCount(float repeatCount) throws DOMException {
        String repeatCountString = "indefinite";
        if (repeatCount > 0) {
            repeatCountString = Float.toString(repeatCount);
        }
        mSmilElement.setAttribute("repeatCount", repeatCountString);
    }

    public void setRepeatDur(float repeatDur) throws DOMException {
        String repeatDurString = "indefinite";
        if (repeatDur > 0) {
            repeatDurString = Float.toString(repeatDur) + "ms";
        }
        mSmilElement.setAttribute("repeatDur", repeatDurString);
    }

    public void setRestart(short restart) throws DOMException {
        if (restart == RESTART_NEVER) {
            mSmilElement.setAttribute("restart", "never");
        } else if (restart == RESTART_WHEN_NOT_ACTIVE) {
            mSmilElement.setAttribute("restart", "whenNotActive");
        } else {
            mSmilElement.setAttribute("restart", "always");
        }
    }
}
