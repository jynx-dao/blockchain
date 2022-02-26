package com.jynx.pro.controller;

import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/user")
public class UserController extends AbstractController {

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(
            @PathVariable("id") UUID userId
    ) {
        return ResponseEntity.ok(readOnlyRepository.getUserById(userId)
                .orElseThrow(() -> new JynxProException(ErrorCode.USER_NOT_FOUND)));
    }

    @GetMapping("/{publicKey}")
    public ResponseEntity<User> getByPublicKey(
            @PathVariable("publicKey") String publicKey
    ) {
        return ResponseEntity.ok(readOnlyRepository.getUserByPublicKey(publicKey)
                .orElseThrow(() -> new JynxProException(ErrorCode.USER_NOT_FOUND)));
    }

    @GetMapping("/{id}/accounts")
    public ResponseEntity<List<Account>> getAccounts(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getAccountsByUserId(id));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<Order>> getOrders(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getOrdersByUserId(id));
    }

    @GetMapping("/{id}/trades")
    public ResponseEntity<List<Trade>> getTrades(
            @PathVariable("id") UUID id,
            @RequestParam("marketId") UUID marketId
    ) {
        return ResponseEntity.ok(readOnlyRepository.getTradesByUserIdAndMarketId(id, marketId));
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<List<Position>> getPositions(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getPositionsByUserId(id));
    }
}