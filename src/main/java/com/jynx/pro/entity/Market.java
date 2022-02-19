package com.jynx.pro.entity;

import com.jynx.pro.constant.MarketStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_market")
@Accessors(chain = true)
public class Market {
    @Id
    private UUID id;
    @Column(name = "name", nullable = false)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_asset_id", nullable = false)
    private Asset settlementAsset;
    @Column(name = "initial_margin", nullable = false)
    private BigDecimal initialMargin;
    @Column(name = "maintenance_margin", nullable = false)
    private BigDecimal maintenanceMargin;
    @Column(name = "tick_size", nullable = false)
    private Integer tickSize;
    @Column(name = "step_size", nullable = false)
    private Integer stepSize;
    @Column(name = "settlement_frequency", nullable = false)
    private Integer settlementFrequency;
    @Column(name = "maker_fee", nullable = false, scale = 8, precision = 18)
    private BigDecimal makerFee;
    @Column(name = "taker_fee", nullable = false, scale = 8, precision = 18)
    private BigDecimal takerFee;
    @Column(name = "open_volume", nullable = false, scale = 8, precision = 18)
    private BigDecimal openVolume = BigDecimal.ZERO;
    @Column(name = "volume_24h", nullable = false, scale = 8, precision = 18)
    private BigDecimal volume24h = BigDecimal.ZERO;
    @Column(name = "last_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal lastPrice = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MarketStatus status;
    @Column(name = "pending_initial_margin", scale = 8, precision = 18)
    private BigDecimal pendingInitialMargin;
    @Column(name = "pending_maintenance_margin", scale = 8, precision = 18)
    private BigDecimal pendingMaintenanceMargin;
    @Column(name = "pending_tick_size")
    private Integer pendingTickSize;
    @Column(name = "pending_step_size")
    private Integer pendingStepSize;
    @Column(name = "pending_settlement_frequency")
    private Integer pendingSettlementFrequency;
    @Column(name = "pending_maker_fee", scale = 8, precision = 18)
    private BigDecimal pendingMakerFee;
    @Column(name = "pending_taker_fee", scale = 8, precision = 18)
    private BigDecimal pendingTakerFee;
}