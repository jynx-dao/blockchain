package com.jynx.pro.repository;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusInAndTypeAndMarket(List<OrderStatus> statusList, OrderType type, Market market);
    List<Order> findByStatusInAndTypeAndMarketAndUser(List<OrderStatus> statusList, OrderType type, Market market, User user);
    int countByMarketAndPriceAndSideAndStatusInAndType(Market market, BigDecimal price, MarketSide side, List<OrderStatus> statusList, OrderType limit);
    List<Order> findByUser(User user);
}