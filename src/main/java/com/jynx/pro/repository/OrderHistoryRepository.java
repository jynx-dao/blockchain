package com.jynx.pro.repository;

import com.jynx.pro.entity.OrderHistory;
import org.springframework.stereotype.Repository;

@Repository
public class OrderHistoryRepository extends EntityRepository<OrderHistory> {
    @Override
    public Class<OrderHistory> getType() {
        return OrderHistory.class;
    }
}