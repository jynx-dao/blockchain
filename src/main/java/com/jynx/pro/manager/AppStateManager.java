package com.jynx.pro.manager;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

@Component
public class AppStateManager {

    @Getter
    private int appState;

    /**
     * Update the app state hash code
     *
     * @param newState the new app state
     */
    public void update(
            final int newState
    ) {
        appState = List.of(appState, newState).hashCode();
    }

    /**
     * Get the app state as a byte-array
     *
     * @return the app state in byte-array format
     */
    public byte[] getStateAsBytes() {
        return BigInteger.valueOf(appState).toByteArray();
    }
}