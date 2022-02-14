package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_oracle")
@Accessors(chain = true)
public class Oracle {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OracleType type;
    @Column(name = "identifier", nullable = false)
    private String identifier;
}