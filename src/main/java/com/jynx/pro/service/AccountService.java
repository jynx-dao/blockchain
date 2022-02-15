package com.jynx.pro.service;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    public Optional<Account> get(
            final User user,
            final Asset asset
    ) {
        return accountRepository.findByUserAndAsset(user, asset);
    }

    public void allocateMargin(
            final BigDecimal margin,
            final User user,
            final Asset asset
    ) {
        Account account = get(user, asset).orElseThrow(() -> new JynxProException(ErrorCode.INSUFFICIENT_MARGIN));
        account.setMarginBalance(account.getMarginBalance().add(margin));
        account.setAvailableBalance(account.getAvailableBalance().subtract(margin));
        accountRepository.save(account);
    }

    public void releaseMargin(
            final BigDecimal margin,
            final User user,
            final Asset asset
    ) {
        Account account = get(user, asset).orElseThrow(() -> new JynxProException(ErrorCode.MARGIN_NOT_ALLOCATED));
        account.setMarginBalance(account.getMarginBalance().subtract(margin));
        account.setAvailableBalance(account.getAvailableBalance().add(margin));
        accountRepository.save(account);
    }
}