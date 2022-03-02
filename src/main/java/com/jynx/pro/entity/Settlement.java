package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Table
@Entity(name = "jynx_settlement")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Settlement {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oracle_id", nullable = false)
    private Oracle oracle;
    @Column(name = "settlement_interval", nullable = false)
    private Long settlementInterval;
    @Column(name = "value", nullable = false, scale = 8, precision = 18)
    private BigDecimal value;
}