package org.thoughtcrime.bouncycastle.asn1;

import org.bouncycastle.util.Arrays;

import java.io.IOException;
import java.math.BigInteger;

public class DEREnumerated
    extends ASN1Object
{
    byte[]      bytes;

    /**
     * return an integer from the passed in object
     *
     * @exception IllegalArgumentException if the object cannot be converted.
     */
    public static DEREnumerated getInstance(
        Object  obj)
    {
        if (obj == null || obj instanceof DEREnumerated)
        {
            return (DEREnumerated)obj;
        }

        if (obj instanceof ASN1OctetString)
        {
            return new DEREnumerated(((ASN1OctetString)obj).getOctets());
        }

        if (obj instanceof ASN1TaggedObject)
        {
            return getInstance(((ASN1TaggedObject)obj).getObject());
        }

        throw new IllegalArgumentException("illegal object in getInstance: " + obj.getClass().getName());
    }

    /**
     * return an Enumerated from a tagged object.
     *
     * @param obj the tagged object holding the object we want
     * @param explicit true if the object is meant to be explicitly
     *              tagged false otherwise.
     * @exception IllegalArgumentException if the tagged object cannot
     *               be converted.
     */
    public static DEREnumerated getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(obj.getObject());
    }

    public DEREnumerated(
        int         value)
    {
        bytes = BigInteger.valueOf(value).toByteArray();
    }

    public DEREnumerated(
        BigInteger   value)
    {
        bytes = value.toByteArray();
    }

    public DEREnumerated(
        byte[]   bytes)
    {
        this.bytes = bytes;
    }

    public BigInteger getValue()
    {
        return new BigInteger(bytes);
    }

    void encode(
        DEROutputStream out)
        throws IOException
    {
        out.writeEncoded(ENUMERATED, bytes);
    }
    
    boolean asn1Equals(
        DERObject  o)
    {
        if (!(o instanceof DEREnumerated))
        {
            return false;
        }

        DEREnumerated other = (DEREnumerated)o;

        return Arrays.areEqual(this.bytes, other.bytes);
    }

    public int hashCode()
    {
        return Arrays.hashCode(bytes);
    }
}
