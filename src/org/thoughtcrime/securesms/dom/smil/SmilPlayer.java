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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import org.w3c.dom.NodeList;
import org.w3c.dom.events.DocumentEvent;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.smil.ElementParallelTimeContainer;
import org.w3c.dom.smil.ElementSequentialTimeContainer;
import org.w3c.dom.smil.ElementTime;
import org.w3c.dom.smil.Time;
import org.w3c.dom.smil.TimeList;

import android.util.Log;

/**
 * The SmilPlayer is responsible for playing, stopping, pausing and resuming a SMIL tree.
 * <li>It creates a whole timeline before playing.</li>
 * <li>The player runs in a different thread which intends not to block the main thread.</li>
 */
public class SmilPlayer implements Runnable {
    private static final String TAG = "Mms/smil";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;
    private static final int TIMESLICE = 200;

    private static enum SmilPlayerState {
        INITIALIZED,
        PLAYING,
        PLAYED,
        PAUSED,
        STOPPED,
    }

    private static enum SmilPlayerAction {
        NO_ACTIVE_ACTION,
        RELOAD,
        STOP,
        PAUSE,
        START,
        NEXT,
        PREV
    }

    public static final String MEDIA_TIME_UPDATED_EVENT = "mediaTimeUpdated";

    private static final Comparator<TimelineEntry> sTimelineEntryComparator =
        new Comparator<TimelineEntry>() {
        public int compare(TimelineEntry o1, TimelineEntry o2) {
            return Double.compare(o1.getOffsetTime(), o2.getOffsetTime());
        }
    };

    private static SmilPlayer sPlayer;

    private long mCurrentTime;
    private int mCurrentElement;
    private int mCurrentSlide;
    private ArrayList<TimelineEntry> mAllEntries;
    private ElementTime mRoot;
    private Thread mPlayerThread;
    private SmilPlayerState mState = SmilPlayerState.INITIALIZED;
    private SmilPlayerAction mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
    private ArrayList<ElementTime> mActiveElements;
    private Event mMediaTimeUpdatedEvent;

    private static ArrayList<TimelineEntry> getParTimeline(
            ElementParallelTimeContainer par, double offset, double maxOffset) {
        ArrayList<TimelineEntry> timeline = new ArrayList<TimelineEntry>();

        // Set my begin at first
        TimeList myBeginList = par.getBegin();
        /*
         * Begin list only contain 1 begin time which has been resolved.
         * @see org.thoughtcrime.securesms.dom.smil.ElementParallelTimeContainerImpl#getBegin()
         */
        Time begin = myBeginList.item(0);
        double beginOffset = begin.getResolvedOffset() + offset;
        if (beginOffset > maxOffset) {
            // This element can't be started.
            return timeline;
        }
        TimelineEntry myBegin = new TimelineEntry(beginOffset, par, TimelineEntry.ACTION_BEGIN);
        timeline.add(myBegin);

        TimeList myEndList = par.getEnd();
        /*
         * End list only contain 1 end time which has been resolved.
         * @see org.thoughtcrime.securesms.dom.smil.ElementParallelTimeContainerImpl#getEnd()
         */
        Time end = myEndList.item(0);
        double endOffset = end.getResolvedOffset() + offset;
        if (endOffset > maxOffset) {
            endOffset = maxOffset;
        }
        TimelineEntry myEnd = new TimelineEntry(endOffset, par, TimelineEntry.ACTION_END);

        maxOffset = endOffset;

        NodeList children = par.getTimeChildren();
        for (int i = 0; i < children.getLength(); ++i) {
            ElementTime child = (ElementTime) children.item(i);
            ArrayList<TimelineEntry> childTimeline = getTimeline(child, offset, maxOffset);
            timeline.addAll(childTimeline);
        }

        Collections.sort(timeline, sTimelineEntryComparator);

        // Add end-event to timeline for all active children
        NodeList activeChildrenAtEnd = par.getActiveChildrenAt(
                (float) (endOffset - offset) * 1000);
        for (int i = 0; i < activeChildrenAtEnd.getLength(); ++i) {
            timeline.add(new TimelineEntry(endOffset,
                    (ElementTime) activeChildrenAtEnd.item(i),
                    TimelineEntry.ACTION_END));
        }

        // Set my end at last
        timeline.add(myEnd);

        return timeline;
    }

