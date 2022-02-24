package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_auction_trigger")
@Accessors(chain = true)
public class AuctionTrigger {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
    @Column(name = "depth", nullable = false)
    private BigDecimal depth;
    @Column(name = "open_volume_ratio", nullable = false)
    private BigDecimal openVolumeRatio;
}