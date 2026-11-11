package com.shadowcrypt.crypto.rsa;

import com.shadowcrypt.config.CryptoConstants;
import com.shadowcrypt.exception.AuthenticationFailedException;
import com.shadowcrypt.exception.CryptoOperationException;
import com.shadowcrypt.exception.MalformedPackageException;
import com.shadowcrypt.util.SecureWipe;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Objects;

/**
 * RSA-OAEP (SHA-256 for both the digest and MGF1) used exclusively to wrap the
 * one-time AES session key. The parameters are configured explicitly so provider
 * defaults can never change the format.
 */
public final class RsaOaepCipher {

    private static final OAEPParameterSpec OAEP_PARAMETERS = new OAEPParameterSpec(
            CryptoConstants.OAEP_DIGEST,
            CryptoConstants.OAEP_MGF1_PARAMETER_NAME,
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    private RsaOaepCipher() {
    }

    public static byte[] wrap(PublicKey publicKey, SecretKey sessionKey) {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(sessionKey, "sessionKey");

        byte[] rawKey = sessionKey.getEncoded();
        if (rawKey == null) {
            throw new CryptoOperationException("The session key does not expose its encoded form");
        }
        try {
            Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_PARAMETERS);
            byte[] wrapped = cipher.doFinal(rawKey);
            if (wrapped.length != CryptoConstants.RSA_WRAPPED_KEY_BYTES) {
                throw new CryptoOperationException("Unexpected wrapped-key length: " + wrapped.length
                        + " bytes (expected " + CryptoConstants.RSA_WRAPPED_KEY_BYTES + ")");
            }
            return wrapped;
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("RSA-OAEP key wrapping failed", e);
        } finally {
            SecureWipe.wipe(rawKey);
        }
    }

    public static SecretKey unwrap(PrivateKey privateKey, byte[] wrappedKey) {
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(wrappedKey, "wrappedKey");
        if (wrappedKey.length != CryptoConstants.RSA_WRAPPED_KEY_BYTES) {
            throw new MalformedPackageException("The wrapped session key must be "
                    + CryptoConstants.RSA_WRAPPED_KEY_BYTES + " bytes, got " + wrappedKey.length);
        }
        byte[] rawKey = null;
        try {
            Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMETERS);
            rawKey = cipher.doFinal(wrappedKey);
            if (rawKey.length != CryptoConstants.AES_KEY_BYTES) {
                throw new CryptoOperationException("Unexpected session-key length: " + rawKey.length + " bytes");
            }
            return new SecretKeySpec(rawKey, CryptoConstants.AES_ALGORITHM);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new AuthenticationFailedException(
                    "The private key cannot unwrap this package's session key (wrong key or corrupted data)", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("RSA-OAEP key unwrapping failed", e);
        } finally {
            SecureWipe.wipe(rawKey);
        }
    }
}
