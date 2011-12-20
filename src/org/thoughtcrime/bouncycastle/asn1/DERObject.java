package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;

public abstract class DERObject
    extends ASN1Encodable
    implements DERTags
{
    public DERObject toASN1Object()
    {
        return this;
    }
    
    public abstract int hashCode();
    
    public abstract boolean equals(Object o);
    
    abstract void encode(DEROutputStream out)
        throws IOException;
}
