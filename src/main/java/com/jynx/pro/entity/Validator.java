package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
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
    @Column(name = "eth_address", nullable = false)
    private String ethAddress;
    @Column(name = "delegation", nullable = false)
    private BigDecimal delegation = BigDecimal.ZERO;
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
    @Column(name = "priority", nullable = false)
    private Integer priority;
}