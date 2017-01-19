/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "NETEQTEST_DummyRTPpacket.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

#ifdef WIN32
#include <winsock2.h>
#else
#include <netinet/in.h> // for htons, htonl, etc
#endif

int NETEQTEST_DummyRTPpacket::readFromFile(FILE *fp)
{
    if (!fp)
    {
        return -1;
    }

    uint16_t length, plen;
    uint32_t offset;
    int packetLen = 0;

    bool readNextPacket = true;
    while (readNextPacket) {
        readNextPacket = false;
        if (fread(&length, 2, 1, fp) == 0)
        {
            reset();
            return -2;
        }
        length = ntohs(length);

        if (fread(&plen, 2, 1, fp) == 0)
        {
            reset();
            return -1;
        }
        packetLen = ntohs(plen);

        if (fread(&offset, 4, 1, fp) == 0)
        {
            reset();
            return -1;
        }
        // Store in local variable until we have passed the reset below.
        uint32_t receiveTime = ntohl(offset);

        // Use length here because a plen of 0 specifies rtcp.
        length = (uint16_t) (length - _kRDHeaderLen);

        // check buffer size
        if (_datagram && _memSize < length + 1)
        {
            reset();
        }

        if (!_datagram)
        {
            // Add one extra byte, to be able to fake a dummy payload of 1 byte.
            _datagram = new uint8_t[length + 1];
            _memSize = length + 1;
        }
        memset(_datagram, 0, length + 1);

        if (length == 0)
        {
            _datagramLen = 0;
            _rtpParsed = false;
            return packetLen;
        }

        // Read basic header
        if (fread((unsigned short *) _datagram, 1, _kBasicHeaderLen, fp)
            != (size_t)_kBasicHeaderLen)
        {
            reset();
            return -1;
        }
        _receiveTime = receiveTime;
        _datagramLen = _kBasicHeaderLen;

        // Parse the basic header
        webrtc::WebRtcRTPHeader tempRTPinfo;
        int P, X, CC;
        parseBasicHeader(&tempRTPinfo, &P, &X, &CC);

        // Check if we have to extend the header
        if (X != 0 || CC != 0)
        {
            int newLen = _kBasicHeaderLen + CC * 4 + X * 4;
            assert(_memSize >= newLen);

            // Read extension from file
            size_t readLen = newLen - _kBasicHeaderLen;
            if (fread(&_datagram[_kBasicHeaderLen], 1, readLen, fp) != readLen)
            {
                reset();
                return -1;
            }
            _datagramLen = newLen;

            if (X != 0)
            {
                int totHdrLen = calcHeaderLength(X, CC);
                assert(_memSize >= totHdrLen);

                // Read extension from file
                size_t readLen = totHdrLen - newLen;
                if (fread(&_datagram[newLen], 1, readLen, fp) != readLen)
                {
                    reset();
                    return -1;
                }
                _datagramLen = totHdrLen;
            }
        }
        _datagramLen = length;

        if (!_blockList.empty() && _blockList.count(payloadType()) > 0)
        {
            readNextPacket = true;
        }
    }

    _rtpParsed = false;
    assert(_memSize > _datagramLen);
    _payloadLen = 1;  // Set the length to 1 byte.
    return packetLen;

}

int NETEQTEST_DummyRTPpacket::writeToFile(FILE *fp)
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

    // Figure out the length of the RTP header.
    int headerLen;
    if (_datagramLen == 0)
    {
        // No payload at all; we are done writing to file.
        headerLen = 0;
    }
    else
    {
        parseHeader();
        headerLen = _payloadPtr - _datagram;
        assert(headerLen >= 0);
    }

    // write RTP header
    if (fwrite((unsigned short *) _datagram, 1, headerLen, fp) !=
        static_cast<size_t>(headerLen))
    {
        return -1;
    }

    return (headerLen + _kRDHeaderLen); // total number of bytes written

}

void NETEQTEST_DummyRTPpacket::parseHeader() {
  NETEQTEST_RTPpacket::parseHeader();
  // Change _payloadLen to 1 byte. The memory should always be big enough.
  assert(_memSize > _datagramLen);
  _payloadLen = 1;
}
