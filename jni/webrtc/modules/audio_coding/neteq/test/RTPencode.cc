/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// TODO(hlundin): Reformat file to meet style guide.

/* header includes */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef WIN32
#include <winsock2.h>
#endif
#ifdef WEBRTC_LINUX
#include <netinet/in.h>
#endif

#include <assert.h>

#include <algorithm>

#include "webrtc/base/checks.h"
#include "webrtc/typedefs.h"

// needed for NetEqDecoder
#include "webrtc/modules/audio_coding/neteq/audio_decoder_impl.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"

/************************/
/* Define payload types */
/************************/

#include "PayloadTypes.h"

namespace {
const size_t kRtpDataSize = 8000;
}

/*********************/
/* Misc. definitions */
/*********************/

#define STOPSENDTIME 3000
#define RESTARTSENDTIME 0  // 162500
#define FIRSTLINELEN 40
#define CHECK_NOT_NULL(a)                                                \
  if ((a) == 0) {                                                        \
    printf("\n %s \n line: %d \nerror at %s\n", __FILE__, __LINE__, #a); \
    return (-1);                                                         \
  }

//#define MULTIPLE_SAME_TIMESTAMP
#define REPEAT_PACKET_DISTANCE 17
#define REPEAT_PACKET_COUNT 1  // number of extra packets to send

//#define INSERT_OLD_PACKETS
#define OLD_PACKET 5  // how many seconds too old should the packet be?

//#define TIMESTAMP_WRAPAROUND

//#define RANDOM_DATA
//#define RANDOM_PAYLOAD_DATA
#define RANDOM_SEED 10

//#define INSERT_DTMF_PACKETS
//#define NO_DTMF_OVERDUB
#define DTMF_PACKET_INTERVAL 2000
#define DTMF_DURATION 500

#define STEREO_MODE_FRAME 0
#define STEREO_MODE_SAMPLE_1 1  // 1 octet per sample
#define STEREO_MODE_SAMPLE_2 2  // 2 octets per sample

/*************************/
/* Function declarations */
/*************************/

void NetEQTest_GetCodec_and_PT(char* name,
                               webrtc::NetEqDecoder* codec,
                               int* PT,
                               size_t frameLen,
                               int* fs,
                               int* bitrate,
                               int* useRed);
int NetEQTest_init_coders(webrtc::NetEqDecoder coder,
                          size_t enc_frameSize,
                          int bitrate,
                          int sampfreq,
                          int vad,
                          size_t numChannels);
void defineCodecs(webrtc::NetEqDecoder* usedCodec, int* noOfCodecs);
int NetEQTest_free_coders(webrtc::NetEqDecoder coder, size_t numChannels);
size_t NetEQTest_encode(webrtc::NetEqDecoder coder,
                        int16_t* indata,
                        size_t frameLen,
                        unsigned char* encoded,
                        int sampleRate,
                        int* vad,
                        int useVAD,
                        int bitrate,
                        size_t numChannels);
void makeRTPheader(unsigned char* rtp_data,
                   int payloadType,
                   int seqNo,
                   uint32_t timestamp,
                   uint32_t ssrc);
int makeRedundantHeader(unsigned char* rtp_data,
                        int* payloadType,
                        int numPayloads,
                        uint32_t* timestamp,
                        uint16_t* blockLen,
                        int seqNo,
                        uint32_t ssrc);
size_t makeDTMFpayload(unsigned char* payload_data,
                       int Event,
                       int End,
                       int Volume,
                       int Duration);
void stereoDeInterleave(int16_t* audioSamples, size_t numSamples);
void stereoInterleave(unsigned char* data, size_t dataLen, size_t stride);

/*********************/
/* Codec definitions */
/*********************/

#include "webrtc_vad.h"

#if ((defined CODEC_PCM16B) || (defined NETEQ_ARBITRARY_CODEC))
#include "webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.h"
#endif
#ifdef CODEC_G711
#include "webrtc/modules/audio_coding/codecs/g711/g711_interface.h"
#endif
#ifdef CODEC_G729
#include "G729Interface.h"
#endif
#ifdef CODEC_G729_1
#include "G729_1Interface.h"
#endif
#ifdef CODEC_AMR
#include "AMRInterface.h"
#include "AMRCreation.h"
#endif
#ifdef CODEC_AMRWB
#include "AMRWBInterface.h"
#include "AMRWBCreation.h"
#endif
#ifdef CODEC_ILBC
#include "webrtc/modules/audio_coding/codecs/ilbc/ilbc.h"
#endif
#if (defined CODEC_ISAC || defined CODEC_ISAC_SWB)
#include "webrtc/modules/audio_coding/codecs/isac/main/include/isac.h"
#endif
#ifdef NETEQ_ISACFIX_CODEC
#include "webrtc/modules/audio_coding/codecs/isac/fix/include/isacfix.h"
#ifdef CODEC_ISAC
#error Cannot have both ISAC and ISACfix defined. Please de-select one.
#endif
#endif
#ifdef CODEC_G722
#include "webrtc/modules/audio_coding/codecs/g722/g722_interface.h"
#endif
#ifdef CODEC_G722_1_24
#include "G722_1Interface.h"
#endif
#ifdef CODEC_G722_1_32
#include "G722_1Interface.h"
#endif
#ifdef CODEC_G722_1_16
#include "G722_1Interface.h"
#endif
#ifdef CODEC_G722_1C_24
#include "G722_1Interface.h"
#endif
#ifdef CODEC_G722_1C_32
#include "G722_1Interface.h"
#endif
#ifdef CODEC_G722_1C_48
#include "G722_1Interface.h"
#endif
#ifdef CODEC_G726
#include "G726Creation.h"
#include "G726Interface.h"
#endif
#ifdef CODEC_GSMFR
#include "GSMFRInterface.h"
#include "GSMFRCreation.h"
#endif
#if (defined(CODEC_CNGCODEC8) || defined(CODEC_CNGCODEC16) || \
     defined(CODEC_CNGCODEC32) || defined(CODEC_CNGCODEC48))
#include "webrtc/modules/audio_coding/codecs/cng/webrtc_cng.h"
#endif
#if ((defined CODEC_SPEEX_8) || (defined CODEC_SPEEX_16))
#include "SpeexInterface.h"
#endif
#ifdef CODEC_OPUS
#include "webrtc/modules/audio_coding/codecs/opus/opus_interface.h"
#endif

/***********************************/
/* Global codec instance variables */
/***********************************/

WebRtcVadInst* VAD_inst[2];

#ifdef CODEC_G722
G722EncInst* g722EncState[2];
#endif

#ifdef CODEC_G722_1_24
G722_1_24_encinst_t* G722_1_24enc_inst[2];
#endif
#ifdef CODEC_G722_1_32
G722_1_32_encinst_t* G722_1_32enc_inst[2];
#endif
#ifdef CODEC_G722_1_16
G722_1_16_encinst_t* G722_1_16enc_inst[2];
#endif
#ifdef CODEC_G722_1C_24
G722_1C_24_encinst_t* G722_1C_24enc_inst[2];
#endif
#ifdef CODEC_G722_1C_32
G722_1C_32_encinst_t* G722_1C_32enc_inst[2];
#endif
#ifdef CODEC_G722_1C_48
G722_1C_48_encinst_t* G722_1C_48enc_inst[2];
#endif
#ifdef CODEC_G726
G726_encinst_t* G726enc_inst[2];
#endif
#ifdef CODEC_G729
G729_encinst_t* G729enc_inst[2];
#endif
#ifdef CODEC_G729_1
G729_1_inst_t* G729_1_inst[2];
#endif
#ifdef CODEC_AMR
AMR_encinst_t* AMRenc_inst[2];
int16_t AMR_bitrate;
#endif
#ifdef CODEC_AMRWB
AMRWB_encinst_t* AMRWBenc_inst[2];
int16_t AMRWB_bitrate;
#endif
#ifdef CODEC_ILBC
IlbcEncoderInstance* iLBCenc_inst[2];
#endif
#ifdef CODEC_ISAC
ISACStruct* ISAC_inst[2];
#endif
#ifdef NETEQ_ISACFIX_CODEC
ISACFIX_MainStruct* ISAC_inst[2];
#endif
#ifdef CODEC_ISAC_SWB
ISACStruct* ISACSWB_inst[2];
#endif
#ifdef CODEC_GSMFR
GSMFR_encinst_t* GSMFRenc_inst[2];
#endif
#if (defined(CODEC_CNGCODEC8) || defined(CODEC_CNGCODEC16) || \
     defined(CODEC_CNGCODEC32) || defined(CODEC_CNGCODEC48))
