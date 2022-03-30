package com.jynx.pro.model;

import com.jynx.pro.constant.WebSocketChannelType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Accessors(chain = true)
public class WebSocketSubscription {

    private List<WebSocketChannel> channels = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class WebSocketChannel {
        private WebSocketChannelType type;
        private String publicKey;
        private UUID marketId;
    }
}