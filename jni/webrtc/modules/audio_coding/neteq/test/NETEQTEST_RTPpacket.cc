/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "NETEQTEST_RTPpacket.h"

#include <assert.h>
#include <stdlib.h>  // rand
#include <string.h>

#ifdef WIN32
#include <winsock2.h>
#else
#include <netinet/in.h> // for htons, htonl, etc
#endif

const int NETEQTEST_RTPpacket::_kRDHeaderLen = 8;
const int NETEQTEST_RTPpacket::_kBasicHeaderLen = 12;

NETEQTEST_RTPpacket::NETEQTEST_RTPpacket()
:
_datagram(NULL),
_payloadPtr(NULL),
_memSize(0),
_datagramLen(-1),
_payloadLen(0),
_rtpParsed(false),
_receiveTime(0),
_lost(false)
{
    memset(&_rtpInfo, 0, sizeof(_rtpInfo));
    _blockList.clear();
}

NETEQTEST_RTPpacket::~NETEQTEST_RTPpacket()
{
    if(_datagram)
    {
        delete [] _datagram;
    }
}

void NETEQTEST_RTPpacket::reset()
{
    if(_datagram) {
        delete [] _datagram;
    }
    _datagram = NULL;
    _memSize = 0;
    _datagramLen = -1;
    _payloadLen = 0;
    _payloadPtr = NULL;
    _receiveTime = 0;
    memset(&_rtpInfo, 0, sizeof(_rtpInfo));
    _rtpParsed = false;

}

int NETEQTEST_RTPpacket::skipFileHeader(FILE *fp)
{
    if (!fp) {
        return -1;
    }

    const int kFirstLineLength = 40;
    char firstline[kFirstLineLength];
    if (fgets(firstline, kFirstLineLength, fp) == NULL) {
        return -1;
    }
    if (strncmp(firstline, "#!rtpplay", 9) == 0) {
        if (strncmp(firstline, "#!rtpplay1.0", 12) != 0) {
            return -1;
        }
    }
    else if (strncmp(firstline, "#!RTPencode", 11) == 0) {
        if (strncmp(firstline, "#!RTPencode1.0", 14) != 0) {
            return -1;
        }
    }
    else
    {
        return -1;
    }

    const int kRtpDumpHeaderSize = 4 + 4 + 4 + 2 + 2;
    if (fseek(fp, kRtpDumpHeaderSize, SEEK_CUR) != 0)
    {
        return -1;
    }
    return 0;
}

int NETEQTEST_RTPpacket::readFromFile(FILE *fp)
{
    if(!fp)
    {
        return(-1);
    }

    uint16_t length, plen;
    uint32_t offset;
    int packetLen = 0;

    bool readNextPacket = true;
    while (readNextPacket) {
        readNextPacket = false;
        if (fread(&length,2,1,fp)==0)
        {
            reset();
            return(-2);
        }
        length = ntohs(length);

        if (fread(&plen,2,1,fp)==0)
        {
            reset();
            return(-1);
        }
        packetLen = ntohs(plen);

        if (fread(&offset,4,1,fp)==0)
        {
            reset();
            return(-1);
        }
        // store in local variable until we have passed the reset below
        uint32_t receiveTime = ntohl(offset);

        // Use length here because a plen of 0 specifies rtcp
        length = (uint16_t) (length - _kRDHeaderLen);

        // check buffer size
        if (_datagram && _memSize < length)
        {
            reset();
        }

        if (!_datagram)
        {
            _datagram = new uint8_t[length];
            _memSize = length;
        }

        if (fread((unsigned short *) _datagram,1,length,fp) != length)
        {
            reset();
            return(-1);
        }

        _datagramLen = length;
        _receiveTime = receiveTime;

        if (!_blockList.empty() && _blockList.count(payloadType()) > 0)
        {
            readNextPacket = true;
        }
    }

    _rtpParsed = false;
    return(packetLen);

}


int NETEQTEST_RTPpacket::readFixedFromFile(FILE *fp, size_t length)
{
    if (!fp)
    {
        return -1;
    }

    // check buffer size
    if (_datagram && _memSize < static_cast<int>(length))
    {
        reset();
    }

    if (!_datagram)
    {
        _datagram = new uint8_t[length];
        _memSize = length;
    }

    if (fread(_datagram, 1, length, fp) != length)
    {
        reset();
        return -1;
    }

    _datagramLen = length;
    _receiveTime = 0;

    if (!_blockList.empty() && _blockList.count(payloadType()) > 0)
    {
        // discard this payload
        return readFromFile(fp);
    }

    _rtpParsed = false;
    return length;

}


