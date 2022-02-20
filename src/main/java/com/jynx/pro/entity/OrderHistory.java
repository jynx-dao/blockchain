package com.jynx.pro.entity;

import com.jynx.pro.constant.OrderAction;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Table
@Entity(name = "jynx_order_history")
@Accessors(chain = true)
public class OrderHistory {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id")
    private Trade trade;
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private OrderAction action;
    @Column(name = "from_price", scale = 8, precision = 18)
    private BigDecimal fromPrice;
    @Column(name = "to_price", scale = 8, precision = 18)
    private BigDecimal toPrice;
    @Column(name = "from_size", scale = 8, precision = 18)
    private BigDecimal fromSize;
    @Column(name = "to_size", scale = 8, precision = 18)
    private BigDecimal toSize;
    @Column(name = "updated", nullable = false)
    private Long updated;
}
