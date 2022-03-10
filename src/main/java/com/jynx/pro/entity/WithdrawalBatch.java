package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_withdrawal_batch")
@Accessors(chain = true)
public class WithdrawalBatch {
    @Id
    private UUID id;
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;
    @Column(name = "tx_hash")
    private String txHash;
    @Column(name = "created", nullable = false)
    private Long created;
    @Column(name = "nonce", nullable = false)
    private String nonce;
}