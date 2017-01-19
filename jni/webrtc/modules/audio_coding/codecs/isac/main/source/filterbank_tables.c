/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* filterbank_tables.c*/
/* This file contains variables that are used in filterbanks.c*/

#include "filterbank_tables.h"
#include "settings.h"

/* The composite all-pass filter factors */
const float WebRtcIsac_kCompositeApFactorsFloat[4] = {
 0.03470000000000f,  0.15440000000000f,  0.38260000000000f,  0.74400000000000f};

/* The upper channel all-pass filter factors */
const float WebRtcIsac_kUpperApFactorsFloat[2] = {
 0.03470000000000f,  0.38260000000000f};

/* The lower channel all-pass filter factors */
const float WebRtcIsac_kLowerApFactorsFloat[2] = {
 0.15440000000000f,  0.74400000000000f};

/* The matrix for transforming the backward composite state to upper channel state */
const float WebRtcIsac_kTransform1Float[8] = {
  -0.00158678506084f,  0.00127157815343f, -0.00104805672709f,  0.00084837248079f,
  0.00134467983258f, -0.00107756549387f,  0.00088814793277f, -0.00071893072525f};

/* The matrix for transforming the backward composite state to lower channel state */
const float WebRtcIsac_kTransform2Float[8] = {
 -0.00170686041697f,  0.00136780109829f, -0.00112736532350f,  0.00091257055385f,
  0.00103094281812f, -0.00082615076557f,  0.00068092756088f, -0.00055119165484f};
