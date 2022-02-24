package com.jynx.pro.repository;

import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Position;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    Optional<Position> findByUserAndMarket(User user, Market market);
    List<Position> findByMarket(Market market);
    List<Position> findByMarketAndSizeGreaterThan(Market market, BigDecimal size);
    List<Position> findByIdIn(List<UUID> liquidatedPositionIds);
    List<Position> findByUser(User user);
}