    private static ArrayList<TimelineEntry> getSeqTimeline(
            ElementSequentialTimeContainer seq, double offset, double maxOffset) {
        ArrayList<TimelineEntry> timeline = new ArrayList<TimelineEntry>();
        double orgOffset = offset;

        // Set my begin at first
        TimeList myBeginList = seq.getBegin();
        /*
         * Begin list only contain 1 begin time which has been resolved.
         * @see org.thoughtcrime.securesms.dom.smil.ElementSequentialTimeContainerImpl#getBegin()
         */
        Time begin = myBeginList.item(0);
        double beginOffset = begin.getResolvedOffset() + offset;
        if (beginOffset > maxOffset) {
            // This element can't be started.
            return timeline;
        }
        TimelineEntry myBegin = new TimelineEntry(beginOffset, seq, TimelineEntry.ACTION_BEGIN);
        timeline.add(myBegin);

        TimeList myEndList = seq.getEnd();
        /*
         * End list only contain 1 end time which has been resolved.
         * @see org.thoughtcrime.securesms.dom.smil.ElementSequentialTimeContainerImpl#getEnd()
         */
        Time end = myEndList.item(0);
        double endOffset = end.getResolvedOffset() + offset;
        if (endOffset > maxOffset) {
            endOffset = maxOffset;
        }
        TimelineEntry myEnd = new TimelineEntry(endOffset, seq, TimelineEntry.ACTION_END);

        maxOffset = endOffset;

        // Get children's timelines
        NodeList children = seq.getTimeChildren();
        for (int i = 0; i < children.getLength(); ++i) {
            ElementTime child = (ElementTime) children.item(i);
            ArrayList<TimelineEntry> childTimeline = getTimeline(child, offset, maxOffset);
            timeline.addAll(childTimeline);

            // Since the child timeline has been sorted, the offset of the last one is the biggest.
            offset = childTimeline.get(childTimeline.size() - 1).getOffsetTime();
        }

        // Add end-event to timeline for all active children
        NodeList activeChildrenAtEnd = seq.getActiveChildrenAt(
                (float) (endOffset - orgOffset));
        for (int i = 0; i < activeChildrenAtEnd.getLength(); ++i) {
            timeline.add(new TimelineEntry(endOffset,
                    (ElementTime) activeChildrenAtEnd.item(i),
                    TimelineEntry.ACTION_END));
        }

        // Set my end at last
        timeline.add(myEnd);

        return timeline;
    }

    private static ArrayList<TimelineEntry> getTimeline(ElementTime element,
            double offset, double maxOffset) {
        if (element instanceof ElementParallelTimeContainer) {
            return getParTimeline((ElementParallelTimeContainer) element, offset, maxOffset);
        } else if (element instanceof ElementSequentialTimeContainer) {
            return getSeqTimeline((ElementSequentialTimeContainer) element, offset, maxOffset);
        } else {
            // Not ElementTimeContainer here
            ArrayList<TimelineEntry> timeline = new ArrayList<TimelineEntry>();

            TimeList beginList = element.getBegin();
            for (int i = 0; i < beginList.getLength(); ++i) {
                Time begin = beginList.item(i);
                if (begin.getResolved()) {
                    double beginOffset = begin.getResolvedOffset() + offset;
                    if (beginOffset <= maxOffset) {
                        TimelineEntry entry = new TimelineEntry(beginOffset,
                                element, TimelineEntry.ACTION_BEGIN);
                        timeline.add(entry);
                    }
                }
            }

            TimeList endList = element.getEnd();
            for (int i = 0; i < endList.getLength(); ++i) {
                Time end = endList.item(i);
                if (end.getResolved()) {
                    double endOffset = end.getResolvedOffset() + offset;
                    if (endOffset <= maxOffset) {
                        TimelineEntry entry = new TimelineEntry(endOffset,
                                element, TimelineEntry.ACTION_END);
                        timeline.add(entry);
                    }
                }
            }

            Collections.sort(timeline, sTimelineEntryComparator);

            return timeline;
        }
    }

