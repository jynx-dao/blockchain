package com.jynx.pro.controller;

import com.jynx.pro.entity.Order;
import com.jynx.pro.request.AmendOrderRequest;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

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

//    @PostMapping("/batch")
//    public ResponseEntity<List<Order>> createMany(
//            @RequestBody CreateManyOrdersRequest request
//    ) {
//        return ResponseEntity.ok(tendermintClient.createManyOrders(request));
//    }
//
//    @DeleteMapping("/batch")
//    public ResponseEntity<List<Order>> cancelMany(
//            @RequestBody CancelManyOrdersRequest request
//    ) {
//        return ResponseEntity.ok(orderService.cancelMany(request));
//    }
//
//    @PutMapping("/batch")
//    public ResponseEntity<List<Order>> amendMany(
//            @RequestBody AmendManyOrdersRequest request
//    ) {
//        return ResponseEntity.ok(orderService.amendMany(request));
//    }
}
