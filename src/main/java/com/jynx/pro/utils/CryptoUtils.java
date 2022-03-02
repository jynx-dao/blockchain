package com.jynx.pro.utils;

import lombok.extern.slf4j.Slf4j;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
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
            EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            EdDSAPrivateKeySpec privKey = new EdDSAPrivateKeySpec(Hex.decodeHex(privateKey), spec);
            PrivateKey signingKey = new EdDSAPrivateKey(privKey);
            signature.initSign(signingKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Optional.of(Hex.encodeHexString(signature.sign()));
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
            EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(Hex.decodeHex(publicKey), spec);
            PublicKey vKey = new EdDSAPublicKey(pubKey);
            signature.initVerify(vKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Hex.decodeHex(sig));
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}