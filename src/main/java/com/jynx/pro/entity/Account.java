package com.jynx.pro.entity;

import com.jynx.pro.constant.AccountType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_account")
@Accessors(chain = true)
public class Account {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountType type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "balance", nullable = false)
    private Long balance;
}