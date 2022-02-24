package com.jynx.pro.controller;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Position;
import com.jynx.pro.entity.User;
import com.jynx.pro.response.MultipleItemResponse;
import com.jynx.pro.response.SingleItemResponse;
import com.jynx.pro.service.AccountService;
import com.jynx.pro.service.OrderService;
import com.jynx.pro.service.PositionService;
import com.jynx.pro.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

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

    @GetMapping("/{userId}/accounts")
    public ResponseEntity<MultipleItemResponse<Account>> getAccounts(
            @PathVariable("userId") UUID userId
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Account>().setItems(accountService.getByUserId(userId)));
    }

    @GetMapping("/{userId}/orders")
    public ResponseEntity<MultipleItemResponse<Order>> getOrders(
            @PathVariable("userId") UUID userId
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Order>().setItems(orderService.getByUserId(userId)));
    }

    @GetMapping("/{userId}/positions")
    public ResponseEntity<MultipleItemResponse<Position>> getPositions(
            @PathVariable("userId") UUID userId
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Position>().setItems(positionService.getByUserId(userId)));
    }
}