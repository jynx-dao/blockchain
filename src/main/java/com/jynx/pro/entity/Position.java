package com.jynx.pro.entity;

import com.jynx.pro.constant.MarketSide;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_position")
@Accessors(chain = true)
public class Position {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "average_entry_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal averageEntryPrice;
    @Enumerated(EnumType.STRING)
    @Column(name = "side")
    private MarketSide side;
    @Column(name = "size", nullable = false, scale = 8, precision = 18)
    private BigDecimal size;
    @Column(name = "allocated_margin", nullable = false, scale = 8, precision = 18)
    private BigDecimal allocatedMargin;
    @Column(name = "liquidation_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal liquidationPrice;
    @Column(name = "bankruptcy_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal bankruptcyPrice;
    @Column(name = "realised_pnl", nullable = false, scale = 8, precision = 18)
    private BigDecimal realisedPnl;
    @Column(name = "unrealised_pnl", nullable = false, scale = 8, precision = 18)
    private BigDecimal unrealisedPnl;
    @Column(name = "leverage", nullable = false, scale = 8, precision = 18)
    private BigDecimal leverage = BigDecimal.ZERO;
    @Column(name = "latest_mark_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal latestMarkPrice = BigDecimal.ZERO;
}