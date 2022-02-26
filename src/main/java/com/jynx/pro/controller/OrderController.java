package com.jynx.pro.controller;

import com.jynx.pro.entity.Order;
import com.jynx.pro.request.AmendOrderRequest;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
import com.jynx.pro.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> create(
            @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.create(request));
    }

    @DeleteMapping
    public ResponseEntity<Order> cancel(
            @RequestBody CancelOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.cancel(request));
    }

    @PutMapping
    public ResponseEntity<Order> amend(
            @RequestBody AmendOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.amend(request));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Order>> createMany(
            @RequestBody List<CreateOrderRequest> request
    ) {
        return ResponseEntity.ok(orderService.createMany(request));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<List<Order>> cancelMany(
            @RequestBody List<CancelOrderRequest> request
    ) {
        return ResponseEntity.ok(orderService.cancelMany(request));
    }

    @PutMapping("/batch")
    public ResponseEntity<List<Order>> amendMany(
            @RequestBody List<AmendOrderRequest> request
    ) {
        return ResponseEntity.ok(orderService.amendMany(request));
    }
}
