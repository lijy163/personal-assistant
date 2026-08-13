package com.personal.assistant.module.wecom;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Service
public class WeComCryptoService {
    private final WeComProperties properties;

    public WeComCryptoService(WeComProperties properties) {
        this.properties = properties;
    }

    public String decryptCallback(String signature, String timestamp, String nonce, String encrypted) {
        if (!sign(timestamp, nonce, encrypted).equalsIgnoreCase(signature)) {
            throw new IllegalArgumentException("企业微信回调签名校验失败");
        }
        try {
            byte[] key = aesKey();
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            byte[] padded = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            int padding = padded[padded.length - 1] & 0xff;
            if (padding < 1 || padding > 32 || padding > padded.length) {
                throw new IllegalArgumentException("企业微信消息填充无效");
            }
            for (int index = padded.length - padding; index < padded.length; index++) {
                if ((padded[index] & 0xff) != padding) throw new IllegalArgumentException("企业微信消息填充无效");
            }
            byte[] plain = Arrays.copyOf(padded, padded.length - padding);
            ByteBuffer buffer = ByteBuffer.wrap(plain);
            buffer.position(16);
            int length = buffer.getInt();
            if (length < 0 || length > buffer.remaining()) throw new IllegalArgumentException("企业微信消息长度无效");
            byte[] message = new byte[length];
            buffer.get(message);
            byte[] corpId = new byte[buffer.remaining()];
            buffer.get(corpId);
            if (!properties.getCorpId().equals(new String(corpId, StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("企业微信 CorpId 不匹配");
            }
            return new String(message, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException("企业微信消息解密失败", exception);
        }
    }

    public String encryptedFromEnvelope(String xml) {
        return text(parse(xml), "Encrypt");
    }

    public IncomingMessage parseIncoming(String xml) {
        Document document = parse(xml);
        return new IncomingMessage(text(document, "FromUserName"), text(document, "MsgType"),
                text(document, "Content"), text(document, "MsgId"));
    }

    private String sign(String timestamp, String nonce, String encrypted) {
        try {
            String[] values = {properties.getToken(), timestamp, nonce, encrypted};
            Arrays.sort(values);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算企业微信签名", exception);
        }
    }

    private byte[] aesKey() {
        return Base64.getDecoder().decode(properties.getEncodingAesKey() + "=");
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("企业微信 XML 格式无效", exception);
        }
    }

    private String text(Document document, String tag) {
        var nodes = document.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    public record IncomingMessage(String fromUser, String messageType, String content, String messageId) {
    }
}