int NETEQTEST_RTPpacket::writeToFile(FILE *fp)
{
    if (!fp)
    {
        return -1;
    }

    uint16_t length, plen;
    uint32_t offset;

    // length including RTPplay header
    length = htons(_datagramLen + _kRDHeaderLen);
    if (fwrite(&length, 2, 1, fp) != 1)
    {
        return -1;
    }

    // payload length
    plen = htons(_datagramLen);
    if (fwrite(&plen, 2, 1, fp) != 1)
    {
        return -1;
    }

    // offset (=receive time)
    offset = htonl(_receiveTime);
    if (fwrite(&offset, 4, 1, fp) != 1)
    {
        return -1;
    }


    // write packet data
    if (fwrite(_datagram, 1, _datagramLen, fp) !=
            static_cast<size_t>(_datagramLen))
    {
        return -1;
    }

    return _datagramLen + _kRDHeaderLen; // total number of bytes written

}


void NETEQTEST_RTPpacket::blockPT(uint8_t pt)
{
    _blockList[pt] = true;
}


void NETEQTEST_RTPpacket::parseHeader()
{
    if (_rtpParsed)
    {
        // nothing to do
        return;
    }

    if (_datagramLen < _kBasicHeaderLen)
    {
        // corrupt packet?
        return;
    }

    _payloadLen = parseRTPheader(&_payloadPtr);

    _rtpParsed = true;

    return;

}

void NETEQTEST_RTPpacket::parseHeader(webrtc::WebRtcRTPHeader* rtp_header) {
  if (!_rtpParsed) {
    parseHeader();
  }
  if (rtp_header) {
    rtp_header->header.markerBit = _rtpInfo.header.markerBit;
    rtp_header->header.payloadType = _rtpInfo.header.payloadType;
    rtp_header->header.sequenceNumber = _rtpInfo.header.sequenceNumber;
    rtp_header->header.timestamp = _rtpInfo.header.timestamp;
    rtp_header->header.ssrc = _rtpInfo.header.ssrc;
  }
}

const webrtc::WebRtcRTPHeader* NETEQTEST_RTPpacket::RTPinfo() const
{
    if (_rtpParsed)
    {
        return &_rtpInfo;
    }
    else
    {
        return NULL;
    }
}

uint8_t * NETEQTEST_RTPpacket::datagram() const
{
    if (_datagramLen > 0)
    {
        return _datagram;
    }
    else
    {
        return NULL;
    }
}

uint8_t * NETEQTEST_RTPpacket::payload() const
{
    if (_payloadLen > 0)
    {
        return _payloadPtr;
    }
    else
    {
        return NULL;
    }
}

size_t NETEQTEST_RTPpacket::payloadLen()
{
    parseHeader();
    return _payloadLen;
}

int16_t NETEQTEST_RTPpacket::dataLen() const
{
    return _datagramLen;
}

bool NETEQTEST_RTPpacket::isParsed() const
{
    return _rtpParsed;
}

bool NETEQTEST_RTPpacket::isLost() const
{
    return _lost;
}

uint8_t  NETEQTEST_RTPpacket::payloadType() const
{
    if(_datagram && _datagramLen >= _kBasicHeaderLen)
    {
        webrtc::WebRtcRTPHeader tempRTPinfo;
        parseRTPheader(&tempRTPinfo);
        return tempRTPinfo.header.payloadType;
    }
    else
    {
        return 0;
    }
}

uint16_t NETEQTEST_RTPpacket::sequenceNumber() const
{
    if(_datagram && _datagramLen >= _kBasicHeaderLen)
    {
        webrtc::WebRtcRTPHeader tempRTPinfo;
        parseRTPheader(&tempRTPinfo);
        return tempRTPinfo.header.sequenceNumber;
    }
    else
    {
        return 0;
    }
}

uint32_t NETEQTEST_RTPpacket::timeStamp() const
{
    if(_datagram && _datagramLen >= _kBasicHeaderLen)
    {
        webrtc::WebRtcRTPHeader tempRTPinfo;
        parseRTPheader(&tempRTPinfo);
        return tempRTPinfo.header.timestamp;
    }
    else
    {
        return 0;
    }
}

