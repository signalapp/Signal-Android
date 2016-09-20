/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_ENGINE_CONFIGURATIONS_H_
#define WEBRTC_ENGINE_CONFIGURATIONS_H_

#include "webrtc/typedefs.h"

// ============================================================================
//                                 VoiceEngine
// ============================================================================

// ----------------------------------------------------------------------------
//  Settings for VoiceEngine
// ----------------------------------------------------------------------------

#define WEBRTC_VOICE_ENGINE_AGC                 // Near-end AGC
#define WEBRTC_VOICE_ENGINE_ECHO                // Near-end AEC
#define WEBRTC_VOICE_ENGINE_NR                  // Near-end NS

#if !defined(WEBRTC_ANDROID) && !defined(WEBRTC_IOS)
#define WEBRTC_VOICE_ENGINE_TYPING_DETECTION    // Typing detection
#endif

// ----------------------------------------------------------------------------
//  VoiceEngine sub-APIs
// ----------------------------------------------------------------------------

#define WEBRTC_VOICE_ENGINE_AUDIO_PROCESSING_API
#define WEBRTC_VOICE_ENGINE_CODEC_API
#define WEBRTC_VOICE_ENGINE_EXTERNAL_MEDIA_API
#define WEBRTC_VOICE_ENGINE_FILE_API
#define WEBRTC_VOICE_ENGINE_HARDWARE_API
#define WEBRTC_VOICE_ENGINE_NETEQ_STATS_API
#define WEBRTC_VOICE_ENGINE_RTP_RTCP_API
#define WEBRTC_VOICE_ENGINE_VIDEO_SYNC_API
#define WEBRTC_VOICE_ENGINE_VOLUME_CONTROL_API

// ============================================================================
//                       Platform specific configurations
// ============================================================================

// ----------------------------------------------------------------------------
//  VideoEngine Windows
// ----------------------------------------------------------------------------

#if defined(_WIN32)
#define DIRECT3D9_RENDERING  // Requires DirectX 9.
#endif

// ----------------------------------------------------------------------------
//  VideoEngine MAC
// ----------------------------------------------------------------------------

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
// #define CARBON_RENDERING
#define COCOA_RENDERING
#endif

// ----------------------------------------------------------------------------
//  VideoEngine Mobile iPhone
// ----------------------------------------------------------------------------

#if defined(WEBRTC_IOS)
#define EAGL_RENDERING
#endif

#endif  // WEBRTC_ENGINE_CONFIGURATIONS_H_
