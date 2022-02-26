package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class AmendOrderRequest extends SignedRequest {
    private UUID id;
    private BigDecimal price;
    private BigDecimal quantity;
}