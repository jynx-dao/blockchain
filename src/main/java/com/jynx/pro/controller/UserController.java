package com.jynx.pro.controller;

import com.jynx.pro.entity.*;
import com.jynx.pro.response.MultipleItemResponse;
import com.jynx.pro.response.SingleItemResponse;
import com.jynx.pro.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private TradeService tradeService;

    @GetMapping("/{id}")
    public ResponseEntity<SingleItemResponse<User>> getById(
            @PathVariable("id") UUID userId
    ) {
        return ResponseEntity.ok(new SingleItemResponse<User>().setItem(userService.getById(userId)));
    }

    @GetMapping("/{publicKey}")
    public ResponseEntity<SingleItemResponse<User>> getByPublicKey(
            @PathVariable("publicKey") String publicKey
    ) {
        return ResponseEntity.ok(new SingleItemResponse<User>().setItem(userService.getByPublicKey(publicKey)));
    }

    @GetMapping("/{id}/accounts")
    public ResponseEntity<MultipleItemResponse<Account>> getAccounts(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Account>().setItems(accountService.getByUserId(id)));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<MultipleItemResponse<Order>> getOrders(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Order>().setItems(orderService.getByUserId(id)));
    }

    @GetMapping("/{id}/trades")
    public ResponseEntity<MultipleItemResponse<Trade>> getTrades(
            @PathVariable("id") UUID id,
            @RequestParam("marketId") UUID marketId
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Trade>().setItems(tradeService.getByUserIdAndMarketId(id, marketId)));
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<MultipleItemResponse<Position>> getPositions(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Position>().setItems(positionService.getByUserId(id)));
    }
}