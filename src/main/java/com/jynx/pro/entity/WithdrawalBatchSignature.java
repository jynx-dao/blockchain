package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_withdrawal_signature")
@Accessors(chain = true)
public class WithdrawalBatchSignature {
    @Id
    private UUID id;
    @Column(name = "signature", nullable = false)
    private String signature;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validator_id", nullable = false)
    private Validator validator;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawal_batch_id", nullable = false)
    private WithdrawalBatch withdrawalBatch;
}