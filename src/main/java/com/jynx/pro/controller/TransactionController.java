package com.jynx.pro.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/transaction")
public class TransactionController extends AbstractController {

    @GetMapping("/{hash}")
    public ResponseEntity<Object> get(
            @PathVariable("hash") String hash
    ) {
        return ResponseEntity.ok(tendermintClient.getTransaction(hash, Object.class));
    }
}