package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jynx.pro.constant.MarketSide;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_trade")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Trade {
    @Id
    private UUID id;
    @Column(name = "quantity", nullable = false, scale = 8, precision = 18)
    private BigDecimal quantity;
    @Column(name = "price", nullable = false, scale = 8, precision = 18)
    private BigDecimal price;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maker_order_id", nullable = false)
    private Order makerOrder;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taker_order_id", nullable = false)
    private Order takerOrder;
    @Column(name = "executed", nullable = false)
    private Long executed;
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private MarketSide side;
}