webrtc::ComfortNoiseEncoder *CNG_encoder[2];
#endif
#ifdef CODEC_SPEEX_8
SPEEX_encinst_t* SPEEX8enc_inst[2];
#endif
#ifdef CODEC_SPEEX_16
SPEEX_encinst_t* SPEEX16enc_inst[2];
#endif
#ifdef CODEC_OPUS
OpusEncInst* opus_inst[2];
#endif

int main(int argc, char* argv[]) {
  size_t packet_size;
  int fs;
  webrtc::NetEqDecoder usedCodec;
  int payloadType;
  int bitrate = 0;
  int useVAD, vad;
  int useRed = 0;
  size_t len, enc_len;
  int16_t org_data[4000];
  unsigned char rtp_data[kRtpDataSize];
  int16_t seqNo = 0xFFF;
  uint32_t ssrc = 1235412312;
  uint32_t timestamp = 0xAC1245;
  uint16_t length, plen;
  uint32_t offset;
  double sendtime = 0;
  int red_PT[2] = {0};
  uint32_t red_TS[2] = {0};
  uint16_t red_len[2] = {0};
  size_t RTPheaderLen = 12;
  uint8_t red_data[kRtpDataSize];
#ifdef INSERT_OLD_PACKETS
  uint16_t old_length, old_plen;
  size_t old_enc_len;
  int first_old_packet = 1;
  unsigned char old_rtp_data[kRtpDataSize];
  size_t packet_age = 0;
#endif
#ifdef INSERT_DTMF_PACKETS
  int NTone = 1;
  int DTMFfirst = 1;
  uint32_t DTMFtimestamp;
  bool dtmfSent = false;
#endif
  bool usingStereo = false;
  size_t stereoMode = 0;
  size_t numChannels = 1;

  /* check number of parameters */
  if ((argc != 6) && (argc != 7)) {
    /* print help text and exit */
    printf("Application to encode speech into an RTP stream.\n");
    printf("The program reads a PCM file and encodes is using the specified "
           "codec.\n");
    printf("The coded speech is packetized in RTP packest and written to the "
           "output file.\n");
    printf("The format of the RTP stream file is simlilar to that of "
           "rtpplay,\n");
    printf("but with the receive time euqal to 0 for all packets.\n");
    printf("Usage:\n\n");
    printf("%s PCMfile RTPfile frameLen codec useVAD bitrate\n", argv[0]);
    printf("where:\n");

    printf("PCMfile      : PCM speech input file\n\n");

    printf("RTPfile      : RTP stream output file\n\n");

    printf("frameLen     : 80...960...  Number of samples per packet (limit "
           "depends on codec)\n\n");

    printf("codecName\n");
#ifdef CODEC_PCM16B
    printf("             : pcm16b       16 bit PCM (8kHz)\n");
#endif
#ifdef CODEC_PCM16B_WB
    printf("             : pcm16b_wb   16 bit PCM (16kHz)\n");
#endif
#ifdef CODEC_PCM16B_32KHZ
    printf("             : pcm16b_swb32 16 bit PCM (32kHz)\n");
#endif
#ifdef CODEC_PCM16B_48KHZ
    printf("             : pcm16b_swb48 16 bit PCM (48kHz)\n");
#endif
#ifdef CODEC_G711
    printf("             : pcma         g711 A-law (8kHz)\n");
#endif
#ifdef CODEC_G711
    printf("             : pcmu         g711 u-law (8kHz)\n");
#endif
#ifdef CODEC_G729
    printf("             : g729         G729 (8kHz and 8kbps) CELP (One-Three "
           "frame(s)/packet)\n");
#endif
#ifdef CODEC_G729_1
    printf("             : g729.1       G729.1 (16kHz) variable rate (8--32 "
           "kbps)\n");
#endif
#ifdef CODEC_G722_1_16
    printf("             : g722.1_16    G722.1 coder (16kHz) (g722.1 with "
           "16kbps)\n");
#endif
#ifdef CODEC_G722_1_24
    printf("             : g722.1_24    G722.1 coder (16kHz) (the 24kbps "
           "version)\n");
#endif
#ifdef CODEC_G722_1_32
    printf("             : g722.1_32    G722.1 coder (16kHz) (the 32kbps "
           "version)\n");
#endif
#ifdef CODEC_G722_1C_24
    printf("             : g722.1C_24    G722.1 C coder (32kHz) (the 24kbps "
           "version)\n");
#endif
#ifdef CODEC_G722_1C_32
    printf("             : g722.1C_32    G722.1 C coder (32kHz) (the 32kbps "
           "version)\n");
#endif
#ifdef CODEC_G722_1C_48
    printf("             : g722.1C_48    G722.1 C coder (32kHz) (the 48kbps "
           "version)\n");
#endif

#ifdef CODEC_G726
    printf("             : g726_16      G726 coder (8kHz) 16kbps\n");
    printf("             : g726_24      G726 coder (8kHz) 24kbps\n");
    printf("             : g726_32      G726 coder (8kHz) 32kbps\n");
    printf("             : g726_40      G726 coder (8kHz) 40kbps\n");
#endif
#ifdef CODEC_AMR
    printf("             : AMRXk        Adaptive Multi Rate CELP codec "
           "(8kHz)\n");
    printf("                            X = 4.75, 5.15, 5.9, 6.7, 7.4, 7.95, "
           "10.2 or 12.2\n");
#endif
#ifdef CODEC_AMRWB
    printf("             : AMRwbXk      Adaptive Multi Rate Wideband CELP "
           "codec (16kHz)\n");
    printf("                            X = 7, 9, 12, 14, 16, 18, 20, 23 or "
           "24\n");
#endif
#ifdef CODEC_ILBC
    printf("             : ilbc         iLBC codec (8kHz and 13.8kbps)\n");
#endif
#ifdef CODEC_ISAC
    printf("             : isac         iSAC (16kHz and 32.0 kbps). To set "
           "rate specify a rate parameter as last parameter\n");
#endif
#ifdef CODEC_ISAC_SWB
    printf("             : isacswb       iSAC SWB (32kHz and 32.0-52.0 kbps). "
           "To set rate specify a rate parameter as last parameter\n");
#endif
#ifdef CODEC_GSMFR
    printf("             : gsmfr        GSM FR codec (8kHz and 13kbps)\n");
#endif
#ifdef CODEC_G722
    printf("             : g722         g722 coder (16kHz) (the 64kbps "
           "version)\n");
#endif
#ifdef CODEC_SPEEX_8
    printf("             : speex8       speex coder (8 kHz)\n");
#endif
#ifdef CODEC_SPEEX_16
    printf("             : speex16      speex coder (16 kHz)\n");
#endif
#ifdef CODEC_RED
#ifdef CODEC_G711
    printf("             : red_pcm      Redundancy RTP packet with 2*G711A "
           "frames\n");
#endif
#ifdef CODEC_ISAC
    printf("             : red_isac     Redundancy RTP packet with 2*iSAC "
           "frames\n");
#endif
#endif  // CODEC_RED
#ifdef CODEC_OPUS
    printf("             : opus         Opus codec with FEC (48kHz, 32kbps, FEC"
        " on and tuned for 5%% packet losses)\n");
#endif
    printf("\n");

#if (defined(CODEC_CNGCODEC8) || defined(CODEC_CNGCODEC16) || \
     defined(CODEC_CNGCODEC32) || defined(CODEC_CNGCODEC48))
    printf("useVAD       : 0 Voice Activity Detection is switched off\n");
    printf("             : 1 Voice Activity Detection is switched on\n\n");
#else
    printf("useVAD       : 0 Voice Activity Detection switched off (on not "
           "supported)\n\n");
#endif
    printf("bitrate      : Codec bitrate in bps (only applies to vbr "
           "codecs)\n\n");

    return (0);
  }

  FILE* in_file = fopen(argv[1], "rb");
  CHECK_NOT_NULL(in_file);
  printf("Input file: %s\n", argv[1]);
  FILE* out_file = fopen(argv[2], "wb");
  CHECK_NOT_NULL(out_file);
  printf("Output file: %s\n\n", argv[2]);
  int packet_size_int = atoi(argv[3]);
  if (packet_size_int <= 0) {
     printf("Packet size %d must be positive", packet_size_int);
     return -1;
  }
  printf("Packet size: %d\n", packet_size_int);
  packet_size = static_cast<size_t>(packet_size_int);

  // check for stereo
  if (argv[4][strlen(argv[4]) - 1] == '*') {
    // use stereo
    usingStereo = true;
    numChannels = 2;
    argv[4][strlen(argv[4]) - 1] = '\0';
  }

  NetEQTest_GetCodec_and_PT(argv[4], &usedCodec, &payloadType, packet_size, &fs,
                            &bitrate, &useRed);

  if (useRed) {
    RTPheaderLen = 12 + 4 + 1; /* standard RTP = 12; 4 bytes per redundant
                                  payload, except last one which is 1 byte */
  }

  useVAD = atoi(argv[5]);
