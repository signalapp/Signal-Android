package org.thoughtcrime.securesms.scribbles.multitouch;

import android.content.Context;
import android.view.MotionEvent;

/**
 * @author Robert Nordan (robert.nordan@norkart.no)
 *         <p>
 *         Copyright (c) 2013, Norkart AS
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
public class ShoveGestureDetector extends TwoFingerGestureDetector {

    private final OnShoveGestureListener mListener;
    private float mPrevAverageY;
    private float mCurrAverageY;
    private boolean mSloppyGesture;

    public ShoveGestureDetector(Context context, OnShoveGestureListener listener) {
        super(context);
        mListener = listener;
    }

    @Override
    protected void handleStartProgressEvent(int actionCode, MotionEvent event) {
        switch (actionCode) {
            case MotionEvent.ACTION_POINTER_DOWN:
                // At least the second finger is on screen now

                resetState(); // In case we missed an UP/CANCEL event
                mPrevEvent = MotionEvent.obtain(event);
                mTimeDelta = 0;

                updateStateByEvent(event);

                // See if we have a sloppy gesture
                mSloppyGesture = isSloppyGesture(event);
                if (!mSloppyGesture) {
                    // No, start gesture now
                    mGestureInProgress = mListener.onShoveBegin(this);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mSloppyGesture) {
                    break;
                }

                // See if we still have a sloppy gesture
                mSloppyGesture = isSloppyGesture(event);
                if (!mSloppyGesture) {
                    // No, start normal gesture now
                    mGestureInProgress = mListener.onShoveBegin(this);
                }

                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (!mSloppyGesture) {
                    break;
                }

                break;
        }
    }

    @Override
    protected void handleInProgressEvent(int actionCode, MotionEvent event) {
        switch (actionCode) {
            case MotionEvent.ACTION_POINTER_UP:
                // Gesture ended but
                updateStateByEvent(event);

                if (!mSloppyGesture) {
                    mListener.onShoveEnd(this);
                }

                resetState();
                break;

            case MotionEvent.ACTION_CANCEL:
                if (!mSloppyGesture) {
                    mListener.onShoveEnd(this);
                }

                resetState();
                break;

            case MotionEvent.ACTION_MOVE:
                updateStateByEvent(event);

                // Only accept the event if our relative pressure is within
                // a certain limit. This can help filter shaky data as a
                // finger is lifted. Also check that shove is meaningful.
                if (mCurrPressure / mPrevPressure > PRESSURE_THRESHOLD
                        && Math.abs(getShovePixelsDelta()) > 0.5f) {
                    final boolean updatePrevious = mListener.onShove(this);
                    if (updatePrevious) {
                        mPrevEvent.recycle();
                        mPrevEvent = MotionEvent.obtain(event);
                    }
                }
                break;
        }
    }

    @Override
    protected void updateStateByEvent(MotionEvent curr) {
        super.updateStateByEvent(curr);

        final MotionEvent prev = mPrevEvent;
        float py0 = prev.getY(0);
        float py1 = prev.getY(1);
        mPrevAverageY = (py0 + py1) / 2.0f;

        float cy0 = curr.getY(0);
        float cy1 = curr.getY(1);
        mCurrAverageY = (cy0 + cy1) / 2.0f;
    }

    @Override
    protected boolean isSloppyGesture(MotionEvent event) {
        boolean sloppy = super.isSloppyGesture(event);
        if (sloppy)
            return true;

        // If it's not traditionally sloppy, we check if the angle between fingers
        // is acceptable.
        double angle = Math.abs(Math.atan2(mCurrFingerDiffY, mCurrFingerDiffX));
        //about 20 degrees, left or right
        return !((0.0f < angle && angle < 0.35f)
                || 2.79f < angle && angle < Math.PI);
    }

    /**
     * Return the distance in pixels from the previous shove event to the current
     * event.
     *
     * @return The current distance in pixels.
     */
    public float getShovePixelsDelta() {
        return mCurrAverageY - mPrevAverageY;
    }

    @Override
    protected void resetState() {
        super.resetState();
        mSloppyGesture = false;
        mPrevAverageY = 0.0f;
        mCurrAverageY = 0.0f;
    }

    /**
     * Listener which must be implemented which is used by ShoveGestureDetector
     * to perform callbacks to any implementing class which is registered to a
     * ShoveGestureDetector via the constructor.
     *
     * @see ShoveGestureDetector.SimpleOnShoveGestureListener
     */
    public interface OnShoveGestureListener {
        public boolean onShove(ShoveGestureDetector detector);

        public boolean onShoveBegin(ShoveGestureDetector detector);

        public void onShoveEnd(ShoveGestureDetector detector);
    }

    /**
     * Helper class which may be extended and where the methods may be
     * implemented. This way it is not necessary to implement all methods
     * of OnShoveGestureListener.
     */
    public static class SimpleOnShoveGestureListener implements OnShoveGestureListener {
        public boolean onShove(ShoveGestureDetector detector) {
            return false;
        }

        public boolean onShoveBegin(ShoveGestureDetector detector) {
            return true;
        }

        public void onShoveEnd(ShoveGestureDetector detector) {
            // Do nothing, overridden implementation may be used
        }
    }
}
