/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/Channel.h"

#include <assert.h>
#include <iostream>

#include "webrtc/base/format_macros.h"
#include "webrtc/base/timeutils.h"

namespace webrtc {

int32_t Channel::SendData(FrameType frameType,
                          uint8_t payloadType,
                          uint32_t timeStamp,
                          const uint8_t* payloadData,
                          size_t payloadSize,
                          const RTPFragmentationHeader* fragmentation) {
  WebRtcRTPHeader rtpInfo;
  int32_t status;
  size_t payloadDataSize = payloadSize;

  rtpInfo.header.markerBit = false;
  rtpInfo.header.ssrc = 0;
  rtpInfo.header.sequenceNumber = (external_sequence_number_ < 0) ?
      _seqNo++ : static_cast<uint16_t>(external_sequence_number_);
  rtpInfo.header.payloadType = payloadType;
  rtpInfo.header.timestamp = (external_send_timestamp_ < 0) ? timeStamp :
      static_cast<uint32_t>(external_send_timestamp_);

  if (frameType == kAudioFrameCN) {
    rtpInfo.type.Audio.isCNG = true;
  } else {
    rtpInfo.type.Audio.isCNG = false;
  }
  if (frameType == kEmptyFrame) {
    // When frame is empty, we should not transmit it. The frame size of the
    // next non-empty frame will be based on the previous frame size.
    _useLastFrameSize = _lastFrameSizeSample > 0;
    return 0;
  }

  rtpInfo.type.Audio.channel = 1;
  // Treat fragmentation separately
  if (fragmentation != NULL) {
    // If silence for too long, send only new data.
    if ((fragmentation->fragmentationVectorSize == 2) &&
        (fragmentation->fragmentationTimeDiff[1] <= 0x3fff)) {
      // only 0x80 if we have multiple blocks
      _payloadData[0] = 0x80 + fragmentation->fragmentationPlType[1];
      size_t REDheader = (fragmentation->fragmentationTimeDiff[1] << 10) +
          fragmentation->fragmentationLength[1];
      _payloadData[1] = uint8_t((REDheader >> 16) & 0x000000FF);
      _payloadData[2] = uint8_t((REDheader >> 8) & 0x000000FF);
      _payloadData[3] = uint8_t(REDheader & 0x000000FF);

      _payloadData[4] = fragmentation->fragmentationPlType[0];
      // copy the RED data
      memcpy(_payloadData + 5,
             payloadData + fragmentation->fragmentationOffset[1],
             fragmentation->fragmentationLength[1]);
      // copy the normal data
      memcpy(_payloadData + 5 + fragmentation->fragmentationLength[1],
             payloadData + fragmentation->fragmentationOffset[0],
             fragmentation->fragmentationLength[0]);
      payloadDataSize += 5;
    } else {
      // single block (newest one)
      memcpy(_payloadData, payloadData + fragmentation->fragmentationOffset[0],
             fragmentation->fragmentationLength[0]);
      payloadDataSize = fragmentation->fragmentationLength[0];
      rtpInfo.header.payloadType = fragmentation->fragmentationPlType[0];
    }
  } else {
    memcpy(_payloadData, payloadData, payloadDataSize);
    if (_isStereo) {
      if (_leftChannel) {
        memcpy(&_rtpInfo, &rtpInfo, sizeof(WebRtcRTPHeader));
        _leftChannel = false;
        rtpInfo.type.Audio.channel = 1;
      } else {
        memcpy(&rtpInfo, &_rtpInfo, sizeof(WebRtcRTPHeader));
        _leftChannel = true;
        rtpInfo.type.Audio.channel = 2;
      }
    }
  }

  _channelCritSect.Enter();
  if (_saveBitStream) {
    //fwrite(payloadData, sizeof(uint8_t), payloadSize, _bitStreamFile);
  }

  if (!_isStereo) {
    CalcStatistics(rtpInfo, payloadSize);
  }
  _useLastFrameSize = false;
  _lastInTimestamp = timeStamp;
  _totalBytes += payloadDataSize;
  _channelCritSect.Leave();

  if (_useFECTestWithPacketLoss) {
    _packetLoss += 1;
    if (_packetLoss == 3) {
      _packetLoss = 0;
      return 0;
    }
  }

  if (num_packets_to_drop_ > 0) {
    num_packets_to_drop_--;
    return 0;
  }

  status = _receiverACM->IncomingPacket(_payloadData, payloadDataSize, rtpInfo);

  return status;
}

// TODO(turajs): rewite this method.
void Channel::CalcStatistics(WebRtcRTPHeader& rtpInfo, size_t payloadSize) {
  int n;
  if ((rtpInfo.header.payloadType != _lastPayloadType)
      && (_lastPayloadType != -1)) {
    // payload-type is changed.
    // we have to terminate the calculations on the previous payload type
    // we ignore the last packet in that payload type just to make things
    // easier.
    for (n = 0; n < MAX_NUM_PAYLOADS; n++) {
      if (_lastPayloadType == _payloadStats[n].payloadType) {
        _payloadStats[n].newPacket = true;
        break;
      }
    }
  }
  _lastPayloadType = rtpInfo.header.payloadType;

  bool newPayload = true;
  ACMTestPayloadStats* currentPayloadStr = NULL;
  for (n = 0; n < MAX_NUM_PAYLOADS; n++) {
    if (rtpInfo.header.payloadType == _payloadStats[n].payloadType) {
      newPayload = false;
      currentPayloadStr = &_payloadStats[n];
      break;
    }
  }

  if (!newPayload) {
    if (!currentPayloadStr->newPacket) {
      if (!_useLastFrameSize) {
        _lastFrameSizeSample = (uint32_t) ((uint32_t) rtpInfo.header.timestamp -
            (uint32_t) currentPayloadStr->lastTimestamp);
      }
      assert(_lastFrameSizeSample > 0);
      int k = 0;
      for (; k < MAX_NUM_FRAMESIZES; ++k) {
        if ((currentPayloadStr->frameSizeStats[k].frameSizeSample ==
            _lastFrameSizeSample) ||
            (currentPayloadStr->frameSizeStats[k].frameSizeSample == 0)) {
          break;
        }
      }
      if (k == MAX_NUM_FRAMESIZES) {
        // New frame size found but no space to count statistics on it. Skip it.
        printf("No memory to store statistics for payload %d : frame size %d\n",
               _lastPayloadType, _lastFrameSizeSample);
        return;
      }
      ACMTestFrameSizeStats* currentFrameSizeStats = &(currentPayloadStr
          ->frameSizeStats[k]);
      currentFrameSizeStats->frameSizeSample = (int16_t) _lastFrameSizeSample;

      // increment the number of encoded samples.
      currentFrameSizeStats->totalEncodedSamples += _lastFrameSizeSample;
      // increment the number of recveived packets
      currentFrameSizeStats->numPackets++;
      // increment the total number of bytes (this is based on
      // the previous payload we don't know the frame-size of
      // the current payload.
      currentFrameSizeStats->totalPayloadLenByte += currentPayloadStr
          ->lastPayloadLenByte;
      // store the maximum payload-size (this is based on
      // the previous payload we don't know the frame-size of
      // the current payload.
      if (currentFrameSizeStats->maxPayloadLen
          < currentPayloadStr->lastPayloadLenByte) {
        currentFrameSizeStats->maxPayloadLen = currentPayloadStr
            ->lastPayloadLenByte;
      }
      // store the current values for the next time
      currentPayloadStr->lastTimestamp = rtpInfo.header.timestamp;
      currentPayloadStr->lastPayloadLenByte = payloadSize;
    } else {
      currentPayloadStr->newPacket = false;
      currentPayloadStr->lastPayloadLenByte = payloadSize;
      currentPayloadStr->lastTimestamp = rtpInfo.header.timestamp;
      currentPayloadStr->payloadType = rtpInfo.header.payloadType;
      memset(currentPayloadStr->frameSizeStats, 0, MAX_NUM_FRAMESIZES *
             sizeof(ACMTestFrameSizeStats));
    }
  } else {
    n = 0;
    while (_payloadStats[n].payloadType != -1) {
      n++;
    }
    // first packet
    _payloadStats[n].newPacket = false;
    _payloadStats[n].lastPayloadLenByte = payloadSize;
    _payloadStats[n].lastTimestamp = rtpInfo.header.timestamp;
    _payloadStats[n].payloadType = rtpInfo.header.payloadType;
    memset(_payloadStats[n].frameSizeStats, 0, MAX_NUM_FRAMESIZES *
           sizeof(ACMTestFrameSizeStats));
  }
}

Channel::Channel(int16_t chID)
    : _receiverACM(NULL),
      _seqNo(0),
      _bitStreamFile(NULL),
      _saveBitStream(false),
      _lastPayloadType(-1),
      _isStereo(false),
      _leftChannel(true),
      _lastInTimestamp(0),
      _useLastFrameSize(false),
      _lastFrameSizeSample(0),
      _packetLoss(0),
      _useFECTestWithPacketLoss(false),
      _beginTime(rtc::TimeMillis()),
      _totalBytes(0),
      external_send_timestamp_(-1),
      external_sequence_number_(-1),
      num_packets_to_drop_(0) {
  int n;
  int k;
  for (n = 0; n < MAX_NUM_PAYLOADS; n++) {
    _payloadStats[n].payloadType = -1;
    _payloadStats[n].newPacket = true;
    for (k = 0; k < MAX_NUM_FRAMESIZES; k++) {
      _payloadStats[n].frameSizeStats[k].frameSizeSample = 0;
      _payloadStats[n].frameSizeStats[k].maxPayloadLen = 0;
      _payloadStats[n].frameSizeStats[k].numPackets = 0;
      _payloadStats[n].frameSizeStats[k].totalPayloadLenByte = 0;
      _payloadStats[n].frameSizeStats[k].totalEncodedSamples = 0;
    }
  }
  if (chID >= 0) {
    _saveBitStream = true;
    char bitStreamFileName[500];
    sprintf(bitStreamFileName, "bitStream_%d.dat", chID);
    _bitStreamFile = fopen(bitStreamFileName, "wb");
  } else {
    _saveBitStream = false;
  }
}

Channel::~Channel() {
}

void Channel::RegisterReceiverACM(AudioCodingModule* acm) {
  _receiverACM = acm;
  return;
}

void Channel::ResetStats() {
  int n;
  int k;
  _channelCritSect.Enter();
  _lastPayloadType = -1;
  for (n = 0; n < MAX_NUM_PAYLOADS; n++) {
    _payloadStats[n].payloadType = -1;
    _payloadStats[n].newPacket = true;
    for (k = 0; k < MAX_NUM_FRAMESIZES; k++) {
      _payloadStats[n].frameSizeStats[k].frameSizeSample = 0;
      _payloadStats[n].frameSizeStats[k].maxPayloadLen = 0;
      _payloadStats[n].frameSizeStats[k].numPackets = 0;
      _payloadStats[n].frameSizeStats[k].totalPayloadLenByte = 0;
      _payloadStats[n].frameSizeStats[k].totalEncodedSamples = 0;
    }
  }
  _beginTime = rtc::TimeMillis();
  _totalBytes = 0;
  _channelCritSect.Leave();
}

int16_t Channel::Stats(CodecInst& codecInst,
                       ACMTestPayloadStats& payloadStats) {
  _channelCritSect.Enter();
  int n;
  payloadStats.payloadType = -1;
  for (n = 0; n < MAX_NUM_PAYLOADS; n++) {
    if (_payloadStats[n].payloadType == codecInst.pltype) {
      memcpy(&payloadStats, &_payloadStats[n], sizeof(ACMTestPayloadStats));
      break;
    }
  }
  if (payloadStats.payloadType == -1) {
    _channelCritSect.Leave();
    return -1;
  }
  for (n = 0; n < MAX_NUM_FRAMESIZES; n++) {
    if (payloadStats.frameSizeStats[n].frameSizeSample == 0) {
      _channelCritSect.Leave();
      return 0;
    }
    payloadStats.frameSizeStats[n].usageLenSec = (double) payloadStats
        .frameSizeStats[n].totalEncodedSamples / (double) codecInst.plfreq;

    payloadStats.frameSizeStats[n].rateBitPerSec =
        payloadStats.frameSizeStats[n].totalPayloadLenByte * 8
            / payloadStats.frameSizeStats[n].usageLenSec;

  }
  _channelCritSect.Leave();
  return 0;
}

void Channel::Stats(uint32_t* numPackets) {
  _channelCritSect.Enter();
  int k;
  int n;
  memset(numPackets, 0, MAX_NUM_PAYLOADS * sizeof(uint32_t));
  for (k = 0; k < MAX_NUM_PAYLOADS; k++) {
    if (_payloadStats[k].payloadType == -1) {
      break;
    }
    numPackets[k] = 0;
    for (n = 0; n < MAX_NUM_FRAMESIZES; n++) {
      if (_payloadStats[k].frameSizeStats[n].frameSizeSample == 0) {
        break;
      }
      numPackets[k] += _payloadStats[k].frameSizeStats[n].numPackets;
    }
  }
  _channelCritSect.Leave();
}

void Channel::Stats(uint8_t* payloadType, uint32_t* payloadLenByte) {
  _channelCritSect.Enter();

  int k;
  int n;
  memset(payloadLenByte, 0, MAX_NUM_PAYLOADS * sizeof(uint32_t));
  for (k = 0; k < MAX_NUM_PAYLOADS; k++) {
    if (_payloadStats[k].payloadType == -1) {
      break;
    }
    payloadType[k] = (uint8_t) _payloadStats[k].payloadType;
    payloadLenByte[k] = 0;
    for (n = 0; n < MAX_NUM_FRAMESIZES; n++) {
      if (_payloadStats[k].frameSizeStats[n].frameSizeSample == 0) {
        break;
      }
      payloadLenByte[k] += (uint16_t) _payloadStats[k].frameSizeStats[n]
          .totalPayloadLenByte;
    }
  }

  _channelCritSect.Leave();
}

void Channel::PrintStats(CodecInst& codecInst) {
  ACMTestPayloadStats payloadStats;
  Stats(codecInst, payloadStats);
  printf("%s %d kHz\n", codecInst.plname, codecInst.plfreq / 1000);
  printf("=====================================================\n");
  if (payloadStats.payloadType == -1) {
    printf("No Packets are sent with payload-type %d (%s)\n\n",
           codecInst.pltype, codecInst.plname);
    return;
  }
  for (int k = 0; k < MAX_NUM_FRAMESIZES; k++) {
    if (payloadStats.frameSizeStats[k].frameSizeSample == 0) {
      break;
    }
    printf("Frame-size.................... %d samples\n",
           payloadStats.frameSizeStats[k].frameSizeSample);
    printf("Average Rate.................. %.0f bits/sec\n",
           payloadStats.frameSizeStats[k].rateBitPerSec);
    printf("Maximum Payload-Size.......... %" PRIuS " Bytes\n",
           payloadStats.frameSizeStats[k].maxPayloadLen);
    printf(
        "Maximum Instantaneous Rate.... %.0f bits/sec\n",
        ((double) payloadStats.frameSizeStats[k].maxPayloadLen * 8.0
            * (double) codecInst.plfreq)
            / (double) payloadStats.frameSizeStats[k].frameSizeSample);
    printf("Number of Packets............. %u\n",
           (unsigned int) payloadStats.frameSizeStats[k].numPackets);
    printf("Duration...................... %0.3f sec\n\n",
           payloadStats.frameSizeStats[k].usageLenSec);

  }

}

uint32_t Channel::LastInTimestamp() {
  uint32_t timestamp;
  _channelCritSect.Enter();
  timestamp = _lastInTimestamp;
  _channelCritSect.Leave();
  return timestamp;
}

double Channel::BitRate() {
  double rate;
  uint64_t currTime = rtc::TimeMillis();
  _channelCritSect.Enter();
  rate = ((double) _totalBytes * 8.0) / (double) (currTime - _beginTime);
  _channelCritSect.Leave();
  return rate;
}

}  // namespace webrtc
