package com.jynx.pro.request;

import java.util.UUID;

public abstract class TendermintRequest {
    public String uuid = UUID.randomUUID().toString();
}