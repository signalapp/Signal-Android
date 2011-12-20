package org.thoughtcrime.bouncycastle.asn1;

import java.io.IOException;
import java.util.Enumeration;

public class BERSequence
    extends DERSequence
{
    /**
     * create an empty sequence
     */
    public BERSequence()
    {
    }

    /**
     * create a sequence containing one object
     */
    public BERSequence(
        DEREncodable    obj)
    {
        super(obj);
    }

    /**
     * create a sequence containing a vector of objects.
     */
    public BERSequence(
        DEREncodableVector   v)
    {
        super(v);
    }

    /*
     */
    void encode(
        DEROutputStream out)
        throws IOException
    {
        if (out instanceof ASN1OutputStream || out instanceof BEROutputStream)
        {
            out.write(SEQUENCE | CONSTRUCTED);
            out.write(0x80);
            
            Enumeration e = getObjects();
            while (e.hasMoreElements())
            {
                out.writeObject(e.nextElement());
            }
        
            out.write(0x00);
            out.write(0x00);
        }
        else
        {
            super.encode(out);
        }
    }
}
