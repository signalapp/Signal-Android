/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ISAC_MACROS_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ISAC_MACROS_H_

#include "webrtc/engine_configurations.h"

namespace webrtc {

namespace acm2 {

#ifdef WEBRTC_CODEC_ISAC
#define ACM_ISAC_CREATE            WebRtcIsac_Create
#define ACM_ISAC_FREE              WebRtcIsac_Free
#define ACM_ISAC_ENCODERINIT       WebRtcIsac_EncoderInit
#define ACM_ISAC_ENCODE            WebRtcIsac_Encode
#define ACM_ISAC_DECODERINIT       WebRtcIsac_DecoderInit
#define ACM_ISAC_DECODE_BWE        WebRtcIsac_UpdateBwEstimate
#define ACM_ISAC_DECODE_B          WebRtcIsac_Decode
#define ACM_ISAC_DECODEPLC         WebRtcIsac_DecodePlc
#define ACM_ISAC_CONTROL           WebRtcIsac_Control
#define ACM_ISAC_CONTROL_BWE       WebRtcIsac_ControlBwe
#define ACM_ISAC_GETFRAMELEN       WebRtcIsac_ReadFrameLen
#define ACM_ISAC_GETERRORCODE      WebRtcIsac_GetErrorCode
#define ACM_ISAC_GETSENDBITRATE    WebRtcIsac_GetUplinkBw
#define ACM_ISAC_SETMAXPAYLOADSIZE WebRtcIsac_SetMaxPayloadSize
#define ACM_ISAC_SETMAXRATE        WebRtcIsac_SetMaxRate
#define ACM_ISAC_GETNEWBITSTREAM   WebRtcIsac_GetNewBitStream
#define ACM_ISAC_GETSENDBWE        WebRtcIsac_GetDownLinkBwIndex
#define ACM_ISAC_SETBWE            WebRtcIsac_UpdateUplinkBw
#define ACM_ISAC_GETBWE            WebRtcIsac_ReadBwIndex
#define ACM_ISAC_GETNEWFRAMELEN    WebRtcIsac_GetNewFrameLen
#define ACM_ISAC_STRUCT            ISACStruct
#define ACM_ISAC_GETENCSAMPRATE    WebRtcIsac_EncSampRate
#define ACM_ISAC_GETDECSAMPRATE    WebRtcIsac_DecSampRate
#define ACM_ISAC_DECODERCU         WebRtcIsac_DecodeRcu
#endif

#ifdef WEBRTC_CODEC_ISACFX
#define ACM_ISAC_CREATE            WebRtcIsacfix_Create
#define ACM_ISAC_FREE              WebRtcIsacfix_Free
#define ACM_ISAC_ENCODERINIT       WebRtcIsacfix_EncoderInit
#define ACM_ISAC_ENCODE            WebRtcIsacfix_Encode
#define ACM_ISAC_DECODERINIT       WebRtcIsacfix_DecoderInit
#define ACM_ISAC_DECODE_BWE        WebRtcIsacfix_UpdateBwEstimate
#define ACM_ISAC_DECODE_B          WebRtcIsacfix_Decode
#define ACM_ISAC_DECODEPLC         WebRtcIsacfix_DecodePlc
#define ACM_ISAC_CONTROL           ACMISACFixControl  // Local Impl
#define ACM_ISAC_CONTROL_BWE       ACMISACFixControlBWE  // Local Impl
#define ACM_ISAC_GETFRAMELEN       WebRtcIsacfix_ReadFrameLen
#define ACM_ISAC_GETERRORCODE      WebRtcIsacfix_GetErrorCode
#define ACM_ISAC_GETSENDBITRATE    ACMISACFixGetSendBitrate  // Local Impl
#define ACM_ISAC_SETMAXPAYLOADSIZE WebRtcIsacfix_SetMaxPayloadSize
#define ACM_ISAC_SETMAXRATE        WebRtcIsacfix_SetMaxRate
#define ACM_ISAC_GETNEWBITSTREAM   ACMISACFixGetNewBitstream  // Local Impl
#define ACM_ISAC_GETSENDBWE        ACMISACFixGetSendBWE  // Local Impl
#define ACM_ISAC_SETBWE            WebRtcIsacfix_UpdateUplinkBw
#define ACM_ISAC_GETBWE            WebRtcIsacfix_ReadBwIndex
#define ACM_ISAC_GETNEWFRAMELEN    WebRtcIsacfix_GetNewFrameLen
#define ACM_ISAC_STRUCT            ISACFIX_MainStruct
#define ACM_ISAC_GETENCSAMPRATE    ACMISACFixGetEncSampRate  // Local Impl
#define ACM_ISAC_GETDECSAMPRATE    ACMISACFixGetDecSampRate  // Local Impl
#define ACM_ISAC_DECODERCU         WebRtcIsacfix_Decode  // No special RCU
                                                         // decoder
#endif

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ISAC_MACROS_H_

