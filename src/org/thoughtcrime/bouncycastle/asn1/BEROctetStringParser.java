package org.thoughtcrime.bouncycastle.asn1;

import org.bouncycastle.util.io.Streams;

import java.io.InputStream;
import java.io.IOException;

public class BEROctetStringParser
    implements ASN1OctetStringParser
{
    private ASN1StreamParser _parser;

    BEROctetStringParser(
        ASN1StreamParser parser)
    {
        _parser = parser;
    }

    /**
     * @deprecated will be removed
     */
    protected BEROctetStringParser(
        ASN1ObjectParser parser)
    {
        _parser = parser._aIn;
    }

    public InputStream getOctetStream()
    {
        return new ConstructedOctetStream(_parser);
    }

    public DERObject getDERObject()
    {
        try
        {
            return new BERConstructedOctetString(Streams.readAll(getOctetStream()));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("IOException converting stream to byte array: " + e.getMessage());
        }
    }
}
