package org.thoughtcrime.bouncycastle.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;

/**
 * A DER encoded set object
 */
public class DERSet
    extends ASN1Set
{
    /**
     * create an empty set
     */
    public DERSet()
    {
    }

    /**
     * @param obj - a single object that makes up the set.
     */
    public DERSet(
        DEREncodable   obj)
    {
        this.addObject(obj);
    }

    /**
     * @param v - a vector of objects making up the set.
     */
    public DERSet(
        DEREncodableVector   v)
    {
        this(v, true);
    }
    
    /**
     * create a set from an array of objects.
     */
    public DERSet(
        ASN1Encodable[]   a)
    {
        for (int i = 0; i != a.length; i++)
        {
            this.addObject(a[i]);
        }
        
        this.sort();
    }
    
    /**
     * @param v - a vector of objects making up the set.
     */
    DERSet(
        DEREncodableVector   v,
        boolean              needsSorting)
    {
        for (int i = 0; i != v.size(); i++)
        {
            this.addObject(v.get(i));
        }

        if (needsSorting)
        {
            this.sort();
        }
    }

    /*
     * A note on the implementation:
     * <p>
     * As DER requires the constructed, definite-length model to
     * be used for structured types, this varies slightly from the
     * ASN.1 descriptions given. Rather than just outputing SET,
     * we also have to specify CONSTRUCTED, and the objects length.
     */
    void encode(
        DEROutputStream out)
        throws IOException
    {
        // TODO Intermediate buffer could be avoided if we could calculate expected length
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);
        Enumeration             e = this.getObjects();

        while (e.hasMoreElements())
        {
            Object    obj = e.nextElement();

            dOut.writeObject(obj);
        }

        dOut.close();

        byte[]  bytes = bOut.toByteArray();

        out.writeEncoded(SET | CONSTRUCTED, bytes);
    }
}
