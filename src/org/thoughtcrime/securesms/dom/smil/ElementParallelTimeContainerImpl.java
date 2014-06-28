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

import java.util.ArrayList;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.smil.ElementParallelTimeContainer;
import org.w3c.dom.smil.ElementTime;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.Time;
import org.w3c.dom.smil.TimeList;

import org.thoughtcrime.securesms.dom.NodeListImpl;

public abstract class ElementParallelTimeContainerImpl extends ElementTimeContainerImpl
        implements ElementParallelTimeContainer {
    private final static String ENDSYNC_ATTRIBUTE_NAME = "endsync";
    private final static String ENDSYNC_FIRST = "first";
    private final static String ENDSYNC_LAST  = "last";
    private final static String ENDSYNC_ALL   = "all";
    private final static String ENDSYNC_MEDIA = "media";

    /*
     * Internal Interface
     */

    ElementParallelTimeContainerImpl(SMILElement element) {
        super(element);
    }

    public String getEndSync() {
        String endsync = mSmilElement.getAttribute(ENDSYNC_ATTRIBUTE_NAME);
        if ((endsync == null) || (endsync.length() == 0)) {
            setEndSync(ENDSYNC_LAST);
            return ENDSYNC_LAST;
        }
        if (ENDSYNC_FIRST.equals(endsync) || ENDSYNC_LAST.equals(endsync) ||
                ENDSYNC_ALL.equals(endsync) || ENDSYNC_MEDIA.equals(endsync)) {
            return endsync;
        }

        // FIXME add the checking for ID-Value and smil1.0-Id-value.

        setEndSync(ENDSYNC_LAST);
        return ENDSYNC_LAST;
    }

    public void setEndSync(String endSync) throws DOMException {
        if (ENDSYNC_FIRST.equals(endSync) || ENDSYNC_LAST.equals(endSync) ||
                ENDSYNC_ALL.equals(endSync) || ENDSYNC_MEDIA.equals(endSync)) {
            mSmilElement.setAttribute(ENDSYNC_ATTRIBUTE_NAME, endSync);
        } else { // FIXME add the support for ID-Value and smil1.0-Id-value.
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                    "Unsupported endsync value" + endSync);
        }
    }

    @Override
    public float getDur() {
        float dur = super.getDur();
        if (dur == 0) {
            dur = getImplicitDuration();
        }
        return dur;
    }

    public float getImplicitDuration() {
        float dur = -1.0F;
        if (ENDSYNC_LAST.equals(getEndSync())) {
            NodeList children = getTimeChildren();
            for (int i = 0; i < children.getLength(); ++i) {
                ElementTime child = (ElementTime) children.item(i);
                TimeList endTimeList = child.getEnd();
                for (int j = 0; j < endTimeList.getLength(); ++j) {
                    Time endTime = endTimeList.item(j);
                    if (endTime.getTimeType() == Time.SMIL_TIME_INDEFINITE) {
                        // Return "indefinite" here.
                        return -1.0F;
                    }
                    if (endTime.getResolved()) {
                        float end = (float)endTime.getResolvedOffset();
                        dur = (end > dur) ? end : dur;
                    }
                }
            }
        } // Other endsync types are not supported now.

        return dur;
    }

    public NodeList getActiveChildrenAt(float instant) {
        /*
         * Find the closest Time of ElementTime before instant.
         * Add ElementTime to list of active elements if the Time belongs to the begin-list,
         * do not add it otherwise.
         */
        ArrayList<Node> activeChildren = new ArrayList<Node>();
        NodeList children = getTimeChildren();
        int childrenLen = children.getLength();
        for (int i = 0; i < childrenLen; ++i) {
            double maxOffset = 0.0;
            boolean active = false;
            ElementTime child = (ElementTime) children.item(i);

            TimeList beginList = child.getBegin();
            int len = beginList.getLength();
            for (int j = 0; j < len; ++j) {
                Time begin = beginList.item(j);
                if (begin.getResolved()) {
                    double resolvedOffset = begin.getResolvedOffset() * 1000.0;
                    if ((resolvedOffset <= instant) && (resolvedOffset >= maxOffset)) {
                        maxOffset = resolvedOffset;
                        active = true;
                    }
                }
            }

            TimeList endList = child.getEnd();
            len = endList.getLength();
            for (int j = 0; j < len; ++j) {
                Time end = endList.item(j);
                if (end.getResolved()) {
                    double resolvedOffset = end.getResolvedOffset() * 1000.0;
                    if ((resolvedOffset <= instant) && (resolvedOffset >= maxOffset)) {
                        maxOffset = resolvedOffset;
                        active = false;
                    }
                }
            }

            if (active) {
                activeChildren.add((Node) child);
            }
        }
        return new NodeListImpl(activeChildren);
    }
}
