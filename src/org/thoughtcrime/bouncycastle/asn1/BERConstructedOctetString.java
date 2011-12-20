package org.thoughtcrime.bouncycastle.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

public class BERConstructedOctetString
    extends DEROctetString
{
    private static final int MAX_LENGTH = 1000;

    /**
     * convert a vector of octet strings into a single byte string
     */
    static private byte[] toBytes(
        Vector  octs)
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();

        for (int i = 0; i != octs.size(); i++)
        {
            try
            {
                DEROctetString  o = (DEROctetString)octs.elementAt(i);

                bOut.write(o.getOctets());
            }
            catch (ClassCastException e)
            {
                throw new IllegalArgumentException(octs.elementAt(i).getClass().getName() + " found in input should only contain DEROctetString");
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("exception converting octets " + e.toString());
            }
        }

        return bOut.toByteArray();
    }

    private Vector  octs;

    /**
     * @param string the octets making up the octet string.
     */
    public BERConstructedOctetString(
        byte[]  string)
    {
        super(string);
    }

    public BERConstructedOctetString(
        Vector  octs)
    {
        super(toBytes(octs));

        this.octs = octs;
    }

    public BERConstructedOctetString(
        DERObject  obj)
    {
        super(obj);
    }

    public BERConstructedOctetString(
        DEREncodable  obj)
    {
        super(obj.getDERObject());
    }

    public byte[] getOctets()
    {
        return string;
    }

    /**
     * return the DER octets that make up this string.
     */
    public Enumeration getObjects()
    {
        if (octs == null)
        {
            return generateOcts().elements();
        }

        return octs.elements();
    }

    private Vector generateOcts() 
    { 
        Vector vec = new Vector(); 
        for (int i = 0; i < string.length; i += MAX_LENGTH) 
        { 
            int end; 

            if (i + MAX_LENGTH > string.length) 
            { 
                end = string.length; 
            } 
            else 
            { 
                end = i + MAX_LENGTH; 
            } 

            byte[] nStr = new byte[end - i]; 

            System.arraycopy(string, i, nStr, 0, nStr.length); 

            vec.addElement(new DEROctetString(nStr)); 
         } 
        
         return vec; 
    }

    public void encode(
        DEROutputStream out)
        throws IOException
    {
        if (out instanceof ASN1OutputStream || out instanceof BEROutputStream)
        {
            out.write(CONSTRUCTED | OCTET_STRING);

            out.write(0x80);

            //
            // write out the octet array
            //
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
