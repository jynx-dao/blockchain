package com.jynx.pro.entity;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_order", indexes = {
        @Index(name = "idx_market_id_status", columnList = "market_id,status")
})
@Accessors(chain = true)
public class Order {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "price", nullable = false)
    private Long price;
    @Column(name = "size", nullable = false)
    private Long size;
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private MarketSide side;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
}