    private SmilPlayer() {
        // Private constructor
    }

    public static SmilPlayer getPlayer() {
        if (sPlayer == null) {
            sPlayer = new SmilPlayer();
        }
        return sPlayer;
    }

    public synchronized boolean isPlayingState() {
        return mState == SmilPlayerState.PLAYING;
    }

    public synchronized boolean isPlayedState() {
        return mState == SmilPlayerState.PLAYED;
    }

    public synchronized boolean isPausedState() {
        return mState == SmilPlayerState.PAUSED;
    }

    public synchronized boolean isStoppedState() {
        return mState == SmilPlayerState.STOPPED;
    }

    private synchronized boolean isPauseAction() {
        return mAction == SmilPlayerAction.PAUSE;
    }

    private synchronized boolean isStartAction() {
        return mAction == SmilPlayerAction.START;
    }

    private synchronized boolean isStopAction() {
        return mAction == SmilPlayerAction.STOP;
    }

    private synchronized boolean isReloadAction() {
        return mAction == SmilPlayerAction.RELOAD;
    }

    private synchronized boolean isNextAction() {
      return mAction == SmilPlayerAction.NEXT;
    }

    private synchronized boolean isPrevAction() {
      return mAction == SmilPlayerAction.PREV;
    }

    public synchronized void init(ElementTime root) {
        mRoot = root;
        mAllEntries = getTimeline(mRoot, 0, Long.MAX_VALUE);
        mMediaTimeUpdatedEvent = ((DocumentEvent) mRoot).createEvent("Event");
        mMediaTimeUpdatedEvent.initEvent(MEDIA_TIME_UPDATED_EVENT, false, false);
        mActiveElements = new ArrayList<ElementTime>();
    }

    public synchronized void play() {
        if (!isPlayingState()) {
            mCurrentTime = 0;
            mCurrentElement = 0;
            mCurrentSlide = 0;
            mPlayerThread = new Thread(this, "SmilPlayer thread");
            mState = SmilPlayerState.PLAYING;
            mPlayerThread.start();
        } else {
            Log.w(TAG, "Error State: Playback is playing!");
        }
    }

    public synchronized void pause() {
        if (isPlayingState()) {
            mAction = SmilPlayerAction.PAUSE;
            notifyAll();
        } else {
            Log.w(TAG, "Error State: Playback is not playing!");
        }
    }

    public synchronized void start() {
        if (isPausedState()) {
            resumeActiveElements();
            mAction = SmilPlayerAction.START;
            notifyAll();
        } else if (isPlayedState()) {
            play();
        } else {
            Log.w(TAG, "Error State: Playback can not be started!");
        }
    }

    public synchronized void stop() {
        if (isPlayingState() || isPausedState()) {
            mAction = SmilPlayerAction.STOP;
            notifyAll();
        } else if (isPlayedState()) {
            actionStop();
        }
    }

    public synchronized void stopWhenReload() {
        endActiveElements();
    }

    public synchronized void reload() {
        if (isPlayingState() || isPausedState()) {
            mAction = SmilPlayerAction.RELOAD;
            notifyAll();
        } else if (isPlayedState()) {
            actionReload();
        }
    }

    public synchronized void next() {
      if (isPlayingState() || isPausedState()) {
        mAction = SmilPlayerAction.NEXT;
        notifyAll();
      }
    }

