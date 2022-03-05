package com.jynx.pro.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.Signer;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
public class CryptoUtils {

    /**
     * Sign message using ED25519 key
     *
     * @param message the message the sign
     * @param privateKey the key to use
     *
     * @return the signature
     */
    public Optional<String> sign(
            final String message,
            final String privateKey
    ) {
        try {
            byte[] messageAsBytes = message.getBytes(StandardCharsets.UTF_8);
            Signer signer = new Ed25519Signer();
            signer.init(true, new Ed25519PrivateKeyParameters(Hex.decodeHex(privateKey), 0));
            signer.update(messageAsBytes, 0, messageAsBytes.length);
            byte[] signature = signer.generateSignature();
            return Optional.of(Hex.encodeHexString(signature));
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Verify ED25519 signature using message and public key
     *
     * @param sig the signature
     * @param message the signed message
     * @param publicKey the public key
     *
     * @return true / false
     */
    public boolean verify(
            final String sig,
            final String message,
            final String publicKey
    ) {
        try {
            byte[] messageAsBytes = message.getBytes(StandardCharsets.UTF_8);
            Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(Hex.decodeHex(publicKey), 0));
            verifier.update(messageAsBytes, 0, messageAsBytes.length);
            return verifier.verifySignature(Hex.decodeHex(sig));
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}