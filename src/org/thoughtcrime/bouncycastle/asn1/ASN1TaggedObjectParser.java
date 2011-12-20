package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public interface ASN1TaggedObjectParser
    extends DEREncodable
{
    public int getTagNo();
    
    public DEREncodable getObjectParser(int tag, boolean isExplicit)
        throws IOException;
}
