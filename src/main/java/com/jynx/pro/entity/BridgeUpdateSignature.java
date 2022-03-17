package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_bridge_update_signature")
@Accessors(chain = true)
public class BridgeUpdateSignature {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bridge_update_id", nullable = false)
    private BridgeUpdate bridgeUpdate;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validator_id", nullable = false)
    private Validator validator;
    @Column(name = "signature", nullable = false)
    private String signature;
}