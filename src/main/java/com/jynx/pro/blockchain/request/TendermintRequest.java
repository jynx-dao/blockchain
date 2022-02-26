package com.jynx.pro.blockchain.request;

import java.util.UUID;

public abstract class TendermintRequest {
    public String uuid = UUID.randomUUID().toString();
}