uint32_t NETEQTEST_RTPpacket::SSRC() const
{
    if(_datagram && _datagramLen >= _kBasicHeaderLen)
    {
        webrtc::WebRtcRTPHeader tempRTPinfo;
        parseRTPheader(&tempRTPinfo);
        return tempRTPinfo.header.ssrc;
    }
    else
    {
        return 0;
    }
}

uint8_t  NETEQTEST_RTPpacket::markerBit() const
{
    if(_datagram && _datagramLen >= _kBasicHeaderLen)
    {
        webrtc::WebRtcRTPHeader tempRTPinfo;
        parseRTPheader(&tempRTPinfo);
        return tempRTPinfo.header.markerBit;
    }
    else
    {
        return 0;
    }
}



int NETEQTEST_RTPpacket::setPayloadType(uint8_t pt)
{

    if (_datagramLen < 12)
    {
        return -1;
    }

    if (!_rtpParsed)
    {
        _rtpInfo.header.payloadType = pt;
    }

    _datagram[1] = pt;

    return 0;

}

int NETEQTEST_RTPpacket::setSequenceNumber(uint16_t sn)
{

    if (_datagramLen < 12)
    {
        return -1;
    }

    if (!_rtpParsed)
    {
        _rtpInfo.header.sequenceNumber = sn;
    }

    _datagram[2]=(unsigned char)((sn>>8)&0xFF);
    _datagram[3]=(unsigned char)((sn)&0xFF);

    return 0;

}

int NETEQTEST_RTPpacket::setTimeStamp(uint32_t ts)
{

    if (_datagramLen < 12)
    {
        return -1;
    }

    if (!_rtpParsed)
    {
        _rtpInfo.header.timestamp = ts;
    }

    _datagram[4]=(unsigned char)((ts>>24)&0xFF);
    _datagram[5]=(unsigned char)((ts>>16)&0xFF);
    _datagram[6]=(unsigned char)((ts>>8)&0xFF);
    _datagram[7]=(unsigned char)(ts & 0xFF);

    return 0;

}

int NETEQTEST_RTPpacket::setSSRC(uint32_t ssrc)
{

    if (_datagramLen < 12)
    {
        return -1;
    }

    if (!_rtpParsed)
    {
        _rtpInfo.header.ssrc = ssrc;
    }

    _datagram[8]=(unsigned char)((ssrc>>24)&0xFF);
    _datagram[9]=(unsigned char)((ssrc>>16)&0xFF);
    _datagram[10]=(unsigned char)((ssrc>>8)&0xFF);
    _datagram[11]=(unsigned char)(ssrc & 0xFF);

    return 0;

}

int NETEQTEST_RTPpacket::setMarkerBit(uint8_t mb)
{

    if (_datagramLen < 12)
    {
        return -1;
    }

    if (_rtpParsed)
    {
        _rtpInfo.header.markerBit = mb;
    }

    if (mb)
    {
        _datagram[0] |= 0x01;
    }
    else
    {
        _datagram[0] &= 0xFE;
    }

    return 0;

}

int NETEQTEST_RTPpacket::setRTPheader(const webrtc::WebRtcRTPHeader* RTPinfo)
{
    if (_datagramLen < 12)
    {
        // this packet is not ok
        return -1;
    }

    makeRTPheader(_datagram,
        RTPinfo->header.payloadType,
        RTPinfo->header.sequenceNumber,
        RTPinfo->header.timestamp,
        RTPinfo->header.ssrc,
        RTPinfo->header.markerBit);

    return 0;
}


int NETEQTEST_RTPpacket::splitStereo(NETEQTEST_RTPpacket* slaveRtp,
                                     enum stereoModes mode)
{
    // if mono, do nothing
    if (mode == stereoModeMono)
    {
        return 0;
    }

    // check that the RTP header info is parsed
    parseHeader();

    // start by copying the main rtp packet
    *slaveRtp = *this;

    if(_payloadLen == 0)
    {
        // do no more
        return 0;
    }

    if(_payloadLen%2 != 0)
    {
        // length must be a factor of 2
        return -1;
    }

