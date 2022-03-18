package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "jynx_config")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Config {
    @Id
    @JsonIgnore
    @Column(updatable = false, nullable = false)
    private Long id = 1L;
    @Column(name = "uuid_seed", nullable = false)
    private Long uuidSeed;
    @Column(name = "governance_token_address", nullable = false)
    private String governanceTokenAddress;
    @Column(name = "min_enactment_delay", nullable = false)
    private Long minEnactmentDelay;
    @Column(name = "min_open_delay", nullable = false)
    private Long minOpenDelay;
    @Column(name = "min_closing_delay", nullable = false)
    private Long minClosingDelay;
    @Column(name = "network_fee", nullable = false)
    private BigDecimal networkFee;
    @Column(name = "min_proposer_stake", nullable = false)
    private Long minProposerStake;
    @Column(name = "participation_threshold", nullable = false)
    private BigDecimal participationThreshold;
    @Column(name = "approval_threshold", nullable = false)
    private BigDecimal approvalThreshold;
    @Column(name = "bridge_address", nullable = false)
    private String bridgeAddress;
    @Column(name = "eth_confirmations", nullable = false)
    private Integer ethConfirmations;
    @Column(name = "active_validator_count", nullable = false)
    private Integer activeValidatorCount;
    @Column(name = "backup_validator_count", nullable = false)
    private Integer backupValidatorCount;
    @Column(name = "validator_bond", nullable = false)
    private BigDecimal validatorBond;
    @Column(name = "validator_min_delegation", nullable = false)
    private BigDecimal validatorMinDelegation;
}