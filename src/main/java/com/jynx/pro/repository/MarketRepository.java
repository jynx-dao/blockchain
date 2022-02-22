package com.jynx.pro.repository;

import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketRepository extends JpaRepository<Market, UUID> {
    List<Market> findByStatusIn(List<MarketStatus> statusList);
}