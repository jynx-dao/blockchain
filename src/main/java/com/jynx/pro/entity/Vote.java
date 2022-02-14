package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "vote")
@Accessors(chain = true)
public class Vote {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "in_favour", nullable = false)
    private Boolean inFavour;
}