package de.gdata.messaging;

/**
 * Created by jan on 09.02.15.
 */

import android.util.Log;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.Cipher;

public class TextEncrypter {

  private static String pubKeyMod = "125405990079924322664411142883218485720317509175045677104597790" +
      "044743495204260242800188371314614590462417174367143441555403963732738764156689901141909444364" +
      "887572721494649465723322080437916965884233225821540546260027188711708224282472429616204762197" +
      "784315314122049598666862529574023974492430645419481223896683";
  private static String pubKeyExp = "65537";

  public byte[] encryptData(String data) {

    byte[] dataToEncrypt = data.getBytes();
    byte[] encryptedData = null;
    try {
      RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(new BigInteger(pubKeyMod), new BigInteger(
          pubKeyExp));
      KeyFactory fact = KeyFactory.getInstance("RSA");
      PublicKey publicKey = fact.generatePublic(rsaPublicKeySpec);

      Cipher cipher = Cipher.getInstance("RSA");
      cipher.init(Cipher.ENCRYPT_MODE, publicKey);
      encryptedData = cipher.doFinal(dataToEncrypt);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return encryptedData;
  }

}

