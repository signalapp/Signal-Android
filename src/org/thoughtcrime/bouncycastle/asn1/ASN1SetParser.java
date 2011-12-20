package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public interface ASN1SetParser
    extends DEREncodable
{
    public DEREncodable readObject()
        throws IOException;
}
