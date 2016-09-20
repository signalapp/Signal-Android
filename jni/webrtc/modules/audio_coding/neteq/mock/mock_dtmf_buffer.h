/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DTMF_BUFFER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DTMF_BUFFER_H_

#include "webrtc/modules/audio_coding/neteq/dtmf_buffer.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockDtmfBuffer : public DtmfBuffer {
 public:
  MockDtmfBuffer(int fs) : DtmfBuffer(fs) {}
  virtual ~MockDtmfBuffer() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_METHOD0(Flush,
      void());
  MOCK_METHOD1(InsertEvent,
      int(const DtmfEvent& event));
  MOCK_METHOD2(GetEvent,
      bool(uint32_t current_timestamp, DtmfEvent* event));
  MOCK_CONST_METHOD0(Length,
      size_t());
  MOCK_CONST_METHOD0(Empty,
      bool());
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DTMF_BUFFER_H_
