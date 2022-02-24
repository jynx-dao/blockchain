package com.jynx.pro.repository;

import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByMakerOrderUserAndMarket(User user, Market market);
    List<Trade> findByTakerOrderUserAndMarket(User user, Market market);
    List<Trade> findByMarketId(UUID marketId);
    List<Trade> findByTakerOrderUserIdAndMarketId(UUID userId, UUID marketId);
}