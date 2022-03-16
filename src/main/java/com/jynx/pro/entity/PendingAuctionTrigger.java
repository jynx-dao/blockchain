package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_pending_auction_trigger")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PendingAuctionTrigger {
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