#if !(defined(CODEC_CNGCODEC8) || defined(CODEC_CNGCODEC16) || \
      defined(CODEC_CNGCODEC32) || defined(CODEC_CNGCODEC48))
  if (useVAD != 0) {
    printf("Error: this simulation does not support VAD/DTX/CNG\n");
  }
#endif

  // check stereo type
  if (usingStereo) {
    switch (usedCodec) {
      // sample based codecs
      case webrtc::NetEqDecoder::kDecoderPCMu:
      case webrtc::NetEqDecoder::kDecoderPCMa:
      case webrtc::NetEqDecoder::kDecoderG722: {
        // 1 octet per sample
        stereoMode = STEREO_MODE_SAMPLE_1;
        break;
      }
      case webrtc::NetEqDecoder::kDecoderPCM16B:
      case webrtc::NetEqDecoder::kDecoderPCM16Bwb:
      case webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz:
      case webrtc::NetEqDecoder::kDecoderPCM16Bswb48kHz: {
        // 2 octets per sample
        stereoMode = STEREO_MODE_SAMPLE_2;
        break;
      }

      // fixed-rate frame codecs (with internal VAD)
      default: {
        printf("Cannot use codec %s as stereo codec\n", argv[4]);
        exit(0);
      }
    }
  }

  if ((usedCodec == webrtc::NetEqDecoder::kDecoderISAC) ||
      (usedCodec == webrtc::NetEqDecoder::kDecoderISACswb)) {
    if (argc != 7) {
      if (usedCodec == webrtc::NetEqDecoder::kDecoderISAC) {
        bitrate = 32000;
        printf("Running iSAC at default bitrate of 32000 bps (to specify "
               "explicitly add the bps as last parameter)\n");
      } else  // (usedCodec==webrtc::kDecoderISACswb)
      {
        bitrate = 56000;
        printf("Running iSAC at default bitrate of 56000 bps (to specify "
               "explicitly add the bps as last parameter)\n");
      }
    } else {
      bitrate = atoi(argv[6]);
      if (usedCodec == webrtc::NetEqDecoder::kDecoderISAC) {
        if ((bitrate < 10000) || (bitrate > 32000)) {
          printf("Error: iSAC bitrate must be between 10000 and 32000 bps (%i "
                 "is invalid)\n", bitrate);
          exit(0);
        }
        printf("Running iSAC at bitrate of %i bps\n", bitrate);
      } else  // (usedCodec==webrtc::kDecoderISACswb)
      {
        if ((bitrate < 32000) || (bitrate > 56000)) {
          printf("Error: iSAC SWB bitrate must be between 32000 and 56000 bps "
                 "(%i is invalid)\n", bitrate);
          exit(0);
        }
      }
    }
  } else {
    if (argc == 7) {
      printf("Error: Bitrate parameter can only be specified for iSAC, G.723, "
             "and G.729.1\n");
      exit(0);
    }
  }

  if (useRed) {
    printf("Redundancy engaged. ");
  }
  printf("Used codec: %i\n", static_cast<int>(usedCodec));
  printf("Payload type: %i\n", payloadType);

  NetEQTest_init_coders(usedCodec, packet_size, bitrate, fs, useVAD,
                        numChannels);

  /* write file header */
  // fprintf(out_file, "#!RTPencode%s\n", "1.0");
  fprintf(out_file, "#!rtpplay%s \n",
          "1.0");               // this is the string that rtpplay needs
  uint32_t dummy_variable = 0;  // should be converted to network endian format,
                                // but does not matter when 0
  if (fwrite(&dummy_variable, 4, 1, out_file) != 1) {
    return -1;
  }
  if (fwrite(&dummy_variable, 4, 1, out_file) != 1) {
    return -1;
  }
  if (fwrite(&dummy_variable, 4, 1, out_file) != 1) {
    return -1;
  }
  if (fwrite(&dummy_variable, 2, 1, out_file) != 1) {
    return -1;
  }
  if (fwrite(&dummy_variable, 2, 1, out_file) != 1) {
    return -1;
  }

#ifdef TIMESTAMP_WRAPAROUND
  timestamp = 0xFFFFFFFF - fs * 10; /* should give wrap-around in 10 seconds */
#endif
#if defined(RANDOM_DATA) | defined(RANDOM_PAYLOAD_DATA)
  srand(RANDOM_SEED);
