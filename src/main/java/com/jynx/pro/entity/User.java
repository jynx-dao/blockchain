package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_user")
@Accessors(chain = true)
public class User {
    @Id
    private UUID id;
    @Column(name = "public_key", nullable = false)
    private String publicKey;
    @Column(name = "username", nullable = false)
    private String username;
    @Column(name = "reputation_score", nullable = false)
    private Long reputationScore = 1L;
}