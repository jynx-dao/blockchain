package com.jynx.pro.entity;

import com.jynx.pro.constant.MarketSide;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
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
    @Column(name = "average_entry_price", nullable = false)
    private Long averageEntryPrice;
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private MarketSide side;
    @Column(name = "size", nullable = false)
    private Long size;
    @Column(name = "allocated_margin", nullable = false)
    private Long allocatedMargin;
    @Column(name = "liquidation_price", nullable = false)
    private Long liquidationPrice;
    @Column(name = "bankruptcy_price", nullable = false)
    private Long bankruptcyPrice;
    @Column(name = "realised_pnl", nullable = false)
    private Long realisedPnl;
    @Column(name = "unrealised_pnl", nullable = false)
    private Long unrealisedPnl;
}