package org.thoughtcrime.bouncycastle.asn1;

import org.bouncycastle.util.Arrays;

import java.io.IOException;

/**
 * We insert one of these when we find a tag we don't recognise.
 */
public class DERUnknownTag
    extends DERObject
{
    private boolean   isConstructed;
    private int       tag;
    private byte[]    data;

    /**
     * @param tag the tag value.
     * @param data the contents octets.
     */
    public DERUnknownTag(
        int     tag,
        byte[]  data)
    {
        this(false, tag, data);
    }

    public DERUnknownTag(
        boolean isConstructed,
        int     tag,
        byte[]  data)
    {
        this.isConstructed = isConstructed;
        this.tag = tag;
        this.data = data;
    }

    public boolean isConstructed()
    {
        return isConstructed;
    }

    public int getTag()
    {
        return tag;
    }

    public byte[] getData()
    {
        return data;
    }

    void encode(
        DEROutputStream  out)
        throws IOException
    {
        out.writeEncoded(isConstructed ? DERTags.CONSTRUCTED : 0, tag, data);
    }
    
    public boolean equals(
        Object o)
    {
        if (!(o instanceof DERUnknownTag))
        {
            return false;
        }
        
        DERUnknownTag other = (DERUnknownTag)o;

        return isConstructed == other.isConstructed
        	&& tag == other.tag
            && Arrays.areEqual(data, other.data);
    }
    
    public int hashCode()
    {
        return (isConstructed ? ~0 : 0) ^ tag ^ Arrays.hashCode(data);
    }
}
