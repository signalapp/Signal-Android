#ifndef __SAMPLE_RATE_UTIL_H__
#define __SAMPLE_RATE_UTIL_H__

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

class SampleRateUtil {

public:
  static SLuint32 convertSampleRate(SLuint32 rate) {
    switch(rate) {
    case 8000:  return SL_SAMPLINGRATE_8;
    case 11025: return SL_SAMPLINGRATE_11_025;
    case 12000: return SL_SAMPLINGRATE_12;
    case 16000: return SL_SAMPLINGRATE_16;
    case 22050: return SL_SAMPLINGRATE_22_05;
    case 24000: return SL_SAMPLINGRATE_24;
    case 32000: return SL_SAMPLINGRATE_32;
    case 44100: return SL_SAMPLINGRATE_44_1;
    case 48000: return SL_SAMPLINGRATE_48;
    case 64000: return SL_SAMPLINGRATE_64;
    case 88200: return SL_SAMPLINGRATE_88_2;
    case 96000: return SL_SAMPLINGRATE_96;
    case 192000: return SL_SAMPLINGRATE_192;
    }

    return -1;
  }


};

#endif