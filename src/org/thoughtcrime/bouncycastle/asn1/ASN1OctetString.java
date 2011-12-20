package org.thoughtcrime.bouncycastle.asn1;

import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.Arrays;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;

public abstract class ASN1OctetString
    extends ASN1Object
    implements ASN1OctetStringParser
{
    byte[]  string;

    /**
     * return an Octet String from a tagged object.
     *
     * @param obj the tagged object holding the object we want.
     * @param explicit true if the object is meant to be explicitly
     *              tagged false otherwise.
     * @exception IllegalArgumentException if the tagged object cannot
     *              be converted.
     */
    public static ASN1OctetString getInstance(
        ASN1TaggedObject    obj,
        boolean             explicit)
    {
        return getInstance(obj.getObject());
    }
    
    /**
     * return an Octet String from the given object.
     *
     * @param obj the object we want converted.
     * @exception IllegalArgumentException if the object cannot be converted.
     */
    public static ASN1OctetString getInstance(
        Object  obj)
    {
        if (obj == null || obj instanceof ASN1OctetString)
        {
            return (ASN1OctetString)obj;
        }

        if (obj instanceof ASN1TaggedObject)
        {
            return getInstance(((ASN1TaggedObject)obj).getObject());
        }

        if (obj instanceof ASN1Sequence)
        {
            Vector      v = new Vector();
            Enumeration e = ((ASN1Sequence)obj).getObjects();

            while (e.hasMoreElements())
            {
                v.addElement(e.nextElement());
            }

            return new BERConstructedOctetString(v);
        }

        throw new IllegalArgumentException("illegal object in getInstance: " + obj.getClass().getName());
    }

    /**
     * @param string the octets making up the octet string.
     */
    public ASN1OctetString(
        byte[]  string)
    {
        if (string == null)
        {
            throw new NullPointerException("string cannot be null");
        }
        this.string = string;
    }

    public ASN1OctetString(
        DEREncodable obj)
    {
        try
        {
            this.string = obj.getDERObject().getEncoded(ASN1Encodable.DER);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Error processing object : " + e.toString());
        }
    }

    public InputStream getOctetStream()
    {
        return new ByteArrayInputStream(string);
    }

    public ASN1OctetStringParser parser()
    {
        return this;
    }

    public byte[] getOctets()
    {
        return string;
    }

    public int hashCode()
    {
        return Arrays.hashCode(this.getOctets());
    }

    boolean asn1Equals(
        DERObject  o)
    {
        if (!(o instanceof ASN1OctetString))
        {
            return false;
        }

        ASN1OctetString  other = (ASN1OctetString)o;

        return Arrays.areEqual(string, other.string);
    }

    abstract void encode(DEROutputStream out)
        throws IOException;

    public String toString()
    {
      return "#"+new String(Hex.encode(string));
    }
}
