package com.jynx.pro.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

@Data
@Accessors(chain = true)
public class SignBridgeUpdateRequest {
    private UUID bridgeUpdateId;
    private UUID validatorId;
    private String signature;
}