package com.personal.assistant.module.wecom;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeComCryptoServiceTest {
    @Test
    void decryptsSignedCallback() throws Exception {
        WeComProperties properties = new WeComProperties();
        properties.setCorpId("corp-test");
        properties.setToken("token-test");
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) key[index] = (byte) (index + 1);
        properties.setEncodingAesKey(Base64.getEncoder().withoutPadding().encodeToString(key));
        String xml = "<xml><FromUserName>user1</FromUserName><MsgType>text</MsgType><Content>问 项目</Content><MsgId>1</MsgId></xml>";
        String encrypted = encrypt(xml, properties.getCorpId(), key);
        String signature = sign(properties.getToken(), "100", "nonce", encrypted);

        WeComCryptoService crypto = new WeComCryptoService(properties);
        assertEquals(xml, crypto.decryptCallback(signature, "100", "nonce", encrypted));
        assertEquals("user1", crypto.parseIncoming(xml).fromUser());
    }

    private String encrypt(String message, String corpId, byte[] key) throws Exception {
        byte[] random = new byte[16];
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        byte[] receiver = corpId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + content.length + receiver.length);
        buffer.put(random).putInt(content.length).put(content).put(receiver);
        byte[] plain = buffer.array();
        int padding = 32 - plain.length % 32;
        byte[] padded = Arrays.copyOf(plain, plain.length + padding);
        Arrays.fill(padded, plain.length, padded.length, (byte) padding);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    }

    private String sign(String token, String timestamp, String nonce, String encrypted) throws Exception {
        String[] values = {token, timestamp, nonce, encrypted};
        Arrays.sort(values);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
    }
}
