/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * filterbank_tables.h
 *
 * Header file for variables that are defined in
 * filterbank_tables.c.
 *
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_FILTERBANK_TABLES_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_FILTERBANK_TABLES_H_

#include "structs.h"

/********************* Coefficient Tables ************************/
/* The number of composite all-pass filter factors */
#define NUMBEROFCOMPOSITEAPSECTIONS 4

/* The number of all-pass filter factors in an upper or lower channel*/
#define NUMBEROFCHANNELAPSECTIONS 2

/* The composite all-pass filter factors */
extern const float WebRtcIsac_kCompositeApFactorsFloat[4];

/* The upper channel all-pass filter factors */
extern const float WebRtcIsac_kUpperApFactorsFloat[2];

/* The lower channel all-pass filter factors */
extern const float WebRtcIsac_kLowerApFactorsFloat[2];

/* The matrix for transforming the backward composite state to upper channel state */
extern const float WebRtcIsac_kTransform1Float[8];

/* The matrix for transforming the backward composite state to lower channel state */
extern const float WebRtcIsac_kTransform2Float[8];

#endif /* WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_FILTERBANK_TABLES_H_ */
