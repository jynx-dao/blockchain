package com.jynx.pro.controller;

import com.jynx.pro.response.MultipleItemResponse;
import com.jynx.pro.service.cache.AccountCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountCacheService accountCacheService;

    @GetMapping("/{userId}")
    public ResponseEntity<MultipleItemResponse> getMany(
            @PathVariable("userId") UUID userId
    ) {
        return ResponseEntity.ok(new MultipleItemResponse().setItems(accountCacheService.getByUserId(userId)));
    }
}