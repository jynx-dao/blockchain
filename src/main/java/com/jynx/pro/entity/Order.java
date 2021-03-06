package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jynx.pro.constant.*;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_order", indexes = {
        @Index(name = "idx_market_id_status", columnList = "market_id,status")
})
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
    @Column(name = "price", scale = 8, precision = 18)
    private BigDecimal price;
    @Column(name = "quantity", nullable = false, scale = 8, precision = 18)
    private BigDecimal quantity;
    @Column(name = "remaining_quantity", nullable = false, scale = 8, precision = 18)
    private BigDecimal remainingQuantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private MarketSide side;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    @Column(name = "rejected_reason")
    private String rejectedReason;
    @Column(name = "post_only")
    private Boolean postOnly = false;
    @Column(name = "reduce_only")
    private Boolean reduceOnly = false;
    @Column(name = "priority", nullable = false)
    private Long priority;
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false)
    private OrderTag tag;
    @Enumerated(EnumType.STRING)
    @Column(name = "stop_trigger")
    private StopTrigger stopTrigger;
}
