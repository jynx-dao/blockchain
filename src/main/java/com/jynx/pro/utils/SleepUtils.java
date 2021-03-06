package com.jynx.pro.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SleepUtils {

    /**
     * Pause the current thread
     *
     * @param millis duration to pause for in milliseconds
     */
    public void sleep(
            final long millis
    ) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}