package com.jynx.pro.repository;

import com.jynx.pro.constant.OracleStatus;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Oracle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OracleRepository extends JpaRepository<Oracle, UUID> {
    List<Oracle> findByMarketAndStatus(Market market, OracleStatus status);
}