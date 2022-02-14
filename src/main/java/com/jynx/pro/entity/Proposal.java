package com.jynx.pro.entity;

import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.constant.ProposalType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_proposal")
@Accessors(chain = true)
public class Proposal {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ProposalType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProposalStatus status;
    @Column(name = "linked_id", nullable = false)
    private UUID linkedId;
    @Column(name = "open_time", nullable = false)
    private Long openTime;
    @Column(name = "closing_time", nullable = false)
    private Long closingTime;
    @Column(name = "enactment_time", nullable = false)
    private Long enactmentTime;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}