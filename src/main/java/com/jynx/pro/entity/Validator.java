package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_validator")
@Accessors(chain = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Validator {
    @Id
    private UUID id;
    @Column(name = "address")
    private String address;
    @Column(name = "public_key", nullable = false)
    private String publicKey;
    @Column(name = "active", nullable = false)
    private Boolean active;
}