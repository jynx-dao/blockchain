package com.jynx.pro.entity;

import com.jynx.pro.constant.OracleStatus;
import com.jynx.pro.constant.OracleType;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OracleStatus status;
}