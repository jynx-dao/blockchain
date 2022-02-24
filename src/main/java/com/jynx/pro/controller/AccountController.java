package com.jynx.pro.controller;

import com.jynx.pro.entity.Account;
import com.jynx.pro.response.SingleItemResponse;
import com.jynx.pro.service.AccountService;
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
    private AccountService accountService;

    @GetMapping("/{id}")
    public ResponseEntity<SingleItemResponse<Account>> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new SingleItemResponse<Account>().setItem(accountService.getById(id)));
    }
}