package com.jynx.pro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Oracle {
    @Id
    private UUID id;
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OracleType type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OracleStatus status;
    @Column(name = "key", nullable = false)
    private String key;
}