package com.jynx.pro.entity;

import com.jynx.pro.constant.BlockValidatorStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_block_validator")
@Accessors(chain = true)
public class BlockValidator {
    @Id
    private UUID id;
    @Column(name = "block_height", nullable = false)
    private Long blockHeight;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validator_id", nullable = false)
    private Validator validator;
    @Column(name = "delegation", nullable = false)
    private BigDecimal delegation;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BlockValidatorStatus status;
    @Column(name = "sufficient_eth_balance")
    private Boolean sufficientEthBalance;
    @Column(name = "signed_block")
    private Boolean signedBlock;
    @Column(name = "duplicate_vote")
    private Boolean duplicateVote;
    @Column(name = "light_client_attack")
    private Boolean lightClientAttack;
}