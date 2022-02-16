package com.jynx.pro.entity;

import com.jynx.pro.constant.Blockchain;
import com.jynx.pro.constant.EventType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_event")
@Accessors(chain = true)
public class Event {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "blockchain", nullable = false)
    private Blockchain blockchain;
    @Column(name = "hash", nullable = false)
    private String hash;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private EventType type;
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = false;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;
}