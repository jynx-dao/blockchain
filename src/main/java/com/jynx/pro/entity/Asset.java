package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.AssetType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_asset")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Asset {
    @Id
    private UUID id;
    @Column(name = "name", nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AssetType type;
    @Column(name = "address")
    private String address;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssetStatus status;
    @Column(name = "decimal_places", nullable = false)
    private Integer decimalPlaces;
    @Column(name = "treasury_balance", nullable = false)
    private BigDecimal treasuryBalance = BigDecimal.ZERO;
}
