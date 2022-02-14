package com.jynx.pro.repository;

import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.entity.Proposal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByStatus(ProposalStatus status, Pageable pageable);
    List<Proposal> findByStatus(ProposalStatus status);
}