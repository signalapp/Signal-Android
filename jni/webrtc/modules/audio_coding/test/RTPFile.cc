/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "RTPFile.h"

#include <stdlib.h>
#include <limits>

#ifdef WIN32
#   include <Winsock2.h>
#else
#   include <arpa/inet.h>
#endif

#include "audio_coding_module.h"
#include "engine_configurations.h"
#include "webrtc/system_wrappers/include/rw_lock_wrapper.h"
// TODO(tlegrand): Consider removing usage of gtest.
#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

void RTPStream::ParseRTPHeader(WebRtcRTPHeader* rtpInfo,
                               const uint8_t* rtpHeader) {
  rtpInfo->header.payloadType = rtpHeader[1];
  rtpInfo->header.sequenceNumber = (static_cast<uint16_t>(rtpHeader[2]) << 8) |
      rtpHeader[3];
  rtpInfo->header.timestamp = (static_cast<uint32_t>(rtpHeader[4]) << 24) |
      (static_cast<uint32_t>(rtpHeader[5]) << 16) |
      (static_cast<uint32_t>(rtpHeader[6]) << 8) | rtpHeader[7];
  rtpInfo->header.ssrc = (static_cast<uint32_t>(rtpHeader[8]) << 24) |
      (static_cast<uint32_t>(rtpHeader[9]) << 16) |
      (static_cast<uint32_t>(rtpHeader[10]) << 8) | rtpHeader[11];
}

void RTPStream::MakeRTPheader(uint8_t* rtpHeader, uint8_t payloadType,
                              int16_t seqNo, uint32_t timeStamp,
                              uint32_t ssrc) {
  rtpHeader[0] = 0x80;
  rtpHeader[1] = payloadType;
  rtpHeader[2] = (seqNo >> 8) & 0xFF;
  rtpHeader[3] = seqNo & 0xFF;
  rtpHeader[4] = timeStamp >> 24;
  rtpHeader[5] = (timeStamp >> 16) & 0xFF;
  rtpHeader[6] = (timeStamp >> 8) & 0xFF;
  rtpHeader[7] = timeStamp & 0xFF;
  rtpHeader[8] = ssrc >> 24;
  rtpHeader[9] = (ssrc >> 16) & 0xFF;
  rtpHeader[10] = (ssrc >> 8) & 0xFF;
  rtpHeader[11] = ssrc & 0xFF;
}

RTPPacket::RTPPacket(uint8_t payloadType, uint32_t timeStamp, int16_t seqNo,
                     const uint8_t* payloadData, size_t payloadSize,
                     uint32_t frequency)
    : payloadType(payloadType),
      timeStamp(timeStamp),
      seqNo(seqNo),
      payloadSize(payloadSize),
      frequency(frequency) {
  if (payloadSize > 0) {
    this->payloadData = new uint8_t[payloadSize];
    memcpy(this->payloadData, payloadData, payloadSize);
  }
}

RTPPacket::~RTPPacket() {
  delete[] payloadData;
}

RTPBuffer::RTPBuffer() {
  _queueRWLock = RWLockWrapper::CreateRWLock();
}

RTPBuffer::~RTPBuffer() {
  delete _queueRWLock;
}

void RTPBuffer::Write(const uint8_t payloadType, const uint32_t timeStamp,
                      const int16_t seqNo, const uint8_t* payloadData,
                      const size_t payloadSize, uint32_t frequency) {
  RTPPacket *packet = new RTPPacket(payloadType, timeStamp, seqNo, payloadData,
                                    payloadSize, frequency);
  _queueRWLock->AcquireLockExclusive();
  _rtpQueue.push(packet);
  _queueRWLock->ReleaseLockExclusive();
}

size_t RTPBuffer::Read(WebRtcRTPHeader* rtpInfo, uint8_t* payloadData,
                       size_t payloadSize, uint32_t* offset) {
  _queueRWLock->AcquireLockShared();
  RTPPacket *packet = _rtpQueue.front();
  _rtpQueue.pop();
  _queueRWLock->ReleaseLockShared();
  rtpInfo->header.markerBit = 1;
  rtpInfo->header.payloadType = packet->payloadType;
  rtpInfo->header.sequenceNumber = packet->seqNo;
  rtpInfo->header.ssrc = 0;
  rtpInfo->header.timestamp = packet->timeStamp;
  if (packet->payloadSize > 0 && payloadSize >= packet->payloadSize) {
    memcpy(payloadData, packet->payloadData, packet->payloadSize);
  } else {
    return 0;
  }
  *offset = (packet->timeStamp / (packet->frequency / 1000));

  return packet->payloadSize;
}

