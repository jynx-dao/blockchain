package com.jynx.pro.repository;

import com.jynx.pro.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
}