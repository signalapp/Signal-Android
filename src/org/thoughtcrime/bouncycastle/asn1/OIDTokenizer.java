package org.thoughtcrime.bouncycastle.asn1;

/**
 * class for breaking up an OID into it's component tokens, ala
 * java.util.StringTokenizer. We need this class as some of the
 * lightweight Java environment don't support classes like
 * StringTokenizer.
 */
public class OIDTokenizer
{
    private String  oid;
    private int     index;

    public OIDTokenizer(
        String oid)
    {
        this.oid = oid;
        this.index = 0;
    }

    public boolean hasMoreTokens()
    {
        return (index != -1);
    }

    public String nextToken()
    {
        if (index == -1)
        {
            return null;
        }

        String  token;
        int     end = oid.indexOf('.', index);

        if (end == -1)
        {
            token = oid.substring(index);
            index = -1;
            return token;
        }

        token = oid.substring(index, end);

        index = end + 1;
        return token;
    }
}
