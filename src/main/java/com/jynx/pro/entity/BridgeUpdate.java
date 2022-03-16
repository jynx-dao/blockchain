package com.jynx.pro.entity;

import com.jynx.pro.constant.BridgeUpdateType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_bridge_update")
@Accessors(chain = true)
public class BridgeUpdate {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;
    @Column(name = "nonce", nullable = false)
    private String nonce;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BridgeUpdateType type;
    @Column(name = "complete", nullable = false)
    private Boolean complete = false;
}