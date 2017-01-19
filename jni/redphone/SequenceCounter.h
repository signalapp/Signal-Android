#ifndef __SEQUENCE_COUNTER_H__
#define __SEQUENCE_COUNTER_H__

#include <stdint.h>

#define 	INT16_MAX   0x7fff
#define 	INT16_MIN   (-INT16_MAX - 1)

const int64_t ShortRange = ((int64_t)1) << 16;

class SequenceCounter {

private:
  uint16_t prevShortId;
  int64_t  prevLongId;

//  int64_t currentLongId;

public:
  SequenceCounter() : prevShortId(0), prevLongId(0) {}

  int64_t convertNext(uint16_t nextShortId) {
    int64_t delta = (int64_t)nextShortId - (int64_t)prevShortId;

    if (delta > INT16_MAX) delta -= ShortRange;
    if (delta < INT16_MIN) delta += ShortRange;

    int64_t nextLongId = prevLongId + delta;

    prevShortId = nextShortId;
    prevLongId  = nextLongId;

    return nextLongId;
  }
};


#endif