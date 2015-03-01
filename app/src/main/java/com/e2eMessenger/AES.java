package com.e2eMessenger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class is used for encrypting byte arrays using AES-GCM.
 *
 * <p>The implementation is provided by Bouncy Castle.</p>
 *
 * @author Jack Hindmarch
 */
public class AES
{
	/**
	 * Algorithm used is AES with Galois/Counter Mode, with no padding.
	 */
	public static final String TRANSFORMATION = "AES/GCM/NoPadding";

	/**
	 * Provider of the algorithm implementation: Bouncy Castle.
	 */
	public static final String PROVIDER = "BC"; // Bouncy Castle

	/**
	 * Size of the initialisation vector: 96 bytes.
	 */
	public static final int IV_SIZE = 96;

	private byte[] IV;
	private SecretKeySpec key;

	/**
	 * @param keyString - the key to be used, in string format.
	 */
	public AES(String keyString)
	{
		// add new security provider
		Security.addProvider(new BouncyCastleProvider());

		// set the key
		setKey(keyString);
	}

	/**
	 * Generates a random initialisation vector needed for
	 * ciphertext randomness and then encrypts the plaintext
	 * using the shared key.
	 *
	 * <p>Make sure to get the IV after executing this method,
	 * as it is needed for decryption.</p>
	 *
	 * @param P - plaintext data to encrypt.
	 * @return - ciphertext data.
	 */
	public byte[] encrpyt(byte[] P) throws Exception
	{
		IV = new byte[IV_SIZE];
		new SecureRandom().nextBytes(IV);

		// initialise cipher object to use AES-GCM
		Cipher in = null;
		byte[] C = null;

		in = Cipher.getInstance(TRANSFORMATION, PROVIDER);
		in.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(IV));
		C = in.doFinal(P);

		// encrypt plaintext message
		return C;
	}

	/**
	 * @param C - ciphertext data to decrypt.
	 * @param IV - initialisation vector used during
	 * @return - bytes of decrypted string
	 */
	public byte[] decrypt(byte[] C, byte[] IV) throws Exception
	{
		// initialise cipher object to use AES GCM
		Cipher out = null;
		byte[] P = null;

		out = Cipher.getInstance(TRANSFORMATION, PROVIDER);
		out.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(IV));
		P = out.doFinal(C);

		return P;
	}

	/**
	 * Sets the key to be used during encrypt and decrypt operations.
	 *
	 * @param keyString - the key to be used, in string format.
	 */
	public void setKey(String keyString)
	{
		BigInteger keyInt = new BigInteger(keyString);
		byte[] K = keyInt.toByteArray();
		this.key = new SecretKeySpec(K, "AES");
	}

	/**
	 * @return - the initialisation vector to be generated after executing encrypt method.
	 */
	public byte[] getIV()
	{
		return IV;
	}
}
