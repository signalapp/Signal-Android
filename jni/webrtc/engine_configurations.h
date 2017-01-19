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
//                              Voice and Video
// ============================================================================

// ----------------------------------------------------------------------------
//  [Voice] Codec settings
// ----------------------------------------------------------------------------

// iSAC is not included in the Mozilla build, but in all other builds.
#ifndef WEBRTC_MOZILLA_BUILD
#ifdef WEBRTC_ARCH_ARM
#define WEBRTC_CODEC_ISACFX  // Fix-point iSAC implementation.
#else
#define WEBRTC_CODEC_ISAC  // Floating-point iSAC implementation (default).
#endif  // WEBRTC_ARCH_ARM
#endif  // !WEBRTC_MOZILLA_BUILD

// AVT is included in all builds, along with G.711, NetEQ and CNG
// (which are mandatory and don't have any defines).
#define WEBRTC_CODEC_AVT

// PCM16 is useful for testing and incurs only a small binary size cost.
#define WEBRTC_CODEC_PCM16

// iLBC, G.722, and Redundancy coding are excluded from Chromium and Mozilla
// builds to reduce binary size.
#if !defined(WEBRTC_CHROMIUM_BUILD) && !defined(WEBRTC_MOZILLA_BUILD)
#define WEBRTC_CODEC_ILBC
#define WEBRTC_CODEC_G722
#define WEBRTC_CODEC_RED
#endif  // !WEBRTC_CHROMIUM_BUILD && !WEBRTC_MOZILLA_BUILD

// ----------------------------------------------------------------------------
//  [Video] Codec settings
// ----------------------------------------------------------------------------

#define VIDEOCODEC_I420
#define VIDEOCODEC_VP8
#define VIDEOCODEC_H264

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
#define WEBRTC_VOICE_ENGINE_DTMF_API
#define WEBRTC_VOICE_ENGINE_EXTERNAL_MEDIA_API
#define WEBRTC_VOICE_ENGINE_FILE_API
#define WEBRTC_VOICE_ENGINE_HARDWARE_API
#define WEBRTC_VOICE_ENGINE_NETEQ_STATS_API
#define WEBRTC_VOICE_ENGINE_RTP_RTCP_API
#define WEBRTC_VOICE_ENGINE_VIDEO_SYNC_API
#define WEBRTC_VOICE_ENGINE_VOLUME_CONTROL_API

// ============================================================================
//                                 VideoEngine
// ============================================================================

// ----------------------------------------------------------------------------
//  Settings for special VideoEngine configurations
// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------
//  VideoEngine sub-API:s
// ----------------------------------------------------------------------------

#define WEBRTC_VIDEO_ENGINE_CAPTURE_API
#define WEBRTC_VIDEO_ENGINE_CODEC_API
#define WEBRTC_VIDEO_ENGINE_IMAGE_PROCESS_API
#define WEBRTC_VIDEO_ENGINE_RENDER_API
#define WEBRTC_VIDEO_ENGINE_RTP_RTCP_API
#define WEBRTC_VIDEO_ENGINE_EXTERNAL_CODEC_API

// Now handled by gyp:
// WEBRTC_VIDEO_ENGINE_FILE_API

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

// ----------------------------------------------------------------------------
//  Deprecated
// ----------------------------------------------------------------------------

// #define WEBRTC_CODEC_G729
// #define WEBRTC_DTMF_DETECTION

// For RedPhone
#undef WEBRTC_CODEC_CELT
#undef WEBRTC_CODEC_G722
#undef WEBRTC_CODEC_ILBC
#undef WEBRTC_CODEC_ISACFX
#undef WEBRTC_CODEC_ISAC
#undef WEBRTC_CODEC_OPUS
#undef WEBRTC_CODEC_PCM16



#endif  // WEBRTC_ENGINE_CONFIGURATIONS_H_
