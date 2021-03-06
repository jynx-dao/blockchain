package com.jynx.pro.request;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderTag;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.constant.StopTrigger;
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
    private BigDecimal quantity;
    private UUID marketId;
    private MarketSide side;
    private Boolean postOnly;
    private Boolean reduceOnly;
    private OrderTag tag;
    private StopTrigger stopTrigger;
}