package com.jynx.pro.repository;

import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusInAndTypeAndMarket(List<OrderStatus> statusList, OrderType type, Market market);
}