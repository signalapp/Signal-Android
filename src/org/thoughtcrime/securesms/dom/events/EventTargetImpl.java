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

package org.thoughtcrime.securesms.dom.events;

import java.util.ArrayList;

import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventException;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import android.util.Log;

public class EventTargetImpl implements EventTarget {
    private static final String TAG = "EventTargetImpl";
    private ArrayList<EventListenerEntry> mListenerEntries;
    private EventTarget mNodeTarget;

    static class EventListenerEntry
    {
        final String mType;
        final EventListener mListener;
        final boolean mUseCapture;

        EventListenerEntry(String type, EventListener listener, boolean useCapture)
        {
            mType = type;
            mListener = listener;
            mUseCapture = useCapture;
        }
    }

    public EventTargetImpl(EventTarget target) {
        mNodeTarget = target;
    }

    public void addEventListener(String type, EventListener listener, boolean useCapture) {
        if ((type == null) || type.equals("") || (listener == null)) {
            return;
        }

        // Make sure we have only one entry
        removeEventListener(type, listener, useCapture);

        if (mListenerEntries == null) {
            mListenerEntries = new ArrayList<EventListenerEntry>();
        }
        mListenerEntries.add(new EventListenerEntry(type, listener, useCapture));
    }

    public boolean dispatchEvent(Event evt) throws EventException {
        // We need to use the internal APIs to modify and access the event status
        EventImpl eventImpl = (EventImpl)evt;

        if (!eventImpl.isInitialized()) {
            throw new EventException(EventException.UNSPECIFIED_EVENT_TYPE_ERR,
                    "Event not initialized");
        } else if ((eventImpl.getType() == null) || eventImpl.getType().equals("")) {
            throw new EventException(EventException.UNSPECIFIED_EVENT_TYPE_ERR,
                    "Unspecified even type");
        }

        // Initialize event status
        eventImpl.setTarget(mNodeTarget);

        // TODO: At this point, to support event capturing and bubbling, we should
        // establish the chain of EventTargets from the top of the tree to this
        // event's target.

        // TODO: CAPTURING_PHASE skipped

        // Handle AT_TARGET
        // Invoke handleEvent of non-capturing listeners on this EventTarget.
        eventImpl.setEventPhase(Event.AT_TARGET);
        eventImpl.setCurrentTarget(mNodeTarget);
        if (!eventImpl.isPropogationStopped() && (mListenerEntries != null)) {
            for (int i = 0; i < mListenerEntries.size(); i++) {
                EventListenerEntry listenerEntry = mListenerEntries.get(i);
                if (!listenerEntry.mUseCapture
                        && listenerEntry.mType.equals(eventImpl.getType())) {
                    try {
                        listenerEntry.mListener.handleEvent(eventImpl);
                    }
                    catch (Exception e) {
                        // Any exceptions thrown inside an EventListener will
                        // not stop propagation of the event
                        Log.w(TAG, "Catched EventListener exception", e);
                    }
                }
            }
        }

        if (eventImpl.getBubbles()) {
            // TODO: BUBBLING_PHASE skipped
        }

        return eventImpl.isPreventDefault();
    }

    public void removeEventListener(String type, EventListener listener,
            boolean useCapture) {
        if (null == mListenerEntries) {
            return;
        }
        for (int i = 0; i < mListenerEntries.size(); i ++) {
            EventListenerEntry listenerEntry = mListenerEntries.get(i);
            if ((listenerEntry.mUseCapture == useCapture)
                    && (listenerEntry.mListener == listener)
                    && listenerEntry.mType.equals(type)) {
                mListenerEntries.remove(i);
                break;
            }
        }
    }

}
