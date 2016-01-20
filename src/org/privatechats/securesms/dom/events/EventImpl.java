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

package org.privatechats.securesms.dom.events;

import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventTarget;

public class EventImpl implements Event {

    // Event type informations
    private String mEventType;
    private boolean mCanBubble;
    private boolean mCancelable;

    // Flags whether the event type information was set
    // FIXME: Can we use mEventType for this purpose?
    private boolean mInitialized;

    // Target of this event
    private EventTarget mTarget;

    // Event status variables
    private short mEventPhase;
    private boolean mStopPropagation;
    private boolean mPreventDefault;
    private EventTarget mCurrentTarget;
    private int mSeekTo;

    private final long mTimeStamp = System.currentTimeMillis();

    public boolean getBubbles() {
        return mCanBubble;
    }

    public boolean getCancelable() {
        return mCancelable;
    }

    public EventTarget getCurrentTarget() {
        return mCurrentTarget;
    }

    public short getEventPhase() {
        return mEventPhase;
    }

    public EventTarget getTarget() {
        return mTarget;
    }

    public long getTimeStamp() {
        return mTimeStamp;
    }

    public String getType() {
        return mEventType;
    }

    public void initEvent(String eventTypeArg, boolean canBubbleArg,
            boolean cancelableArg) {
        mEventType = eventTypeArg;
        mCanBubble = canBubbleArg;
        mCancelable = cancelableArg;
        mInitialized = true;
    }

    public void initEvent(String eventTypeArg, boolean canBubbleArg, boolean cancelableArg,
            int seekTo) {
        mSeekTo = seekTo;
        initEvent(eventTypeArg, canBubbleArg, cancelableArg);
    }

    public void preventDefault() {
        mPreventDefault = true;
    }

    public void stopPropagation() {
        mStopPropagation = true;
    }

    /*
     * Internal Interface
     */

    boolean isInitialized() {
        return mInitialized;
    }

    boolean isPreventDefault() {
        return mPreventDefault;
    }

    boolean isPropogationStopped() {
        return mStopPropagation;
    }

    void setTarget(EventTarget target) {
        mTarget = target;
    }

    void setEventPhase(short eventPhase) {
        mEventPhase = eventPhase;
    }

    void setCurrentTarget(EventTarget currentTarget) {
        mCurrentTarget = currentTarget;
    }

    public int getSeekTo() {
        return mSeekTo;
    }
}
