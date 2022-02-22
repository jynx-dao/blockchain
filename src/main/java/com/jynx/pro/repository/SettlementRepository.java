package com.jynx.pro.repository;

import com.jynx.pro.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findBySettlementInterval(Long interval);
}