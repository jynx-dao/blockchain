package com.jynx.pro.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

@Slf4j
public class CryptoUtilsTest {

    private final CryptoUtils cryptoUtils = new CryptoUtils();

    private static final String PRIVATE_KEY = "1498b5467a63dffa2dc9d9e069caf075d16fc33fdd4c3b01bfadae6433767d93";
    private static final String PUBLIC_KEY = "b7a3c12dc0c8c748ab07525b701122b88bd78f600c76342d27f25e5f92444cde";

    @Test
    public void testSignAndVerify() {
        String message = "hello";
        Optional<String> signatureOpt = cryptoUtils.sign(message, PRIVATE_KEY);
        Assertions.assertTrue(signatureOpt.isPresent());
        String signature = signatureOpt.get();
        boolean result = cryptoUtils.verify(signature, String.format("%s!", message), PUBLIC_KEY);
        Assertions.assertFalse(result);
        result = cryptoUtils.verify(signature, message, PUBLIC_KEY);
        Assertions.assertTrue(result);
    }
}