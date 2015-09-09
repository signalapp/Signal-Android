/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "TimedTrace.h"
#include <math.h>

double TimedTrace::_timeEllapsedSec = 0;
FILE* TimedTrace::_timedTraceFile = NULL;

TimedTrace::TimedTrace() {

}

TimedTrace::~TimedTrace() {
  if (_timedTraceFile != NULL) {
    fclose(_timedTraceFile);
  }
  _timedTraceFile = NULL;
}

int16_t TimedTrace::SetUp(char* fileName) {
  if (_timedTraceFile == NULL) {
    _timedTraceFile = fopen(fileName, "w");
  }
  if (_timedTraceFile == NULL) {
    return -1;
  }
  return 0;
}

void TimedTrace::SetTimeEllapsed(double timeEllapsedSec) {
  _timeEllapsedSec = timeEllapsedSec;
}

double TimedTrace::TimeEllapsed() {
  return _timeEllapsedSec;
}

void TimedTrace::Tick10Msec() {
  _timeEllapsedSec += 0.010;
}

void TimedTrace::TimedLogg(char* message) {
  unsigned int minutes = (uint32_t) floor(_timeEllapsedSec / 60.0);
  double seconds = _timeEllapsedSec - minutes * 60;
  //char myFormat[100] = "%8.2f, %3u:%05.2f: %s\n";
  if (_timedTraceFile != NULL) {
    fprintf(_timedTraceFile, "%8.2f, %3u:%05.2f: %s\n", _timeEllapsedSec,
            minutes, seconds, message);
  }
}
