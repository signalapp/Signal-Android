/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef NETEQTEST_DUMMYRTPPACKET_H
#define NETEQTEST_DUMMYRTPPACKET_H

#include "NETEQTEST_RTPpacket.h"

class NETEQTEST_DummyRTPpacket : public NETEQTEST_RTPpacket {
 public:
  virtual int readFromFile(FILE* fp) OVERRIDE;
  virtual int writeToFile(FILE* fp) OVERRIDE;
  virtual void parseHeader() OVERRIDE;
};

#endif  // NETEQTEST_DUMMYRTPPACKET_H