#endif

  /* if redundancy is used, the first redundant payload is zero length */
  red_len[0] = 0;

  /* read first frame */
  len = fread(org_data, 2, packet_size * numChannels, in_file) / numChannels;

  /* de-interleave if stereo */
  if (usingStereo) {
    stereoDeInterleave(org_data, len * numChannels);
  }

  while (len == packet_size) {
#ifdef INSERT_DTMF_PACKETS
    dtmfSent = false;

    if (sendtime >= NTone * DTMF_PACKET_INTERVAL) {
      if (sendtime < NTone * DTMF_PACKET_INTERVAL + DTMF_DURATION) {
        // tone has not ended
        if (DTMFfirst == 1) {
          DTMFtimestamp = timestamp;  // save this timestamp
          DTMFfirst = 0;
        }
        makeRTPheader(rtp_data, NETEQ_CODEC_AVT_PT, seqNo, DTMFtimestamp, ssrc);
        enc_len = makeDTMFpayload(
            &rtp_data[12], NTone % 12, 0, 4,
            (int)(sendtime - NTone * DTMF_PACKET_INTERVAL) * (fs / 1000) + len);
      } else {
        // tone has ended
        makeRTPheader(rtp_data, NETEQ_CODEC_AVT_PT, seqNo, DTMFtimestamp, ssrc);
        enc_len = makeDTMFpayload(&rtp_data[12], NTone % 12, 1, 4,
                                  DTMF_DURATION * (fs / 1000));
        NTone++;
        DTMFfirst = 1;
      }

      /* write RTP packet to file */
      length = htons(static_cast<unsigned short>(12 + enc_len + 8));
      plen = htons(static_cast<unsigned short>(12 + enc_len));
      offset = (uint32_t)sendtime;  //(timestamp/(fs/1000));
      offset = htonl(offset);
      if (fwrite(&length, 2, 1, out_file) != 1) {
        return -1;
      }
      if (fwrite(&plen, 2, 1, out_file) != 1) {
        return -1;
      }
      if (fwrite(&offset, 4, 1, out_file) != 1) {
        return -1;
      }
      if (fwrite(rtp_data, 12 + enc_len, 1, out_file) != 1) {
        return -1;
      }

      dtmfSent = true;
    }
#endif

#ifdef NO_DTMF_OVERDUB
    /* If DTMF is sent, we should not send any speech packets during the same
     * time */
    if (dtmfSent) {
      enc_len = 0;
    } else {
#endif
      /* encode frame */
      enc_len =
          NetEQTest_encode(usedCodec, org_data, packet_size, &rtp_data[12], fs,
                           &vad, useVAD, bitrate, numChannels);

      if (usingStereo && stereoMode != STEREO_MODE_FRAME && vad == 1) {
        // interleave the encoded payload for sample-based codecs (not for CNG)
        stereoInterleave(&rtp_data[12], enc_len, stereoMode);
      }
#ifdef NO_DTMF_OVERDUB
    }
#endif

    if (enc_len > 0 &&
        (sendtime <= STOPSENDTIME || sendtime > RESTARTSENDTIME)) {
      if (useRed) {
        if (red_len[0] > 0) {
          memmove(&rtp_data[RTPheaderLen + red_len[0]], &rtp_data[12], enc_len);
          memcpy(&rtp_data[RTPheaderLen], red_data, red_len[0]);

          red_len[1] = static_cast<uint16_t>(enc_len);
          red_TS[1] = timestamp;
          if (vad)
            red_PT[1] = payloadType;
          else
            red_PT[1] = NETEQ_CODEC_CN_PT;

          makeRedundantHeader(rtp_data, red_PT, 2, red_TS, red_len, seqNo++,
                              ssrc);

          enc_len += red_len[0] + RTPheaderLen - 12;
        } else {  // do not use redundancy payload for this packet, i.e., only
                  // last payload
          memmove(&rtp_data[RTPheaderLen - 4], &rtp_data[12], enc_len);
          // memcpy(&rtp_data[RTPheaderLen], red_data, red_len[0]);

          red_len[1] = static_cast<uint16_t>(enc_len);
          red_TS[1] = timestamp;
          if (vad)
            red_PT[1] = payloadType;
          else
            red_PT[1] = NETEQ_CODEC_CN_PT;

          makeRedundantHeader(rtp_data, red_PT, 2, red_TS, red_len, seqNo++,
                              ssrc);

          enc_len += red_len[0] + RTPheaderLen - 4 -
                     12;  // 4 is length of redundancy header (not used)
        }
      } else {
        /* make RTP header */
        if (vad)  // regular speech data
          makeRTPheader(rtp_data, payloadType, seqNo++, timestamp, ssrc);
        else  // CNG data
          makeRTPheader(rtp_data, NETEQ_CODEC_CN_PT, seqNo++, timestamp, ssrc);
      }
#ifdef MULTIPLE_SAME_TIMESTAMP
      int mult_pack = 0;
      do {
#endif  // MULTIPLE_SAME_TIMESTAMP
        /* write RTP packet to file */
        length = htons(static_cast<unsigned short>(12 + enc_len + 8));
        plen = htons(static_cast<unsigned short>(12 + enc_len));
        offset = (uint32_t)sendtime;
        //(timestamp/(fs/1000));
        offset = htonl(offset);
        if (fwrite(&length, 2, 1, out_file) != 1) {
          return -1;
        }
        if (fwrite(&plen, 2, 1, out_file) != 1) {
          return -1;
        }
        if (fwrite(&offset, 4, 1, out_file) != 1) {
          return -1;
        }
#ifdef RANDOM_DATA
        for (size_t k = 0; k < 12 + enc_len; k++) {
          rtp_data[k] = rand() + rand();
        }
#endif
#ifdef RANDOM_PAYLOAD_DATA
        for (size_t k = 12; k < 12 + enc_len; k++) {
          rtp_data[k] = rand() + rand();
        }
#endif
        if (fwrite(rtp_data, 12 + enc_len, 1, out_file) != 1) {
          return -1;
        }
#ifdef MULTIPLE_SAME_TIMESTAMP
      } while ((seqNo % REPEAT_PACKET_DISTANCE == 0) &&
               (mult_pack++ < REPEAT_PACKET_COUNT));
#endif  // MULTIPLE_SAME_TIMESTAMP

#ifdef INSERT_OLD_PACKETS
      if (packet_age >= OLD_PACKET * fs) {
        if (!first_old_packet) {
          // send the old packet
          if (fwrite(&old_length, 2, 1, out_file) != 1) {
            return -1;
          }
          if (fwrite(&old_plen, 2, 1, out_file) != 1) {
            return -1;
          }
          if (fwrite(&offset, 4, 1, out_file) != 1) {
            return -1;
          }
          if (fwrite(old_rtp_data, 12 + old_enc_len, 1, out_file) != 1) {
            return -1;
          }
        }
        // store current packet as old
        old_length = length;
        old_plen = plen;
        memcpy(old_rtp_data, rtp_data, 12 + enc_len);
        old_enc_len = enc_len;
        first_old_packet = 0;
        packet_age = 0;
      }
      packet_age += packet_size;
#endif

      if (useRed) {
/* move data to redundancy store */
#ifdef CODEC_ISAC
        if (usedCodec == webrtc::NetEqDecoder::kDecoderISAC) {
          assert(!usingStereo);  // Cannot handle stereo yet
          red_len[0] = WebRtcIsac_GetRedPayload(ISAC_inst[0], red_data);
        } else {
#endif
          memcpy(red_data, &rtp_data[RTPheaderLen + red_len[0]], enc_len);
          red_len[0] = red_len[1];
#ifdef CODEC_ISAC
        }
#endif
        red_TS[0] = red_TS[1];
        red_PT[0] = red_PT[1];
      }
    }

    /* read next frame */
    len = fread(org_data, 2, packet_size * numChannels, in_file) / numChannels;
    /* de-interleave if stereo */
    if (usingStereo) {
      stereoDeInterleave(org_data, len * numChannels);
    }

    if (payloadType == NETEQ_CODEC_G722_PT)
      timestamp += len >> 1;
    else
      timestamp += len;

    sendtime += (double)len / (fs / 1000);
  }

  NetEQTest_free_coders(usedCodec, numChannels);
  fclose(in_file);
  fclose(out_file);
  printf("Done!\n");

  return (0);
}

/****************/
/* Subfunctions */
/****************/

void NetEQTest_GetCodec_and_PT(char* name,
                               webrtc::NetEqDecoder* codec,
                               int* PT,
                               size_t frameLen,
                               int* fs,
                               int* bitrate,
                               int* useRed) {
  *bitrate = 0; /* Default bitrate setting */
  *useRed = 0;  /* Default no redundancy */

  if (!strcmp(name, "pcmu")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCMu;
    *PT = NETEQ_CODEC_PCMU_PT;
    *fs = 8000;
  } else if (!strcmp(name, "pcma")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCMa;
    *PT = NETEQ_CODEC_PCMA_PT;
    *fs = 8000;
  } else if (!strcmp(name, "pcm16b")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCM16B;
    *PT = NETEQ_CODEC_PCM16B_PT;
    *fs = 8000;
  } else if (!strcmp(name, "pcm16b_wb")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCM16Bwb;
    *PT = NETEQ_CODEC_PCM16B_WB_PT;
    *fs = 16000;
  } else if (!strcmp(name, "pcm16b_swb32")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz;
    *PT = NETEQ_CODEC_PCM16B_SWB32KHZ_PT;
    *fs = 32000;
  } else if (!strcmp(name, "pcm16b_swb48")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCM16Bswb48kHz;
    *PT = NETEQ_CODEC_PCM16B_SWB48KHZ_PT;
    *fs = 48000;
  } else if (!strcmp(name, "g722")) {
    *codec = webrtc::NetEqDecoder::kDecoderG722;
    *PT = NETEQ_CODEC_G722_PT;
    *fs = 16000;
  } else if ((!strcmp(name, "ilbc")) &&
             ((frameLen % 240 == 0) || (frameLen % 160 == 0))) {
    *fs = 8000;
    *codec = webrtc::NetEqDecoder::kDecoderILBC;
    *PT = NETEQ_CODEC_ILBC_PT;
  } else if (!strcmp(name, "isac")) {
    *fs = 16000;
    *codec = webrtc::NetEqDecoder::kDecoderISAC;
    *PT = NETEQ_CODEC_ISAC_PT;
  } else if (!strcmp(name, "isacswb")) {
    *fs = 32000;
    *codec = webrtc::NetEqDecoder::kDecoderISACswb;
    *PT = NETEQ_CODEC_ISACSWB_PT;
  } else if (!strcmp(name, "red_pcm")) {
    *codec = webrtc::NetEqDecoder::kDecoderPCMa;
    *PT = NETEQ_CODEC_PCMA_PT; /* this will be the PT for the sub-headers */
    *fs = 8000;
    *useRed = 1;
  } else if (!strcmp(name, "red_isac")) {
    *codec = webrtc::NetEqDecoder::kDecoderISAC;
    *PT = NETEQ_CODEC_ISAC_PT; /* this will be the PT for the sub-headers */
    *fs = 16000;
    *useRed = 1;
  } else if (!strcmp(name, "opus")) {
    *codec = webrtc::NetEqDecoder::kDecoderOpus;
    *PT = NETEQ_CODEC_OPUS_PT; /* this will be the PT for the sub-headers */
    *fs = 48000;
  } else {
    printf("Error: Not a supported codec (%s)\n", name);
    exit(0);
  }
}

