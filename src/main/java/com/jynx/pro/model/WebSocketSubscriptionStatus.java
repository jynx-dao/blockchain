package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebSocketSubscriptionStatus {
    private boolean connected;
}