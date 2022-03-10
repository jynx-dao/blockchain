package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jynx.pro.constant.WithdrawalStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_withdrawal")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Withdrawal {
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
    @Column(name = "destination", nullable = false)
    private String destination;
    @Column(name = "txHash")
    private String txHash;
    @Column(name = "created", nullable = false)
    private Long created;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WithdrawalStatus status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawal_batch_id")
    private WithdrawalBatch withdrawalBatch;
}
