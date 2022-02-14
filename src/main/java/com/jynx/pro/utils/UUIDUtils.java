package com.jynx.pro.utils;

import com.jynx.pro.service.ConfigService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class UUIDUtils {

    private final ConfigService configService;

    public UUIDUtils(ConfigService configService) {
        this.configService = configService;
    }

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