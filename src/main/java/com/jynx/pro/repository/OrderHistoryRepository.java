package com.jynx.pro.repository;

import com.jynx.pro.entity.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {
}