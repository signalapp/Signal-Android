package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public class BERSetParser
    implements ASN1SetParser
{
    private ASN1StreamParser _parser;

    BERSetParser(ASN1StreamParser parser)
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
            return new BERSet(_parser.readVector(), false);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
