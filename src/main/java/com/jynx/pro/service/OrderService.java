package com.jynx.pro.service;

import com.jynx.pro.entity.Order;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private static final int MAX_BULK = 25;

    public Order create(
            final Order order
    ) {
        // TODO - create order
        return null;
    }

    public Order cancel(
            final UUID id
    ) {
        // TODO - cancel order
        return null;
    }

    public Order amend(
            final Order order
    ) {
        // TODO - amend order
        return null;
    }

    public List<Order> createMany(
            final List<Order> orders
    ) {
        if(orders.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return orders.stream().map(this::create).collect(Collectors.toList());
    }

    public List<Order> amendMany(
            final List<Order> orders
    ) {
        if(orders.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return orders.stream().map(this::amend).collect(Collectors.toList());
    }

    public List<Order> cancelMany(
            final List<UUID> ids
    ) {
        if(ids.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return ids.stream().map(this::cancel).collect(Collectors.toList());
    }
}