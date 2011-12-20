package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public class BERApplicationSpecificParser
    implements ASN1ApplicationSpecificParser
{
    private final int tag;
    private final ASN1StreamParser parser;

    BERApplicationSpecificParser(int tag, ASN1StreamParser parser)
    {
        this.tag = tag;
        this.parser = parser;
    }

    public DEREncodable readObject()
        throws IOException
    {
        return parser.readObject();
    }

    public DERObject getDERObject()
    {
        try
        {
            return new BERApplicationSpecific(tag, parser.readVector());
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }
}