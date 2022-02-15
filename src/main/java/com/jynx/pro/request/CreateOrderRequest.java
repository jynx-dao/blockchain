package com.jynx.pro.request;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class CreateOrderRequest extends SignedRequest {
    private OrderType type;
    private BigDecimal price;
    private BigDecimal size;
    private UUID marketId;
    private MarketSide side;
}