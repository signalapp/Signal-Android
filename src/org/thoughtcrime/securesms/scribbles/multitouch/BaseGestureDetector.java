package org.thoughtcrime.securesms.scribbles.multitouch;

import android.content.Context;
import android.view.MotionEvent;

/**
 * @author Almer Thie (code.almeros.com)
 *         Copyright (c) 2013, Almer Thie (code.almeros.com)
 *         <p>
 *         All rights reserved.
 *         <p>
 *         Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *         <p>
 *         Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *         Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer
 *         in the documentation and/or other materials provided with the distribution.
 *         <p>
 *         THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 *         INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *         IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *         OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 *         OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *         OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 *         OF SUCH DAMAGE.
 */
public abstract class BaseGestureDetector {
    /**
     * This value is the threshold ratio between the previous combined pressure
     * and the current combined pressure. When pressure decreases rapidly
     * between events the position values can often be imprecise, as it usually
     * indicates that the user is in the process of lifting a pointer off of the
     * device. This value was tuned experimentally.
     */
    protected static final float PRESSURE_THRESHOLD = 0.67f;
    protected final Context mContext;
    protected boolean mGestureInProgress;
    protected MotionEvent mPrevEvent;
    protected MotionEvent mCurrEvent;
    protected float mCurrPressure;
    protected float mPrevPressure;
    protected long mTimeDelta;


    public BaseGestureDetector(Context context) {
        mContext = context;
    }

    /**
     * All gesture detectors need to be called through this method to be able to
     * detect gestures. This method delegates work to handler methods
     * (handleStartProgressEvent, handleInProgressEvent) implemented in
     * extending classes.
     *
     * @param event
     * @return
     */
    public boolean onTouchEvent(MotionEvent event) {
        final int actionCode = event.getAction() & MotionEvent.ACTION_MASK;
        if (!mGestureInProgress) {
            handleStartProgressEvent(actionCode, event);
        } else {
            handleInProgressEvent(actionCode, event);
        }
        return true;
    }

    /**
     * Called when the current event occurred when NO gesture is in progress
     * yet. The handling in this implementation may set the gesture in progress
     * (via mGestureInProgress) or out of progress
     *
     * @param actionCode
     * @param event
     */
    protected abstract void handleStartProgressEvent(int actionCode, MotionEvent event);

    /**
     * Called when the current event occurred when a gesture IS in progress. The
     * handling in this implementation may set the gesture out of progress (via
     * mGestureInProgress).
     *
     * @param actionCode
     * @param event
     */
    protected abstract void handleInProgressEvent(int actionCode, MotionEvent event);


    protected void updateStateByEvent(MotionEvent curr) {
        final MotionEvent prev = mPrevEvent;

        // Reset mCurrEvent
        if (mCurrEvent != null) {
            mCurrEvent.recycle();
            mCurrEvent = null;
        }
        mCurrEvent = MotionEvent.obtain(curr);


        // Delta time
        mTimeDelta = curr.getEventTime() - prev.getEventTime();

        // Pressure
        mCurrPressure = curr.getPressure(curr.getActionIndex());
        mPrevPressure = prev.getPressure(prev.getActionIndex());
    }

    protected void resetState() {
        if (mPrevEvent != null) {
            mPrevEvent.recycle();
            mPrevEvent = null;
        }
        if (mCurrEvent != null) {
            mCurrEvent.recycle();
            mCurrEvent = null;
        }
        mGestureInProgress = false;
    }


    /**
     * Returns {@code true} if a gesture is currently in progress.
     *
     * @return {@code true} if a gesture is currently in progress, {@code false} otherwise.
     */
    public boolean isInProgress() {
        return mGestureInProgress;
    }

    /**
     * Return the time difference in milliseconds between the previous accepted
     * GestureDetector event and the current GestureDetector event.
     *
     * @return Time difference since the last move event in milliseconds.
     */
    public long getTimeDelta() {
        return mTimeDelta;
    }

    /**
     * Return the event time of the current GestureDetector event being
     * processed.
     *
     * @return Current GestureDetector event time in milliseconds.
     */
    public long getEventTime() {
        return mCurrEvent.getEventTime();
    }

}
