package com.jynx.pro.entity;

import com.jynx.pro.constant.TransactionType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_transaction")
@Accessors(chain = true)
public class Transaction {
    @Id
    private UUID id;
    @Column(name = "amount", nullable = false, scale = 8, precision = 18)
    private BigDecimal amount;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;
    @Column(name = "tx_has")
    private String txHash;
    @Column(name = "timestamp", nullable = false)
    private Long timestamp;
}
