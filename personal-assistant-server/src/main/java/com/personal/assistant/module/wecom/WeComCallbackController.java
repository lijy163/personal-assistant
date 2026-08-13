package com.personal.assistant.module.wecom;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/public/wecom/callback")
public class WeComCallbackController {
    private final WeComProperties properties;
    private final WeComCryptoService crypto;
    private final WeComCallbackProcessor processor;

    public WeComCallbackController(WeComProperties properties, WeComCryptoService crypto,
                                   WeComCallbackProcessor processor) {
        this.properties = properties;
        this.crypto = crypto;
        this.processor = processor;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam("msg_signature") String signature,
                         @RequestParam String timestamp, @RequestParam String nonce,
                         @RequestParam("echostr") String echo) {
        requireEnabled();
        return crypto.decryptCallback(signature, timestamp, nonce, echo);
    }

    @PostMapping(consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam("msg_signature") String signature,
                           @RequestParam String timestamp, @RequestParam String nonce,
                           @RequestBody String envelope) {
        requireEnabled();
        String encrypted = crypto.encryptedFromEnvelope(envelope);
        String plaintext = crypto.decryptCallback(signature, timestamp, nonce, encrypted);
        processor.process(crypto.parseIncoming(plaintext));
        return "success";
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new IllegalStateException("企业微信接入未启用");
    }
}

@Component
class WeComCallbackProcessor {
    private static final Logger log = LoggerFactory.getLogger(WeComCallbackProcessor.class);
    private final WeComCommandService commands;

    WeComCallbackProcessor(WeComCommandService commands) {
        this.commands = commands;
    }

    @Async
    public void process(WeComCryptoService.IncomingMessage message) {
        try {
            commands.handle(message);
        } catch (Exception exception) {
            log.error("Failed to process WeCom message {} from {}",
                    message.messageId(), message.fromUser(), exception);
        }
    }
}
