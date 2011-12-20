package org.thoughtcrime.bouncycastle.asn1;

import org.bouncycastle.util.Strings;

import java.io.IOException;

/**
 * DER UTF8String object.
 */
public class DERUTF8String
    extends ASN1Object
    implements DERString
{
    String string;

    /**
     * return an UTF8 string from the passed in object.
     * 
     * @exception IllegalArgumentException
     *                if the object cannot be converted.
     */
    public static DERUTF8String getInstance(Object obj)
    {
        if (obj == null || obj instanceof DERUTF8String)
        {
            return (DERUTF8String)obj;
        }

        if (obj instanceof ASN1OctetString)
        {
            return new DERUTF8String(((ASN1OctetString)obj).getOctets());
        }

        if (obj instanceof ASN1TaggedObject)
        {
            return getInstance(((ASN1TaggedObject)obj).getObject());
        }

        throw new IllegalArgumentException("illegal object in getInstance: "
                + obj.getClass().getName());
    }

    /**
     * return an UTF8 String from a tagged object.
     * 
     * @param obj
     *            the tagged object holding the object we want
     * @param explicit
     *            true if the object is meant to be explicitly tagged false
     *            otherwise.
     * @exception IllegalArgumentException
     *                if the tagged object cannot be converted.
     */
    public static DERUTF8String getInstance(
        ASN1TaggedObject obj,
        boolean explicit)
    {
        return getInstance(obj.getObject());
    }

    /**
     * basic constructor - byte encoded string.
     */
    DERUTF8String(byte[] string)
    {
        this.string = Strings.fromUTF8ByteArray(string);
    }

    /**
     * basic constructor
     */
    public DERUTF8String(String string)
    {
        this.string = string;
    }

    public String getString()
    {
        return string;
    }

    public String toString()
    {
        return string;
    }

    public int hashCode()
    {
        return this.getString().hashCode();
    }

    boolean asn1Equals(DERObject o)
    {
        if (!(o instanceof DERUTF8String))
        {
            return false;
        }

        DERUTF8String s = (DERUTF8String)o;

        return this.getString().equals(s.getString());
    }

    void encode(DEROutputStream out)
        throws IOException
    {
        out.writeEncoded(UTF8_STRING, Strings.toUTF8ByteArray(string));
    }
}
