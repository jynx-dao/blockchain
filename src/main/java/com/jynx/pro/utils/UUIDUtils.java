package com.jynx.pro.utils;

import com.jynx.pro.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class UUIDUtils {

    @Autowired
    private ConfigService configService;

    /**
     * Get the next deterministic UUID
     *
     * @return the {@link UUID}
     */
    public UUID next() {
        UUID uuid = UUID.nameUUIDFromBytes(configService.get().getUuidSeed().toString()
                .getBytes(StandardCharsets.UTF_8));
        configService.incrementUuidSeed();
        return uuid;
    }
}