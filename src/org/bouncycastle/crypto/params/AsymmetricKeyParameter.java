package org.bouncycastle.crypto.params;

import org.bouncycastle.crypto.CipherParameters;
import org.thoughtcrime.securesms.R;

public class AsymmetricKeyParameter
    implements CipherParameters
{
    boolean privateKey;

    public AsymmetricKeyParameter(
        boolean privateKey)
    {
        this.privateKey = privateKey;
    }

    public boolean isPrivate()
    {
        return privateKey;
    }
}
