package com.jynx.pro.repository;

import com.jynx.pro.entity.AuctionTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionTriggerRepository extends JpaRepository<AuctionTrigger, UUID> {
}