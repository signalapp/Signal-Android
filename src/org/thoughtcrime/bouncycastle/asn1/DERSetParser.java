package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public class DERSetParser
    implements ASN1SetParser
{
    private ASN1StreamParser _parser;

    DERSetParser(ASN1StreamParser parser)
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
            return new DERSet(_parser.readVector(), false);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
