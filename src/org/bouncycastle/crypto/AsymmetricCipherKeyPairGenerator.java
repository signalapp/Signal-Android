package org.bouncycastle.crypto;

/**
 * interface that a public/private key pair generator should conform to.
 */
public interface AsymmetricCipherKeyPairGenerator
{
    /**
     * intialise the key pair generator.
     *
     * @param param the parameters the key pair is to be initialised with.
     */
    public void init(KeyGenerationParameters param);

    /**
     * return an AsymmetricCipherKeyPair containing the generated keys.
     *
     * @return an AsymmetricCipherKeyPair containing the generated keys.
     */
    public AsymmetricCipherKeyPair generateKeyPair();
}