    switch(mode)
    {
    case stereoModeSample1:
        {
            // sample based codec with 1-byte samples
            splitStereoSample(slaveRtp, 1 /* 1 byte/sample */);
            break;
        }
    case stereoModeSample2:
        {
            // sample based codec with 2-byte samples
            splitStereoSample(slaveRtp, 2 /* 2 bytes/sample */);
            break;
        }
    case stereoModeFrame:
        {
            // frame based codec
            splitStereoFrame(slaveRtp);
            break;
        }
    case stereoModeDuplicate:
        {
            // frame based codec, send the whole packet to both master and slave
            splitStereoDouble(slaveRtp);
            break;
        }
    case stereoModeMono:
        {
            assert(false);
            return -1;
        }
    }

    return 0;
}


void NETEQTEST_RTPpacket::makeRTPheader(unsigned char* rtp_data,
                                        uint8_t payloadType,
                                        uint16_t seqNo,
                                        uint32_t timestamp,
                                        uint32_t ssrc,
                                        uint8_t markerBit) const
{
    rtp_data[0] = markerBit ? 0x81 : 0x80;
    rtp_data[1] = payloadType;
    rtp_data[2] = seqNo >> 8;
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

uint16_t NETEQTEST_RTPpacket::parseRTPheader(webrtc::WebRtcRTPHeader* RTPinfo,
                                             uint8_t **payloadPtr) const
{
    uint16_t* rtp_data = reinterpret_cast<uint16_t*>(_datagram);
    int i_P, i_X, i_CC;

    assert(_datagramLen >= 12);
    parseBasicHeader(RTPinfo, &i_P, &i_X, &i_CC);

    int i_startPosition = calcHeaderLength(i_X, i_CC);

    int i_padlength = calcPadLength(i_P);

    if (payloadPtr)
    {
        *payloadPtr =
            reinterpret_cast<uint8_t*>(&rtp_data[i_startPosition >> 1]);
    }

    return static_cast<uint16_t>(_datagramLen - i_startPosition - i_padlength);
}


void NETEQTEST_RTPpacket::parseBasicHeader(webrtc::WebRtcRTPHeader* RTPinfo,
                                           int *i_P, int *i_X, int *i_CC) const
{
    uint16_t* rtp_data = reinterpret_cast<uint16_t*>(_datagram);
    if (_datagramLen < 12)
    {
        assert(false);
        return;
    }

    *i_P = (rtp_data[0] >> 5) & 0x01;
    *i_X = (rtp_data[0] >> 4) & 0x01;
    *i_CC = rtp_data[0] & 0xF;
    RTPinfo->header.markerBit = (rtp_data[0] >> 15) & 0x01;
    RTPinfo->header.payloadType = (rtp_data[0] >> 8) & 0x7F;
    RTPinfo->header.sequenceNumber =
        (rtp_data[1] >> 8) | ((rtp_data[1] & 0xFF) << 8);
    RTPinfo->header.timestamp =
        ((rtp_data[2] & 0xFF) << 24) | ((rtp_data[2] & 0xFF00) << 8) |
        (rtp_data[3] >> 8) | ((rtp_data[3] & 0xFF) << 8);
    RTPinfo->header.ssrc =
        ((rtp_data[4] & 0xFF) << 24) | ((rtp_data[4] & 0xFF00) << 8) |
        (rtp_data[5] >> 8) | ((rtp_data[5] & 0xFF) << 8);
}

int NETEQTEST_RTPpacket::calcHeaderLength(int i_X, int i_CC) const
{
    int i_extlength = 0;
    uint16_t* rtp_data = reinterpret_cast<uint16_t*>(_datagram);

    if (i_X == 1)
    {
        // Extension header exists.
        // Find out how many int32_t it consists of.
        int offset = 7 + 2 * i_CC;
        assert(_datagramLen > 2 * offset);
        if (_datagramLen > 2 * offset)
        {
            i_extlength = 1 +
                (((rtp_data[offset]) >> 8) | ((rtp_data[offset] & 0xFF) << 8));
        }
    }

    return 12 + 4 * i_extlength + 4 * i_CC;
}

int NETEQTEST_RTPpacket::calcPadLength(int i_P) const
{
    uint16_t* rtp_data = reinterpret_cast<uint16_t*>(_datagram);
    if (i_P == 1)
    {
        /* Padding exists. Find out how many bytes the padding consists of. */
        if (_datagramLen & 0x1)
        {
            /* odd number of bytes => last byte in higher byte */
            return rtp_data[_datagramLen >> 1] & 0xFF;
        }
        else
        {
            /* even number of bytes => last byte in lower byte */
            return rtp_data[(_datagramLen >> 1) - 1] >> 8;
        }
    }
    return 0;
}

void NETEQTEST_RTPpacket::splitStereoSample(NETEQTEST_RTPpacket* slaveRtp,
                                            int stride)
{
    if(!_payloadPtr || !slaveRtp || !slaveRtp->_payloadPtr
        || _payloadLen == 0 || slaveRtp->_memSize < _memSize)
    {
        return;
    }

    uint8_t *readDataPtr = _payloadPtr;
    uint8_t *writeDataPtr = _payloadPtr;
    uint8_t *slaveData = slaveRtp->_payloadPtr;

    while (readDataPtr - _payloadPtr < static_cast<ptrdiff_t>(_payloadLen))
    {
        // master data
        for (int ix = 0; ix < stride; ix++) {
            *writeDataPtr = *readDataPtr;
            writeDataPtr++;
            readDataPtr++;
        }

        // slave data
        for (int ix = 0; ix < stride; ix++) {
            *slaveData = *readDataPtr;
            slaveData++;
            readDataPtr++;
        }
    }

    _payloadLen /= 2;
    slaveRtp->_payloadLen = _payloadLen;
}


void NETEQTEST_RTPpacket::splitStereoFrame(NETEQTEST_RTPpacket* slaveRtp)
{
    if(!_payloadPtr || !slaveRtp || !slaveRtp->_payloadPtr
        || _payloadLen == 0 || slaveRtp->_memSize < _memSize)
    {
        return;
    }

    memmove(slaveRtp->_payloadPtr, _payloadPtr + _payloadLen/2, _payloadLen/2);

    _payloadLen /= 2;
    slaveRtp->_payloadLen = _payloadLen;
}
void NETEQTEST_RTPpacket::splitStereoDouble(NETEQTEST_RTPpacket* slaveRtp)
{
    if(!_payloadPtr || !slaveRtp || !slaveRtp->_payloadPtr
        || _payloadLen == 0 || slaveRtp->_memSize < _memSize)
    {
        return;
    }

    memcpy(slaveRtp->_payloadPtr, _payloadPtr, _payloadLen);
    slaveRtp->_payloadLen = _payloadLen;
}

// Get the RTP header for the RED payload indicated by argument index.
// The first RED payload is index = 0.
int NETEQTEST_RTPpacket::extractRED(int index, webrtc::WebRtcRTPHeader& red)
{
//
//  0                   1                    2                   3
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3  4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |1|   block PT  |  timestamp offset         |   block length    |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |1|    ...                                                      |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |0|   block PT  |
// +-+-+-+-+-+-+-+-+
//

    parseHeader();

    uint8_t* ptr = payload();
    uint8_t* payloadEndPtr = ptr + payloadLen();
    int num_encodings = 0;
    int total_len = 0;

    while ((ptr < payloadEndPtr) && (*ptr & 0x80))
    {
        int len = ((ptr[2] & 0x03) << 8) + ptr[3];
        if (num_encodings == index)
        {
            // Header found.
            red.header.payloadType = ptr[0] & 0x7F;
            uint32_t offset = (ptr[1] << 6) + (ptr[2] >> 2);
            red.header.sequenceNumber = sequenceNumber();
            red.header.timestamp = timeStamp() - offset;
            red.header.markerBit = markerBit();
            red.header.ssrc = SSRC();
            return len;
        }
        ++num_encodings;
        total_len += len;
        ptr += 4;
    }
    if ((ptr < payloadEndPtr) && (num_encodings == index))
    {
        // Last header.
        red.header.payloadType = ptr[0] & 0x7F;
        red.header.sequenceNumber = sequenceNumber();
        red.header.timestamp = timeStamp();
        red.header.markerBit = markerBit();
        red.header.ssrc = SSRC();
        ++ptr;
        return payloadLen() - (ptr - payload()) - total_len;
    }
    return -1;
}

// Randomize the payload, not the RTP header.
void NETEQTEST_RTPpacket::scramblePayload(void)
{
    parseHeader();

    for (size_t i = 0; i < _payloadLen; ++i)
    {
        _payloadPtr[i] = static_cast<uint8_t>(rand());
    }
}
