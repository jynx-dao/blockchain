package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_validator")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Validator {
    @Id
    private UUID id;
    @Column(name = "public_key", nullable = false)
    private String publicKey;
    @Column(name = "address", nullable = false)
    private String address;
    @Column(name = "eth_address")
    private String ethAddress;
    @Column(name = "delegation", nullable = false)
    private BigDecimal delegation = BigDecimal.ZERO;
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
    @Column(name = "priority", nullable = false)
    private Integer priority;
    @Column(name = "score", nullable = false)
    private Integer score = 0;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}