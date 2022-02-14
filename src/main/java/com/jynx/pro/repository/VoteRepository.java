package com.jynx.pro.repository;

import com.jynx.pro.entity.Proposal;
import com.jynx.pro.entity.User;
import com.jynx.pro.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    List<Vote> findByProposalAndUser(Proposal proposal, User user);
    List<Vote> findByProposal(Proposal proposal);
}