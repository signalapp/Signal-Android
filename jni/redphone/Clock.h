#ifndef __CLOCK_H__
#define __CLOCK_H__

#include "AudioCodec.h"

#include <android/log.h>

class Clock {

private:
  volatile uint32_t tickCount;

  uint32_t dataReceived;

public:
  Clock() : tickCount(0), dataReceived(0) {}

  uint32_t tick(int frames) {
    tickCount += (frames * SPEEX_FRAME_SIZE);
    return tickCount;
  }

  uint32_t getTickCount() {
    return tickCount;
  }

  uint32_t getImprovisedTimestamp(int dataLen) {
    dataReceived += dataLen;
    return (dataReceived / SPEEX_ENCODED_FRAME_SIZE) * SPEEX_FRAME_SIZE;
  }

};

#endif