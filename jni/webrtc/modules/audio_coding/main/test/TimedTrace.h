/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef TIMED_TRACE_H
#define TIMED_TRACE_H

#include "typedefs.h"

#include <stdio.h>
#include <stdlib.h>

class TimedTrace {
 public:
  TimedTrace();
  ~TimedTrace();

  void SetTimeEllapsed(double myTime);
  double TimeEllapsed();
  void Tick10Msec();
  int16_t SetUp(char* fileName);
  void TimedLogg(char* message);

 private:
  static double _timeEllapsedSec;
  static FILE* _timedTraceFile;

};

#endif
