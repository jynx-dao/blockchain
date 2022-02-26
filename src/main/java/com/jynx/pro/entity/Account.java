package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_account")
@Accessors(chain = true)
public class Account {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;
    @Column(name = "balance", nullable = false, scale = 8, precision = 18)
    private BigDecimal balance;
    @Column(name = "available_balance", nullable = false, scale = 8, precision = 18)
    private BigDecimal availableBalance;
    @Column(name = "margin_balance", nullable = false, scale = 8, precision = 18)
    private BigDecimal marginBalance;
    @Column(name = "oracle_bond", nullable = false, scale = 8, precision = 18)
    private BigDecimal oracleBond = BigDecimal.ZERO;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = true)
    private Asset asset;
}