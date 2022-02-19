package com.jynx.pro.repository;

import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Position;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    Optional<Position> findByUserAndMarket(User user, Market market);
    List<Position> findByMarket(Market market);
}