    public synchronized void prev() {
      if (isPlayingState() || isPausedState()) {
        mAction = SmilPlayerAction.PREV;
        notifyAll();
      }
    }

    private synchronized boolean isBeginOfSlide(TimelineEntry entry) {
        return (TimelineEntry.ACTION_BEGIN == entry.getAction())
                    && (entry.getElement() instanceof SmilParElementImpl);
    }

    private synchronized void reloadActiveSlide() {
        mActiveElements.clear();
        beginSmilDocument();

        for (int i = mCurrentSlide; i < mCurrentElement; i++) {
            TimelineEntry entry = mAllEntries.get(i);
            actionEntry(entry);
        }
        seekActiveMedia();
    }

    private synchronized void beginSmilDocument() {
        TimelineEntry entry = mAllEntries.get(0);
        actionEntry(entry);
    }

    private synchronized double getOffsetTime(ElementTime element) {
        for (int i = mCurrentSlide; i < mCurrentElement; i++) {
            TimelineEntry entry = mAllEntries.get(i);
            if (element.equals(entry.getElement())) {
                return entry.getOffsetTime() * 1000;  // in ms
            }
        }
        return -1;
    }

    private synchronized void seekActiveMedia() {
        for (int i = mActiveElements.size() - 1; i >= 0; i--) {
            ElementTime element = mActiveElements.get(i);
            if (element instanceof SmilParElementImpl) {
                return;
            }
            double offset = getOffsetTime(element);
            if ((offset >= 0) && (offset <= mCurrentTime)) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "[SEEK]  " + " at " + mCurrentTime
                            + " " + element);
                }
                element.seekElement( (float) (mCurrentTime - offset) );
            }
        }
    }

    private synchronized void waitForEntry(long interval)
            throws InterruptedException {
        if (LOCAL_LOGV) {
            Log.v(TAG, "Waiting for " + interval + "ms.");
        }

        long overhead = 0;

        while (interval > 0) {
            long startAt = System.currentTimeMillis();
            long sleep = Math.min(interval, TIMESLICE);
            if (overhead < sleep) {
                wait(sleep - overhead);
                mCurrentTime += sleep;
            } else {
                sleep = 0;
                mCurrentTime += overhead;
            }

            if (isStopAction() || isReloadAction() || isPauseAction() || isNextAction() ||
                isPrevAction()) {
                return;
            }

            ((EventTarget) mRoot).dispatchEvent(mMediaTimeUpdatedEvent);

            interval -= TIMESLICE;
            overhead = System.currentTimeMillis() - startAt - sleep;
        }
    }

    public synchronized int getDuration() {
         if ((mAllEntries != null) && !mAllEntries.isEmpty()) {
             return (int) mAllEntries.get(mAllEntries.size() - 1).mOffsetTime * 1000;
         }
         return 0;
    }

    public synchronized int getCurrentPosition() {
        return (int) mCurrentTime;
    }

    private synchronized void endActiveElements() {
        for (int i = mActiveElements.size() - 1; i >= 0; i--) {
            ElementTime element = mActiveElements.get(i);
            if (LOCAL_LOGV) {
                Log.v(TAG, "[STOP]  " + " at " + mCurrentTime
                        + " " + element);
            }
            element.endElement();
        }
    }

    private synchronized void pauseActiveElements() {
        for (int i = mActiveElements.size() - 1; i >= 0; i--) {
            ElementTime element = mActiveElements.get(i);
            if (LOCAL_LOGV) {
                Log.v(TAG, "[PAUSE]  " + " at " + mCurrentTime
                        + " " + element);
            }
            element.pauseElement();
        }
    }

    private synchronized void resumeActiveElements() {
        int size = mActiveElements.size();
        for (int i = 0; i < size; i++) {
            ElementTime element = mActiveElements.get(i);
            if (LOCAL_LOGV) {
                Log.v(TAG, "[RESUME]  " + " at " + mCurrentTime
                        + " " + element);
            }
            element.resumeElement();
        }
    }

    private synchronized void waitForWakeUp() {
        try {
            while ( !(isStartAction() || isStopAction() || isReloadAction() ||
                    isNextAction() || isPrevAction()) ) {
                wait(TIMESLICE);
            }
            if (isStartAction()) {
                mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
                mState = SmilPlayerState.PLAYING;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Unexpected InterruptedException.", e);
        }
    }

    private synchronized void actionEntry(TimelineEntry entry) {
        switch (entry.getAction()) {
            case TimelineEntry.ACTION_BEGIN:
                if (LOCAL_LOGV) {
                    Log.v(TAG, "[START] " + " at " + mCurrentTime + " "
                            + entry.getElement());
                }
                entry.getElement().beginElement();
                mActiveElements.add(entry.getElement());
                break;
            case TimelineEntry.ACTION_END:
                if (LOCAL_LOGV) {
                    Log.v(TAG, "[STOP]  " + " at " + mCurrentTime + " "
                            + entry.getElement());
                }
                entry.getElement().endElement();
                mActiveElements.remove(entry.getElement());
                break;
            default:
                break;
        }
    }

    private synchronized TimelineEntry reloadCurrentEntry() {
        // Check if the position is less than size of all entries
        if (mCurrentElement < mAllEntries.size()) {
            return mAllEntries.get(mCurrentElement);
        } else {
            return null;
        }
    }

    private void stopCurrentSlide() {
        HashSet<TimelineEntry> skippedEntries = new HashSet<TimelineEntry>();
        int totalEntries = mAllEntries.size();
        for (int i = mCurrentElement; i < totalEntries; i++) {
            // Stop any started entries, and skip the not started entries until
            // meeting the end of slide
            TimelineEntry entry = mAllEntries.get(i);
            int action = entry.getAction();
            if (entry.getElement() instanceof SmilParElementImpl &&
                    action == TimelineEntry.ACTION_END) {
                actionEntry(entry);
                mCurrentElement = i;
                break;
            } else if (action == TimelineEntry.ACTION_END && !skippedEntries.contains(entry)) {
                    actionEntry(entry);
            } else if (action == TimelineEntry.ACTION_BEGIN) {
                skippedEntries.add(entry);
            }
        }
    }

    private TimelineEntry loadNextSlide() {
      TimelineEntry entry;
      int totalEntries = mAllEntries.size();
      for (int i = mCurrentElement; i < totalEntries; i++) {
          entry = mAllEntries.get(i);
          if (isBeginOfSlide(entry)) {
              mCurrentElement = i;
              mCurrentSlide = i;
              mCurrentTime = (long)(entry.getOffsetTime() * 1000);
              return entry;
          }
      }
      // No slide, finish play back
      mCurrentElement++;
      entry = null;
      if (mCurrentElement < totalEntries) {
          entry = mAllEntries.get(mCurrentElement);
          mCurrentTime = (long)(entry.getOffsetTime() * 1000);
      }
      return entry;
    }

    private TimelineEntry loadPrevSlide() {
      int skippedSlides = 1;
      int latestBeginEntryIndex = -1;
      for (int i = mCurrentSlide; i >= 0; i--) {
        TimelineEntry entry = mAllEntries.get(i);
        if (isBeginOfSlide(entry)) {
            latestBeginEntryIndex = i;
          if (0 == skippedSlides-- ) {
            mCurrentElement = i;
            mCurrentSlide = i;
            mCurrentTime = (long)(entry.getOffsetTime() * 1000);
            return entry;
          }
        }
      }
      if (latestBeginEntryIndex != -1) {
          mCurrentElement = latestBeginEntryIndex;
          mCurrentSlide = latestBeginEntryIndex;
          return mAllEntries.get(mCurrentElement);
      }
      return null;
    }

    private synchronized TimelineEntry actionNext() {
        stopCurrentSlide();
        return loadNextSlide();
   }

    private synchronized TimelineEntry actionPrev() {
        stopCurrentSlide();
        return loadPrevSlide();
    }

    private synchronized void actionPause() {
        pauseActiveElements();
        mState = SmilPlayerState.PAUSED;
        mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
    }

    private synchronized void actionStop() {
        endActiveElements();
        mCurrentTime = 0;
        mCurrentElement = 0;
        mCurrentSlide = 0;
        mState = SmilPlayerState.STOPPED;
        mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
    }

    private synchronized void actionReload() {
        reloadActiveSlide();
        mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
    }

    public void run() {
        if (isStoppedState()) {
            return;
        }
        if (LOCAL_LOGV) {
            dumpAllEntries();
        }
        // Play the Element by following the timeline
        int size = mAllEntries.size();
        for (mCurrentElement = 0; mCurrentElement < size; mCurrentElement++) {
            TimelineEntry entry = mAllEntries.get(mCurrentElement);
            if (isBeginOfSlide(entry)) {
                mCurrentSlide = mCurrentElement;
            }
            long offset = (long) (entry.getOffsetTime() * 1000); // in ms.
            while (offset > mCurrentTime) {
                try {
                    waitForEntry(offset - mCurrentTime);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Unexpected InterruptedException.", e);
                }

                while (isPauseAction() || isStopAction() || isReloadAction() || isNextAction() ||
                    isPrevAction()) {
                    if (isPauseAction()) {
                        actionPause();
                        waitForWakeUp();
                    }

                    if (isStopAction()) {
                        actionStop();
                        return;
                    }

                    if (isReloadAction()) {
                        actionReload();
                        entry = reloadCurrentEntry();
                        if (entry == null)
                            return;
                        if (isPausedState()) {
                            mAction = SmilPlayerAction.PAUSE;
                        }
                    }

                    if (isNextAction()) {
                        TimelineEntry nextEntry = actionNext();
                        if (nextEntry != null) {
                            entry = nextEntry;
                        }
                        if (mState == SmilPlayerState.PAUSED) {
                            mAction = SmilPlayerAction.PAUSE;
                            actionEntry(entry);
                        } else {
                            mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
                        }
                        offset = mCurrentTime;
                    }

                    if (isPrevAction()) {
                        TimelineEntry prevEntry = actionPrev();
                        if (prevEntry != null) {
                            entry = prevEntry;
                        }
                        if (mState == SmilPlayerState.PAUSED) {
                            mAction = SmilPlayerAction.PAUSE;
                            actionEntry(entry);
                        } else {
                            mAction = SmilPlayerAction.NO_ACTIVE_ACTION;
                        }
                        offset = mCurrentTime;
                    }
                }
            }
            mCurrentTime = offset;
            actionEntry(entry);
        }

        mState = SmilPlayerState.PLAYED;
    }

    private static final class TimelineEntry {
        final static int ACTION_BEGIN = 0;
        final static int ACTION_END   = 1;

        private final double mOffsetTime;
        private final ElementTime mElement;
        private final int mAction;

        public TimelineEntry(double offsetTime, ElementTime element, int action) {
            mOffsetTime = offsetTime;
            mElement = element;
            mAction  = action;
        }

        public double getOffsetTime() {
            return mOffsetTime;
        }

        public ElementTime getElement() {
            return mElement;
        }

        public int getAction() {
            return mAction;
        }

        public String toString() {
            return "Type = " + mElement + " offset = " + getOffsetTime() + " action = " + getAction();
        }
    }

    private void dumpAllEntries() {
        if (LOCAL_LOGV) {
            for (TimelineEntry entry : mAllEntries) {
                Log.v(TAG, "[Entry] "+ entry);
            }
        }
    }
}
