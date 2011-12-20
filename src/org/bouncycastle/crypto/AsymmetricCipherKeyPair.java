package org.bouncycastle.crypto;

/**
 * a holding class for public/private parameter pairs.
 */
public class AsymmetricCipherKeyPair
{
    private CipherParameters    publicParam;
    private CipherParameters    privateParam;

    /**
     * basic constructor.
     *
     * @param publicParam a public key parameters object.
     * @param privateParam the corresponding private key parameters.
     */
    public AsymmetricCipherKeyPair(
        CipherParameters    publicParam,
        CipherParameters    privateParam)
    {
        this.publicParam = publicParam;
        this.privateParam = privateParam;
    }

    /**
     * return the public key parameters.
     *
     * @return the public key parameters.
     */
    public CipherParameters getPublic()
    {
        return publicParam;
    }

    /**
     * return the private key parameters.
     *
     * @return the private key parameters.
     */
    public CipherParameters getPrivate()
    {
        return privateParam;
    }
}