bool RTPBuffer::EndOfFile() const {
  _queueRWLock->AcquireLockShared();
  bool eof = _rtpQueue.empty();
  _queueRWLock->ReleaseLockShared();
  return eof;
}

void RTPFile::Open(const char *filename, const char *mode) {
  if ((_rtpFile = fopen(filename, mode)) == NULL) {
    printf("Cannot write file %s.\n", filename);
    ADD_FAILURE() << "Unable to write file";
    exit(1);
  }
}

void RTPFile::Close() {
  if (_rtpFile != NULL) {
    fclose(_rtpFile);
    _rtpFile = NULL;
  }
}

void RTPFile::WriteHeader() {
  // Write data in a format that NetEQ and RTP Play can parse
  fprintf(_rtpFile, "#!RTPencode%s\n", "1.0");
  uint32_t dummy_variable = 0;
  // should be converted to network endian format, but does not matter when 0
  EXPECT_EQ(1u, fwrite(&dummy_variable, 4, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&dummy_variable, 4, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&dummy_variable, 4, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&dummy_variable, 2, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&dummy_variable, 2, 1, _rtpFile));
  fflush(_rtpFile);
}

void RTPFile::ReadHeader() {
  uint32_t start_sec, start_usec, source;
  uint16_t port, padding;
  char fileHeader[40];
  EXPECT_TRUE(fgets(fileHeader, 40, _rtpFile) != 0);
  EXPECT_EQ(1u, fread(&start_sec, 4, 1, _rtpFile));
  start_sec = ntohl(start_sec);
  EXPECT_EQ(1u, fread(&start_usec, 4, 1, _rtpFile));
  start_usec = ntohl(start_usec);
  EXPECT_EQ(1u, fread(&source, 4, 1, _rtpFile));
  source = ntohl(source);
  EXPECT_EQ(1u, fread(&port, 2, 1, _rtpFile));
  port = ntohs(port);
  EXPECT_EQ(1u, fread(&padding, 2, 1, _rtpFile));
  padding = ntohs(padding);
}

void RTPFile::Write(const uint8_t payloadType, const uint32_t timeStamp,
                    const int16_t seqNo, const uint8_t* payloadData,
                    const size_t payloadSize, uint32_t frequency) {
  /* write RTP packet to file */
  uint8_t rtpHeader[12];
  MakeRTPheader(rtpHeader, payloadType, seqNo, timeStamp, 0);
  ASSERT_LE(12 + payloadSize + 8, std::numeric_limits<u_short>::max());
  uint16_t lengthBytes = htons(static_cast<u_short>(12 + payloadSize + 8));
  uint16_t plen = htons(static_cast<u_short>(12 + payloadSize));
  uint32_t offsetMs;

  offsetMs = (timeStamp / (frequency / 1000));
  offsetMs = htonl(offsetMs);
  EXPECT_EQ(1u, fwrite(&lengthBytes, 2, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&plen, 2, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&offsetMs, 4, 1, _rtpFile));
  EXPECT_EQ(1u, fwrite(&rtpHeader, 12, 1, _rtpFile));
  EXPECT_EQ(payloadSize, fwrite(payloadData, 1, payloadSize, _rtpFile));
}

size_t RTPFile::Read(WebRtcRTPHeader* rtpInfo, uint8_t* payloadData,
                     size_t payloadSize, uint32_t* offset) {
  uint16_t lengthBytes;
  uint16_t plen;
  uint8_t rtpHeader[12];
  size_t read_len = fread(&lengthBytes, 2, 1, _rtpFile);
  /* Check if we have reached end of file. */
  if ((read_len == 0) && feof(_rtpFile)) {
    _rtpEOF = true;
    return 0;
  }
  EXPECT_EQ(1u, fread(&plen, 2, 1, _rtpFile));
  EXPECT_EQ(1u, fread(offset, 4, 1, _rtpFile));
  lengthBytes = ntohs(lengthBytes);
  plen = ntohs(plen);
  *offset = ntohl(*offset);
  EXPECT_GT(plen, 11);

  EXPECT_EQ(1u, fread(rtpHeader, 12, 1, _rtpFile));
  ParseRTPHeader(rtpInfo, rtpHeader);
  rtpInfo->type.Audio.isCNG = false;
  rtpInfo->type.Audio.channel = 1;
  EXPECT_EQ(lengthBytes, plen + 8);

  if (plen == 0) {
    return 0;
  }
  if (lengthBytes < 20) {
    return 0;
  }
  if (payloadSize < static_cast<size_t>((lengthBytes - 20))) {
    return 0;
  }
  lengthBytes -= 20;
  EXPECT_EQ(lengthBytes, fread(payloadData, 1, lengthBytes, _rtpFile));
  return lengthBytes;
}

}  // namespace webrtc
