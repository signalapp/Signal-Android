package org.thoughtcrime.bouncycastle.asn1;

import java.io.InputStream;
import java.io.IOException;

class ConstructedOctetStream
    extends InputStream
{
    private final ASN1StreamParser _parser;

    private boolean                _first = true;
    private InputStream            _currentStream;

    ConstructedOctetStream(
        ASN1StreamParser parser)
    {
        _parser = parser;
    }

    public int read(byte[] b, int off, int len) throws IOException
    {
        if (_currentStream == null)
        {
            if (!_first)
            {
                return -1;
            }

            ASN1OctetStringParser s = (ASN1OctetStringParser)_parser.readObject();

            if (s == null)
            {
                return -1;
            }

            _first = false;
            _currentStream = s.getOctetStream();
        }

        int totalRead = 0;

        for (;;)
        {
            int numRead = _currentStream.read(b, off + totalRead, len - totalRead);

            if (numRead >= 0)
            {
                totalRead += numRead;

                if (totalRead == len)
                {
                    return totalRead;
                }
            }
            else
            {
                ASN1OctetStringParser aos = (ASN1OctetStringParser)_parser.readObject();

                if (aos == null)
                {
                    _currentStream = null;
                    return totalRead < 1 ? -1 : totalRead;
                }

                _currentStream = aos.getOctetStream();
            }
        }
    }

    public int read()
        throws IOException
    {
        if (_currentStream == null)
        {
            if (!_first)
            {
                return -1;
            }

            ASN1OctetStringParser s = (ASN1OctetStringParser)_parser.readObject();
    
            if (s == null)
            {
                return -1;
            }
    
            _first = false;
            _currentStream = s.getOctetStream();
        }

        for (;;)
        {
            int b = _currentStream.read();

            if (b >= 0)
            {
                return b;
            }

            ASN1OctetStringParser s = (ASN1OctetStringParser)_parser.readObject();

            if (s == null)
            {
                _currentStream = null;
                return -1;
            }

            _currentStream = s.getOctetStream();
        }
    }
}
