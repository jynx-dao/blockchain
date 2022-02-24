package com.jynx.pro.repository;

import com.jynx.pro.entity.Deposit;
import com.jynx.pro.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {
    Optional<Deposit> findByEvent(Event event);
    List<Deposit> findByAssetIdAndUserId(UUID assetId, UUID userId);
}