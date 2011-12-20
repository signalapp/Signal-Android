package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public class BERSequenceParser
    implements ASN1SequenceParser
{
    private ASN1StreamParser _parser;

    BERSequenceParser(ASN1StreamParser parser)
    {
        this._parser = parser;
    }

    public DEREncodable readObject()
        throws IOException
    {
        return _parser.readObject();
    }

    public DERObject getDERObject()
    {
        try
        {
            return new BERSequence(_parser.readVector());
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