int NetEQTest_init_coders(webrtc::NetEqDecoder coder,
                          size_t enc_frameSize,
                          int bitrate,
                          int sampfreq,
                          int vad,
                          size_t numChannels) {
  int ok = 0;

  for (size_t k = 0; k < numChannels; k++) {
    VAD_inst[k] = WebRtcVad_Create();
    if (!VAD_inst[k]) {
      printf("Error: Couldn't allocate memory for VAD instance\n");
      exit(0);
    }
    ok = WebRtcVad_Init(VAD_inst[k]);
    if (ok == -1) {
      printf("Error: Initialization of VAD struct failed\n");
      exit(0);
    }

#if (defined(CODEC_CNGCODEC8) || defined(CODEC_CNGCODEC16) || \
     defined(CODEC_CNGCODEC32) || defined(CODEC_CNGCODEC48))
    if (sampfreq <= 16000) {
      CNG_encoder[k] = new webrtc::ComfortNoiseEncoder(sampfreq, 200, 5);
    }
#endif

    switch (coder) {
#ifdef CODEC_PCM16B
      case webrtc::NetEqDecoder::kDecoderPCM16B:
#endif
#ifdef CODEC_PCM16B_WB
      case webrtc::NetEqDecoder::kDecoderPCM16Bwb:
#endif
#ifdef CODEC_PCM16B_32KHZ
      case webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz:
#endif
#ifdef CODEC_PCM16B_48KHZ
      case webrtc::NetEqDecoder::kDecoderPCM16Bswb48kHz:
#endif
#ifdef CODEC_G711
      case webrtc::NetEqDecoder::kDecoderPCMu:
      case webrtc::NetEqDecoder::kDecoderPCMa:
#endif
        // do nothing
        break;
#ifdef CODEC_G729
      case webrtc::kDecoderG729:
        if (sampfreq == 8000) {
          if ((enc_frameSize == 80) || (enc_frameSize == 160) ||
              (enc_frameSize == 240) || (enc_frameSize == 320) ||
              (enc_frameSize == 400) || (enc_frameSize == 480)) {
            ok = WebRtcG729_CreateEnc(&G729enc_inst[k]);
            if (ok != 0) {
              printf("Error: Couldn't allocate memory for G729 encoding "
                     "instance\n");
              exit(0);
            }
          } else {
            printf("\nError: g729 only supports 10, 20, 30, 40, 50 or 60 "
                   "ms!!\n\n");
            exit(0);
          }
          WebRtcG729_EncoderInit(G729enc_inst[k], vad);
          if ((vad == 1) && (enc_frameSize != 80)) {
            printf("\nError - This simulation only supports VAD for G729 at "
                   "10ms packets (not %" PRIuS "ms)\n", (enc_frameSize >> 3));
          }
        } else {
          printf("\nError - g729 is only developed for 8kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G729_1
      case webrtc::kDecoderG729_1:
        if (sampfreq == 16000) {
          if ((enc_frameSize == 320) || (enc_frameSize == 640) ||
              (enc_frameSize == 960)) {
            ok = WebRtcG7291_Create(&G729_1_inst[k]);
            if (ok != 0) {
              printf("Error: Couldn't allocate memory for G.729.1 codec "
                     "instance\n");
              exit(0);
            }
          } else {
            printf("\nError: G.729.1 only supports 20, 40 or 60 ms!!\n\n");
            exit(0);
          }
          if (!(((bitrate >= 12000) && (bitrate <= 32000) &&
                 (bitrate % 2000 == 0)) ||
                (bitrate == 8000))) {
            /* must be 8, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, or 32 kbps */
            printf("\nError: G.729.1 bitrate must be 8000 or 12000--32000 in "
                   "steps of 2000 bps\n");
            exit(0);
          }
          WebRtcG7291_EncoderInit(G729_1_inst[k], bitrate, 0 /* flag8kHz*/,
                                  0 /*flagG729mode*/);
        } else {
          printf("\nError - G.729.1 input is always 16 kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_SPEEX_8
      case webrtc::kDecoderSPEEX_8:
        if (sampfreq == 8000) {
          if ((enc_frameSize == 160) || (enc_frameSize == 320) ||
              (enc_frameSize == 480)) {
            ok = WebRtcSpeex_CreateEnc(&SPEEX8enc_inst[k], sampfreq);
            if (ok != 0) {
              printf("Error: Couldn't allocate memory for Speex encoding "
                     "instance\n");
              exit(0);
            }
          } else {
            printf("\nError: Speex only supports 20, 40, and 60 ms!!\n\n");
            exit(0);
          }
          if ((vad == 1) && (enc_frameSize != 160)) {
            printf("\nError - This simulation only supports VAD for Speex at "
                   "20ms packets (not %" PRIuS "ms)\n",
                (enc_frameSize >> 3));
            vad = 0;
          }
          ok = WebRtcSpeex_EncoderInit(SPEEX8enc_inst[k], 0 /*vbr*/,
                                       3 /*complexity*/, vad);
          if (ok != 0)
            exit(0);
        } else {
          printf("\nError - Speex8 called with sample frequency other than 8 "
                 "kHz.\n\n");
        }
        break;
#endif
#ifdef CODEC_SPEEX_16
      case webrtc::kDecoderSPEEX_16:
        if (sampfreq == 16000) {
          if ((enc_frameSize == 320) || (enc_frameSize == 640) ||
              (enc_frameSize == 960)) {
            ok = WebRtcSpeex_CreateEnc(&SPEEX16enc_inst[k], sampfreq);
            if (ok != 0) {
              printf("Error: Couldn't allocate memory for Speex encoding "
                     "instance\n");
              exit(0);
            }
          } else {
            printf("\nError: Speex only supports 20, 40, and 60 ms!!\n\n");
            exit(0);
          }
          if ((vad == 1) && (enc_frameSize != 320)) {
            printf("\nError - This simulation only supports VAD for Speex at "
                   "20ms packets (not %" PRIuS "ms)\n",
                (enc_frameSize >> 4));
            vad = 0;
          }
          ok = WebRtcSpeex_EncoderInit(SPEEX16enc_inst[k], 0 /*vbr*/,
                                       3 /*complexity*/, vad);
          if (ok != 0)
            exit(0);
        } else {
          printf("\nError - Speex16 called with sample frequency other than 16 "
                 "kHz.\n\n");
        }
        break;
#endif

#ifdef CODEC_G722_1_16
      case webrtc::kDecoderG722_1_16:
        if (sampfreq == 16000) {
          ok = WebRtcG7221_CreateEnc16(&G722_1_16enc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for G.722.1 instance\n");
            exit(0);
          }
          if (enc_frameSize == 320) {
          } else {
            printf("\nError: G722.1 only supports 20 ms!!\n\n");
            exit(0);
          }
          WebRtcG7221_EncoderInit16((G722_1_16_encinst_t*)G722_1_16enc_inst[k]);
        } else {
          printf("\nError - G722.1 is only developed for 16kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G722_1_24
      case webrtc::kDecoderG722_1_24:
        if (sampfreq == 16000) {
          ok = WebRtcG7221_CreateEnc24(&G722_1_24enc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for G.722.1 instance\n");
            exit(0);
          }
          if (enc_frameSize == 320) {
          } else {
            printf("\nError: G722.1 only supports 20 ms!!\n\n");
            exit(0);
          }
          WebRtcG7221_EncoderInit24((G722_1_24_encinst_t*)G722_1_24enc_inst[k]);
        } else {
          printf("\nError - G722.1 is only developed for 16kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G722_1_32
      case webrtc::kDecoderG722_1_32:
        if (sampfreq == 16000) {
          ok = WebRtcG7221_CreateEnc32(&G722_1_32enc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for G.722.1 instance\n");
            exit(0);
          }
          if (enc_frameSize == 320) {
          } else {
            printf("\nError: G722.1 only supports 20 ms!!\n\n");
            exit(0);
          }
          WebRtcG7221_EncoderInit32((G722_1_32_encinst_t*)G722_1_32enc_inst[k]);
        } else {
          printf("\nError - G722.1 is only developed for 16kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G722_1C_24
      case webrtc::kDecoderG722_1C_24:
        if (sampfreq == 32000) {
          ok = WebRtcG7221C_CreateEnc24(&G722_1C_24enc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for G.722.1C instance\n");
            exit(0);
          }
          if (enc_frameSize == 640) {
          } else {
            printf("\nError: G722.1 C only supports 20 ms!!\n\n");
            exit(0);
          }
          WebRtcG7221C_EncoderInit24(
              (G722_1C_24_encinst_t*)G722_1C_24enc_inst[k]);
        } else {
          printf("\nError - G722.1 C is only developed for 32kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G722_1C_32
      case webrtc::kDecoderG722_1C_32:
        if (sampfreq == 32000) {
          ok = WebRtcG7221C_CreateEnc32(&G722_1C_32enc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for G.722.1C instance\n");
            exit(0);
          }
          if (enc_frameSize == 640) {
          } else {
            printf("\nError: G722.1 C only supports 20 ms!!\n\n");
            exit(0);
          }
          WebRtcG7221C_EncoderInit32(
              (G722_1C_32_encinst_t*)G722_1C_32enc_inst[k]);
        } else {
          printf("\nError - G722.1 C is only developed for 32kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G722_1C_48
      case webrtc::kDecoderG722_1C_48:
        if (sampfreq == 32000) {
          ok = WebRtcG7221C_CreateEnc48(&G722_1C_48enc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for G.722.1C instance\n");
            exit(0);
          }
          if (enc_frameSize == 640) {
          } else {
            printf("\nError: G722.1 C only supports 20 ms!!\n\n");
            exit(0);
          }
          WebRtcG7221C_EncoderInit48(
              (G722_1C_48_encinst_t*)G722_1C_48enc_inst[k]);
        } else {
          printf("\nError - G722.1 C is only developed for 32kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_G722
      case webrtc::NetEqDecoder::kDecoderG722:
        if (sampfreq == 16000) {
          if (enc_frameSize % 2 == 0) {
          } else {
            printf(
                "\nError - g722 frames must have an even number of "
                "enc_frameSize\n");
            exit(0);
          }
          WebRtcG722_CreateEncoder(&g722EncState[k]);
          WebRtcG722_EncoderInit(g722EncState[k]);
        } else {
          printf("\nError - g722 is only developed for 16kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_AMR
      case webrtc::kDecoderAMR:
        if (sampfreq == 8000) {
          ok = WebRtcAmr_CreateEnc(&AMRenc_inst[k]);
          if (ok != 0) {
            printf(
                "Error: Couldn't allocate memory for AMR encoding instance\n");
            exit(0);
          }
          if ((enc_frameSize == 160) || (enc_frameSize == 320) ||
              (enc_frameSize == 480)) {
          } else {
            printf("\nError - AMR must have a multiple of 160 enc_frameSize\n");
            exit(0);
          }
          WebRtcAmr_EncoderInit(AMRenc_inst[k], vad);
          WebRtcAmr_EncodeBitmode(AMRenc_inst[k], AMRBandwidthEfficient);
          AMR_bitrate = bitrate;
        } else {
          printf("\nError - AMR is only developed for 8kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_AMRWB
      case webrtc::kDecoderAMRWB:
        if (sampfreq == 16000) {
          ok = WebRtcAmrWb_CreateEnc(&AMRWBenc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for AMRWB encoding "
                   "instance\n");
            exit(0);
          }
          if (((enc_frameSize / 320) > 3) || ((enc_frameSize % 320) != 0)) {
            printf("\nError - AMRwb must have frameSize of 20, 40 or 60ms\n");
            exit(0);
          }
          WebRtcAmrWb_EncoderInit(AMRWBenc_inst[k], vad);
          if (bitrate == 7000) {
            AMRWB_bitrate = AMRWB_MODE_7k;
          } else if (bitrate == 9000) {
            AMRWB_bitrate = AMRWB_MODE_9k;
          } else if (bitrate == 12000) {
            AMRWB_bitrate = AMRWB_MODE_12k;
          } else if (bitrate == 14000) {
            AMRWB_bitrate = AMRWB_MODE_14k;
          } else if (bitrate == 16000) {
            AMRWB_bitrate = AMRWB_MODE_16k;
          } else if (bitrate == 18000) {
            AMRWB_bitrate = AMRWB_MODE_18k;
          } else if (bitrate == 20000) {
            AMRWB_bitrate = AMRWB_MODE_20k;
          } else if (bitrate == 23000) {
            AMRWB_bitrate = AMRWB_MODE_23k;
          } else if (bitrate == 24000) {
            AMRWB_bitrate = AMRWB_MODE_24k;
          }
          WebRtcAmrWb_EncodeBitmode(AMRWBenc_inst[k], AMRBandwidthEfficient);

        } else {
          printf("\nError - AMRwb is only developed for 16kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_ILBC
      case webrtc::NetEqDecoder::kDecoderILBC:
        if (sampfreq == 8000) {
          ok = WebRtcIlbcfix_EncoderCreate(&iLBCenc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for iLBC encoding "
                   "instance\n");
            exit(0);
          }
          if ((enc_frameSize == 160) || (enc_frameSize == 240) ||
              (enc_frameSize == 320) || (enc_frameSize == 480)) {
          } else {
            printf("\nError - iLBC only supports 160, 240, 320 and 480 "
                   "enc_frameSize (20, 30, 40 and 60 ms)\n");
            exit(0);
          }
          if ((enc_frameSize == 160) || (enc_frameSize == 320)) {
            /* 20 ms version */
            WebRtcIlbcfix_EncoderInit(iLBCenc_inst[k], 20);
          } else {
            /* 30 ms version */
            WebRtcIlbcfix_EncoderInit(iLBCenc_inst[k], 30);
          }
        } else {
          printf("\nError - iLBC is only developed for 8kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_ISAC
      case webrtc::NetEqDecoder::kDecoderISAC:
        if (sampfreq == 16000) {
          ok = WebRtcIsac_Create(&ISAC_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for iSAC instance\n");
            exit(0);
          }
          if ((enc_frameSize == 480) || (enc_frameSize == 960)) {
          } else {
            printf("\nError - iSAC only supports frameSize (30 and 60 ms)\n");
            exit(0);
          }
          WebRtcIsac_EncoderInit(ISAC_inst[k], 1);
          if ((bitrate < 10000) || (bitrate > 32000)) {
            printf("\nError - iSAC bitrate has to be between 10000 and 32000 "
                   "bps (not %i)\n",
                bitrate);
            exit(0);
          }
          WebRtcIsac_Control(ISAC_inst[k], bitrate,
                             static_cast<int>(enc_frameSize >> 4));
        } else {
          printf("\nError - iSAC only supports 480 or 960 enc_frameSize (30 or "
                 "60 ms)\n");
          exit(0);
        }
        break;
#endif
#ifdef NETEQ_ISACFIX_CODEC
      case webrtc::kDecoderISAC:
        if (sampfreq == 16000) {
          ok = WebRtcIsacfix_Create(&ISAC_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for iSAC instance\n");
            exit(0);
          }
          if ((enc_frameSize == 480) || (enc_frameSize == 960)) {
          } else {
            printf("\nError - iSAC only supports frameSize (30 and 60 ms)\n");
            exit(0);
          }
          WebRtcIsacfix_EncoderInit(ISAC_inst[k], 1);
          if ((bitrate < 10000) || (bitrate > 32000)) {
            printf("\nError - iSAC bitrate has to be between 10000 and 32000 "
                   "bps (not %i)\n", bitrate);
            exit(0);
          }
          WebRtcIsacfix_Control(ISAC_inst[k], bitrate, enc_frameSize >> 4);
        } else {
          printf("\nError - iSAC only supports 480 or 960 enc_frameSize (30 or "
                 "60 ms)\n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_ISAC_SWB
      case webrtc::NetEqDecoder::kDecoderISACswb:
        if (sampfreq == 32000) {
          ok = WebRtcIsac_Create(&ISACSWB_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for iSAC SWB instance\n");
            exit(0);
          }
          if (enc_frameSize == 960) {
          } else {
            printf("\nError - iSAC SWB only supports frameSize 30 ms\n");
            exit(0);
          }
          ok = WebRtcIsac_SetEncSampRate(ISACSWB_inst[k], 32000);
          if (ok != 0) {
            printf("Error: Couldn't set sample rate for iSAC SWB instance\n");
            exit(0);
          }
          WebRtcIsac_EncoderInit(ISACSWB_inst[k], 1);
          if ((bitrate < 32000) || (bitrate > 56000)) {
            printf("\nError - iSAC SWB bitrate has to be between 32000 and "
                   "56000 bps (not %i)\n", bitrate);
            exit(0);
          }
          WebRtcIsac_Control(ISACSWB_inst[k], bitrate,
                             static_cast<int>(enc_frameSize >> 5));
        } else {
          printf("\nError - iSAC SWB only supports 960 enc_frameSize (30 "
                 "ms)\n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_GSMFR
      case webrtc::kDecoderGSMFR:
        if (sampfreq == 8000) {
          ok = WebRtcGSMFR_CreateEnc(&GSMFRenc_inst[k]);
          if (ok != 0) {
            printf("Error: Couldn't allocate memory for GSM FR encoding "
                   "instance\n");
            exit(0);
          }
          if ((enc_frameSize == 160) || (enc_frameSize == 320) ||
              (enc_frameSize == 480)) {
          } else {
            printf("\nError - GSM FR must have a multiple of 160 "
                   "enc_frameSize\n");
            exit(0);
          }
          WebRtcGSMFR_EncoderInit(GSMFRenc_inst[k], 0);
        } else {
          printf("\nError - GSM FR is only developed for 8kHz \n");
          exit(0);
        }
        break;
#endif
#ifdef CODEC_OPUS
      case webrtc::NetEqDecoder::kDecoderOpus:
        ok = WebRtcOpus_EncoderCreate(&opus_inst[k], 1, 0);
        if (ok != 0) {
          printf("Error: Couldn't allocate memory for Opus encoding "
                 "instance\n");
          exit(0);
        }
        WebRtcOpus_EnableFec(opus_inst[k]);
        WebRtcOpus_SetPacketLossRate(opus_inst[k], 5);
        break;
#endif
      default:
        printf("Error: unknown codec in call to NetEQTest_init_coders.\n");
        exit(0);
        break;
    }
    if (ok != 0) {
      return (ok);
    }
  }  // end for

  return (0);
}

int NetEQTest_free_coders(webrtc::NetEqDecoder coder, size_t numChannels) {
  for (size_t k = 0; k < numChannels; k++) {
    WebRtcVad_Free(VAD_inst[k]);
#if (defined(CODEC_CNGCODEC8) || defined(CODEC_CNGCODEC16) || \
     defined(CODEC_CNGCODEC32) || defined(CODEC_CNGCODEC48))
    delete CNG_encoder[k];
    CNG_encoder[k] = nullptr;
#endif

    switch (coder) {
#ifdef CODEC_PCM16B
      case webrtc::NetEqDecoder::kDecoderPCM16B:
#endif
#ifdef CODEC_PCM16B_WB
      case webrtc::NetEqDecoder::kDecoderPCM16Bwb:
#endif
#ifdef CODEC_PCM16B_32KHZ
      case webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz:
#endif
#ifdef CODEC_PCM16B_48KHZ
      case webrtc::NetEqDecoder::kDecoderPCM16Bswb48kHz:
#endif
#ifdef CODEC_G711
      case webrtc::NetEqDecoder::kDecoderPCMu:
      case webrtc::NetEqDecoder::kDecoderPCMa:
#endif
        // do nothing
        break;
#ifdef CODEC_G729
      case webrtc::NetEqDecoder::kDecoderG729:
        WebRtcG729_FreeEnc(G729enc_inst[k]);
        break;
#endif
#ifdef CODEC_G729_1
      case webrtc::NetEqDecoder::kDecoderG729_1:
        WebRtcG7291_Free(G729_1_inst[k]);
        break;
#endif
#ifdef CODEC_SPEEX_8
      case webrtc::NetEqDecoder::kDecoderSPEEX_8:
        WebRtcSpeex_FreeEnc(SPEEX8enc_inst[k]);
        break;
#endif
#ifdef CODEC_SPEEX_16
      case webrtc::NetEqDecoder::kDecoderSPEEX_16:
        WebRtcSpeex_FreeEnc(SPEEX16enc_inst[k]);
        break;
#endif

#ifdef CODEC_G722_1_16
      case webrtc::NetEqDecoder::kDecoderG722_1_16:
        WebRtcG7221_FreeEnc16(G722_1_16enc_inst[k]);
        break;
#endif
#ifdef CODEC_G722_1_24
      case webrtc::NetEqDecoder::kDecoderG722_1_24:
        WebRtcG7221_FreeEnc24(G722_1_24enc_inst[k]);
        break;
#endif
#ifdef CODEC_G722_1_32
      case webrtc::NetEqDecoder::kDecoderG722_1_32:
        WebRtcG7221_FreeEnc32(G722_1_32enc_inst[k]);
        break;
#endif
#ifdef CODEC_G722_1C_24
      case webrtc::NetEqDecoder::kDecoderG722_1C_24:
        WebRtcG7221C_FreeEnc24(G722_1C_24enc_inst[k]);
        break;
#endif
#ifdef CODEC_G722_1C_32
      case webrtc::NetEqDecoder::kDecoderG722_1C_32:
        WebRtcG7221C_FreeEnc32(G722_1C_32enc_inst[k]);
        break;
#endif
#ifdef CODEC_G722_1C_48
      case webrtc::NetEqDecoder::kDecoderG722_1C_48:
        WebRtcG7221C_FreeEnc48(G722_1C_48enc_inst[k]);
        break;
#endif
#ifdef CODEC_G722
      case webrtc::NetEqDecoder::kDecoderG722:
        WebRtcG722_FreeEncoder(g722EncState[k]);
        break;
#endif
#ifdef CODEC_AMR
      case webrtc::NetEqDecoder::kDecoderAMR:
        WebRtcAmr_FreeEnc(AMRenc_inst[k]);
        break;
#endif
#ifdef CODEC_AMRWB
      case webrtc::NetEqDecoder::kDecoderAMRWB:
        WebRtcAmrWb_FreeEnc(AMRWBenc_inst[k]);
        break;
#endif
#ifdef CODEC_ILBC
      case webrtc::NetEqDecoder::kDecoderILBC:
        WebRtcIlbcfix_EncoderFree(iLBCenc_inst[k]);
        break;
#endif
#ifdef CODEC_ISAC
      case webrtc::NetEqDecoder::kDecoderISAC:
        WebRtcIsac_Free(ISAC_inst[k]);
        break;
#endif
#ifdef NETEQ_ISACFIX_CODEC
      case webrtc::NetEqDecoder::kDecoderISAC:
        WebRtcIsacfix_Free(ISAC_inst[k]);
        break;
#endif
#ifdef CODEC_ISAC_SWB
      case webrtc::NetEqDecoder::kDecoderISACswb:
        WebRtcIsac_Free(ISACSWB_inst[k]);
        break;
#endif
#ifdef CODEC_GSMFR
      case webrtc::NetEqDecoder::kDecoderGSMFR:
        WebRtcGSMFR_FreeEnc(GSMFRenc_inst[k]);
        break;
#endif
#ifdef CODEC_OPUS
      case webrtc::NetEqDecoder::kDecoderOpus:
        WebRtcOpus_EncoderFree(opus_inst[k]);
        break;
#endif
      default:
        printf("Error: unknown codec in call to NetEQTest_init_coders.\n");
        exit(0);
        break;
    }
  }

  return (0);
}

size_t NetEQTest_encode(webrtc::NetEqDecoder coder,
                        int16_t* indata,
                        size_t frameLen,
                        unsigned char* encoded,
                        int sampleRate,
                        int* vad,
                        int useVAD,
                        int bitrate,
                        size_t numChannels) {
  size_t cdlen = 0;
  int16_t* tempdata;
  static bool first_cng = true;
  size_t tempLen;
  *vad = 1;

  // check VAD first
  if (useVAD) {
    *vad = 0;

    const size_t sampleRate_10 = static_cast<size_t>(10 * sampleRate / 1000);
    const size_t sampleRate_20 = static_cast<size_t>(20 * sampleRate / 1000);
    const size_t sampleRate_30 = static_cast<size_t>(30 * sampleRate / 1000);
    for (size_t k = 0; k < numChannels; k++) {
      tempLen = frameLen;
      tempdata = &indata[k * frameLen];
      int localVad = 0;
      /* Partition the signal and test each chunk for VAD.
         All chunks must be VAD=0 to produce a total VAD=0. */
      while (tempLen >= sampleRate_10) {
        if ((tempLen % sampleRate_30) == 0) {  // tempLen is multiple of 30ms
          localVad |= WebRtcVad_Process(VAD_inst[k], sampleRate, tempdata,
                                        sampleRate_30);
          tempdata += sampleRate_30;
          tempLen -= sampleRate_30;
        } else if (tempLen >= sampleRate_20) {  // tempLen >= 20ms
          localVad |= WebRtcVad_Process(VAD_inst[k], sampleRate, tempdata,
                                        sampleRate_20);
          tempdata += sampleRate_20;
          tempLen -= sampleRate_20;
        } else {  // use 10ms
          localVad |= WebRtcVad_Process(VAD_inst[k], sampleRate, tempdata,
                                        sampleRate_10);
          tempdata += sampleRate_10;
          tempLen -= sampleRate_10;
        }
      }

      // aggregate all VAD decisions over all channels
      *vad |= localVad;
    }

    if (!*vad) {
      // all channels are silent
      rtc::Buffer workaround;
      cdlen = 0;
      for (size_t k = 0; k < numChannels; k++) {
        workaround.Clear();
        tempLen = CNG_encoder[k]->Encode(
            rtc::ArrayView<const int16_t>(
                &indata[k * frameLen],
                (frameLen <= 640 ? frameLen : 640) /* max 640 */),
            first_cng,
            &workaround);
        memcpy(encoded, workaround.data(), tempLen);
        encoded += tempLen;
        cdlen += tempLen;
      }
      *vad = 0;
      first_cng = false;
      return (cdlen);
    }
  }

  // loop over all channels
  size_t totalLen = 0;

  for (size_t k = 0; k < numChannels; k++) {
    /* Encode with the selected coder type */
    if (coder == webrtc::NetEqDecoder::kDecoderPCMu) { /*g711 u-law */
#ifdef CODEC_G711
      cdlen = WebRtcG711_EncodeU(indata, frameLen, encoded);
#endif
    } else if (coder == webrtc::NetEqDecoder::kDecoderPCMa) { /*g711 A-law */
#ifdef CODEC_G711
      cdlen = WebRtcG711_EncodeA(indata, frameLen, encoded);
    }
#endif
#ifdef CODEC_PCM16B
    else if ((coder == webrtc::NetEqDecoder::kDecoderPCM16B) ||
             (coder == webrtc::NetEqDecoder::kDecoderPCM16Bwb) ||
             (coder == webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz) ||
             (coder == webrtc::NetEqDecoder::
                           kDecoderPCM16Bswb48kHz)) { /*pcm16b (8kHz, 16kHz,
                                                        32kHz or 48kHz) */
      cdlen = WebRtcPcm16b_Encode(indata, frameLen, encoded);
    }
#endif
#ifdef CODEC_G722
    else if (coder == webrtc::NetEqDecoder::kDecoderG722) { /*g722 */
      cdlen = WebRtcG722_Encode(g722EncState[k], indata, frameLen, encoded);
      assert(cdlen == frameLen >> 1);
    }
#endif
#ifdef CODEC_ILBC
    else if (coder == webrtc::NetEqDecoder::kDecoderILBC) { /*iLBC */
      cdlen = static_cast<size_t>(std::max(
          WebRtcIlbcfix_Encode(iLBCenc_inst[k], indata, frameLen, encoded), 0));
    }
#endif
#if (defined(CODEC_ISAC) || \
     defined(NETEQ_ISACFIX_CODEC))            // TODO(hlundin): remove all
                                              // NETEQ_ISACFIX_CODEC
    else if (coder == webrtc::NetEqDecoder::kDecoderISAC) { /*iSAC */
      int noOfCalls = 0;
      int res = 0;
      while (res <= 0) {
#ifdef CODEC_ISAC /* floating point */
        res =
            WebRtcIsac_Encode(ISAC_inst[k], &indata[noOfCalls * 160], encoded);
#else /* fixed point */
        res = WebRtcIsacfix_Encode(ISAC_inst[k], &indata[noOfCalls * 160],
                                   encoded);
#endif
        noOfCalls++;
      }
      cdlen = static_cast<size_t>(res);
    }
#endif
#ifdef CODEC_ISAC_SWB
    else if (coder == webrtc::NetEqDecoder::kDecoderISACswb) { /* iSAC SWB */
      int noOfCalls = 0;
      int res = 0;
      while (res <= 0) {
        res = WebRtcIsac_Encode(ISACSWB_inst[k], &indata[noOfCalls * 320],
                                encoded);
        noOfCalls++;
      }
      cdlen = static_cast<size_t>(res);
    }
#endif
#ifdef CODEC_OPUS
    cdlen = WebRtcOpus_Encode(opus_inst[k], indata, frameLen, kRtpDataSize - 12,
                              encoded);
    RTC_CHECK_GT(cdlen, 0u);
#endif
    indata += frameLen;
    encoded += cdlen;
    totalLen += cdlen;

  }  // end for

  first_cng = true;
  return (totalLen);
}

void makeRTPheader(unsigned char* rtp_data,
                   int payloadType,
                   int seqNo,
                   uint32_t timestamp,
                   uint32_t ssrc) {
  rtp_data[0] = 0x80;
  rtp_data[1] = payloadType & 0xFF;
  rtp_data[2] = (seqNo >> 8) & 0xFF;
  rtp_data[3] = seqNo & 0xFF;
  rtp_data[4] = timestamp >> 24;
  rtp_data[5] = (timestamp >> 16) & 0xFF;
  rtp_data[6] = (timestamp >> 8) & 0xFF;
  rtp_data[7] = timestamp & 0xFF;
  rtp_data[8] = ssrc >> 24;
  rtp_data[9] = (ssrc >> 16) & 0xFF;
  rtp_data[10] = (ssrc >> 8) & 0xFF;
  rtp_data[11] = ssrc & 0xFF;
}

int makeRedundantHeader(unsigned char* rtp_data,
                        int* payloadType,
                        int numPayloads,
                        uint32_t* timestamp,
                        uint16_t* blockLen,
                        int seqNo,
                        uint32_t ssrc) {
  int i;
  unsigned char* rtpPointer;
  uint16_t offset;

  /* first create "standard" RTP header */
  makeRTPheader(rtp_data, NETEQ_CODEC_RED_PT, seqNo, timestamp[numPayloads - 1],
                ssrc);

  rtpPointer = &rtp_data[12];

  /* add one sub-header for each redundant payload (not the primary) */
  for (i = 0; i < numPayloads - 1; i++) {
    if (blockLen[i] > 0) {
      offset = static_cast<uint16_t>(timestamp[numPayloads - 1] - timestamp[i]);

      // Byte |0|       |1       2     |  3       |
      // Bit  |0|1234567|01234567012345|6701234567|
      //      |F|payload|   timestamp  |   block  |
      //      | |  type |    offset    |  length  |
      rtpPointer[0] = (payloadType[i] & 0x7F) | 0x80;
      rtpPointer[1] = (offset >> 6) & 0xFF;
      rtpPointer[2] = ((offset & 0x3F) << 2) | ((blockLen[i] >> 8) & 0x03);
      rtpPointer[3] = blockLen[i] & 0xFF;

      rtpPointer += 4;
    }
  }

  // Bit  |0|1234567|
  //      |0|payload|
  //      | |  type |
  rtpPointer[0] = payloadType[numPayloads - 1] & 0x7F;
  ++rtpPointer;

  return rtpPointer - rtp_data;  // length of header in bytes
}

size_t makeDTMFpayload(unsigned char* payload_data,
                       int Event,
                       int End,
                       int Volume,
                       int Duration) {
  unsigned char E, R, V;
  R = 0;
  V = (unsigned char)Volume;
  if (End == 0) {
    E = 0x00;
  } else {
    E = 0x80;
  }
  payload_data[0] = (unsigned char)Event;
  payload_data[1] = (unsigned char)(E | R | V);
  // Duration equals 8 times time_ms, default is 8000 Hz.
  payload_data[2] = (unsigned char)((Duration >> 8) & 0xFF);
  payload_data[3] = (unsigned char)(Duration & 0xFF);
  return (4);
}

void stereoDeInterleave(int16_t* audioSamples, size_t numSamples) {
  int16_t* tempVec;
  int16_t* readPtr, *writeL, *writeR;

  if (numSamples == 0)
    return;

  tempVec = (int16_t*)malloc(sizeof(int16_t) * numSamples);
  if (tempVec == NULL) {
    printf("Error allocating memory\n");
    exit(0);
  }

  memcpy(tempVec, audioSamples, numSamples * sizeof(int16_t));

  writeL = audioSamples;
  writeR = &audioSamples[numSamples / 2];
  readPtr = tempVec;

  for (size_t k = 0; k < numSamples; k += 2) {
    *writeL = *readPtr;
    readPtr++;
    *writeR = *readPtr;
    readPtr++;
    writeL++;
    writeR++;
  }

  free(tempVec);
}

void stereoInterleave(unsigned char* data, size_t dataLen, size_t stride) {
  unsigned char* ptrL, *ptrR;
  unsigned char temp[10];

  if (stride > 10) {
    exit(0);
  }

  if (dataLen % 1 != 0) {
    // must be even number of samples
    printf("Error: cannot interleave odd sample number\n");
    exit(0);
  }

  ptrL = data + stride;
  ptrR = &data[dataLen / 2];

  while (ptrL < ptrR) {
    // copy from right pointer to temp
    memcpy(temp, ptrR, stride);

    // shift data between pointers
    memmove(ptrL + stride, ptrL, ptrR - ptrL);

    // copy from temp to left pointer
    memcpy(ptrL, temp, stride);

    // advance pointers
    ptrL += stride * 2;
    ptrR += stride;
  }
}
