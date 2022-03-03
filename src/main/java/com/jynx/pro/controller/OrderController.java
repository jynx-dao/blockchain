package com.jynx.pro.controller;

import com.jynx.pro.entity.Order;
import com.jynx.pro.request.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/order")
public class OrderController extends AbstractController {

    @PostMapping
    public ResponseEntity<Order> create(
            @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.createOrder(request).getItem());
    }

    @DeleteMapping
    public ResponseEntity<Order> cancel(
            @RequestBody CancelOrderRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.cancelOrder(request).getItem());
    }

    @PutMapping
    public ResponseEntity<Order> amend(
            @RequestBody AmendOrderRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.amendOrder(request).getItem());
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Order>> createMany(
            @RequestBody BulkCreateOrderRequest request
    ) {
        return ResponseEntity.ok(Arrays.asList(tendermintClient.createOrderMany(request).getItem()));
    }

    @PutMapping("/batch")
    public ResponseEntity<List<Order>> amendMany(
            @RequestBody BulkAmendOrderRequest request
    ) {
        return ResponseEntity.ok(Arrays.asList(tendermintClient.amendOrderMany(request).getItem()));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<List<Order>> cancelMany(
            @RequestBody BulkCancelOrderRequest request
    ) {
        return ResponseEntity.ok(Arrays.asList(tendermintClient.cancelOrderMany(request).getItem()));
    }
}
