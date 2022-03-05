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

    private static final String PRIVATE_KEY2 = "50269a7ea8cb30117b3b94f3e001c615e901139305e0ae45c984fbe5e9ca974fe9aef489a390b519722a32d29ca4261a83fbb0acad8519ed2b3d72c7642bcc6e";
    private static final String PUBLIC_KEY2 = "e9aef489a390b519722a32d29ca4261a83fbb0acad8519ed2b3d72c7642bcc6e";

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

    @Test
    public void testSignAndVerifyWith64ByteKey() {
        String message = "hello";
        Optional<String> signatureOpt = cryptoUtils.sign(message, PRIVATE_KEY2);
        Assertions.assertTrue(signatureOpt.isPresent());
        String signature = signatureOpt.get();
        boolean result = cryptoUtils.verify(signature, String.format("%s!", message), PUBLIC_KEY2);
        Assertions.assertFalse(result);
        result = cryptoUtils.verify(signature, message, PUBLIC_KEY2);
        Assertions.assertTrue(result);
    }
}