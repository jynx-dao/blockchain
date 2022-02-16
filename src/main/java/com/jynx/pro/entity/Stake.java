package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_stake")
@Accessors(chain = true)
public class Stake {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "amount", nullable = false, scale = 8, precision = 18)
    private BigDecimal amount = BigDecimal.ZERO;
}