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
    @Column(name = "margin_requirement", nullable = false)
    private BigDecimal marginRequirement;
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
    @Column(name = "liquidation_fee", nullable = false, scale = 8, precision = 18)
    private BigDecimal liquidationFee;
    @Column(name = "open_volume", nullable = false, scale = 8, precision = 18)
    private BigDecimal openVolume = BigDecimal.ZERO;
    @Column(name = "volume_24h", nullable = false, scale = 8, precision = 18)
    private BigDecimal volume24h = BigDecimal.ZERO;
    @Column(name = "last_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal lastPrice = BigDecimal.ZERO;
    @Column(name = "mark_price", nullable = false, scale = 8, precision = 18)
    private BigDecimal markPrice = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MarketStatus status;
    @Column(name = "pending_margin_requirement", scale = 8, precision = 18)
    private BigDecimal pendingMarginRequirement;
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
    @Column(name = "pending_liquidation_fee", scale = 8, precision = 18)
    private BigDecimal pendingLiquidationFee;
    @Column(name = "insurance_fund", scale = 8, precision = 18)
    private BigDecimal insuranceFund = BigDecimal.ZERO;
    @Column(name = "last_settlement", nullable = false)
    private Long lastSettlement;
    @Column(name = "settlement_count", nullable = false)
    private Long settlementCount = 0L;
    @Column(name = "oracle_bond", scale = 8, precision = 18)
    private BigDecimal oracleBond;
    @Column(name = "oracle_slashing_ratio", scale = 8, precision = 18)
    private BigDecimal oracleSlashingRatio;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oracle_id", nullable = false)
    private Oracle oracle;
}