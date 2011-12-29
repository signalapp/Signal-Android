package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;
import org.thoughtcrime.securesms.R;

public interface ASN1TaggedObjectParser
    extends DEREncodable
{
    public int getTagNo();
    
    public DEREncodable getObjectParser(int tag, boolean isExplicit)
        throws IOException;
}
