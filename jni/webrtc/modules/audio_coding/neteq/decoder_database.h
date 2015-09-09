/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECODER_DATABASE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECODER_DATABASE_H_

#include <map>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_types.h"  // NULL
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declaration.
class AudioDecoder;

class DecoderDatabase {
 public:
  enum DatabaseReturnCodes {
    kOK = 0,
    kInvalidRtpPayloadType = -1,
    kCodecNotSupported = -2,
    kInvalidSampleRate = -3,
    kDecoderExists = -4,
    kDecoderNotFound = -5,
    kInvalidPointer = -6
  };

  // Struct used to store decoder info in the database.
  struct DecoderInfo {
    // Constructors.
    DecoderInfo()
        : codec_type(kDecoderArbitrary),
          fs_hz(8000),
          decoder(NULL),
          external(false) {
    }
    DecoderInfo(NetEqDecoder ct, int fs, AudioDecoder* dec, bool ext)
        : codec_type(ct),
          fs_hz(fs),
          decoder(dec),
          external(ext) {
    }
    // Destructor. (Defined in decoder_database.cc.)
    ~DecoderInfo();

    NetEqDecoder codec_type;
    int fs_hz;
    AudioDecoder* decoder;
    bool external;
  };

  static const uint8_t kMaxRtpPayloadType = 0x7F;  // Max for a 7-bit number.
  // Maximum value for 8 bits, and an invalid RTP payload type (since it is
  // only 7 bits).
  static const uint8_t kRtpPayloadTypeError = 0xFF;

  DecoderDatabase();

  virtual ~DecoderDatabase();

  // Returns true if the database is empty.
  virtual bool Empty() const;

  // Returns the number of decoders registered in the database.
  virtual int Size() const;

  // Resets the database, erasing all registered payload types, and deleting
  // any AudioDecoder objects that were not externally created and inserted
  // using InsertExternal().
  virtual void Reset();

  // Registers |rtp_payload_type| as a decoder of type |codec_type|. Returns
  // kOK on success; otherwise an error code.
  virtual int RegisterPayload(uint8_t rtp_payload_type,
                              NetEqDecoder codec_type);

  // Registers an externally created AudioDecoder object, and associates it
  // as a decoder of type |codec_type| with |rtp_payload_type|.
  virtual int InsertExternal(uint8_t rtp_payload_type,
                             NetEqDecoder codec_type,
                             int fs_hz, AudioDecoder* decoder);

  // Removes the entry for |rtp_payload_type| from the database.
  // Returns kDecoderNotFound or kOK depending on the outcome of the operation.
  virtual int Remove(uint8_t rtp_payload_type);

  // Returns a pointer to the DecoderInfo struct for |rtp_payload_type|. If
  // no decoder is registered with that |rtp_payload_type|, NULL is returned.
  virtual const DecoderInfo* GetDecoderInfo(uint8_t rtp_payload_type) const;

  // Returns one RTP payload type associated with |codec_type|, or
  // kDecoderNotFound if no entry exists for that value. Note that one
  // |codec_type| may be registered with several RTP payload types, and the
  // method may return any of them.
  virtual uint8_t GetRtpPayloadType(NetEqDecoder codec_type) const;

  // Returns a pointer to the AudioDecoder object associated with
  // |rtp_payload_type|, or NULL if none is registered. If the AudioDecoder
  // object does not exist for that decoder, the object is created.
  virtual AudioDecoder* GetDecoder(uint8_t rtp_payload_type);

  // Returns true if |rtp_payload_type| is registered as a |codec_type|.
  virtual bool IsType(uint8_t rtp_payload_type,
                      NetEqDecoder codec_type) const;

  // Returns true if |rtp_payload_type| is registered as comfort noise.
  virtual bool IsComfortNoise(uint8_t rtp_payload_type) const;

  // Returns true if |rtp_payload_type| is registered as DTMF.
  virtual bool IsDtmf(uint8_t rtp_payload_type) const;

  // Returns true if |rtp_payload_type| is registered as RED.
  virtual bool IsRed(uint8_t rtp_payload_type) const;

  // Sets the active decoder to be |rtp_payload_type|. If this call results in a
  // change of active decoder, |new_decoder| is set to true. The previous active
  // decoder's AudioDecoder object is deleted.
  virtual int SetActiveDecoder(uint8_t rtp_payload_type, bool* new_decoder);

  // Returns the current active decoder, or NULL if no active decoder exists.
  virtual AudioDecoder* GetActiveDecoder();

  // Sets the active comfort noise decoder to be |rtp_payload_type|. If this
  // call results in a change of active comfort noise decoder, the previous
  // active decoder's AudioDecoder object is deleted.
  virtual int SetActiveCngDecoder(uint8_t rtp_payload_type);

  // Returns the current active comfort noise decoder, or NULL if no active
  // comfort noise decoder exists.
  virtual AudioDecoder* GetActiveCngDecoder();

  // Returns kOK if all packets in |packet_list| carry payload types that are
  // registered in the database. Otherwise, returns kDecoderNotFound.
  virtual int CheckPayloadTypes(const PacketList& packet_list) const;

 private:
  typedef std::map<uint8_t, DecoderInfo> DecoderMap;

  DecoderMap decoders_;
  int active_decoder_;
  int active_cng_decoder_;

  DISALLOW_COPY_AND_ASSIGN(DecoderDatabase);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECODER_DATABASE_H_
