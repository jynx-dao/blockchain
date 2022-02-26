package com.jynx.pro.controller;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Deposit;
import com.jynx.pro.entity.Transaction;
import com.jynx.pro.entity.Withdrawal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.request.CreateWithdrawalRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountService accountService;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;

    @GetMapping("/{id}")
    public ResponseEntity<Account> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getAccountById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.ACCOUNT_NOT_FOUND)));
    }

    @GetMapping("/{id}/deposits")
    public ResponseEntity<List<Deposit>> getDeposits(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        return ResponseEntity.ok(readOnlyRepository.getDepositsByAccountIdAndUserId(id, userId));
    }

    @GetMapping("/{id}/withdrawals")
    public ResponseEntity<List<Withdrawal>> getWithdrawals(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        return ResponseEntity.ok(readOnlyRepository.getWithdrawalsByAccountIdAndUserId(id, userId));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        return ResponseEntity.ok(readOnlyRepository.getTransactionsByAccountIdAndUserId(id, userId));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Withdrawal> createWithdrawal(
            @RequestBody CreateWithdrawalRequest request
    ) {
        return ResponseEntity.ok(accountService.createWithdrawal(request));
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<Withdrawal> cancelWithdrawal(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(accountService.cancelWithdrawal(request));
    }
}