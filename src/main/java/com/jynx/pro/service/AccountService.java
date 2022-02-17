package com.jynx.pro.service;

import com.jynx.pro.constant.DepositStatus;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.repository.DepositRepository;
import com.jynx.pro.utils.PriceUtils;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

@Slf4j
@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private DepositRepository depositRepository;
    @Autowired
    private EventService eventService;
    @Autowired
    private UserService userService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private PriceUtils priceUtils;

    public Optional<Account> get(
            final User user,
            final Asset asset
    ) {
        return accountRepository.findByUserAndAsset(user, asset);
    }

    private Account getAndCreate(
            final User user,
            final Asset asset
    ) {
        Account account = get(user, asset)
                .orElse(new Account()
                    .setUser(user)
                    .setAsset(asset)
                    .setId(uuidUtils.next())
                    .setAvailableBalance(BigDecimal.ZERO)
                    .setMarginBalance(BigDecimal.ZERO)
                    .setBalance(BigDecimal.ZERO));
        account = accountRepository.save(account);
        return account;
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

    public void deposit(
            final String assetAddress,
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        User user = userService.getAndCreateUser(publicKey);
        Event event = eventService.save(user, blockNumber,
                txHash, amount, EventType.DEPOSIT_ASSET, assetAddress);
        Asset asset = assetService.getByAddress(assetAddress);
        Deposit deposit = new Deposit()
                .setAmount(priceUtils.fromBigInteger(amount, asset.getDecimalPlaces()))
                .setId(uuidUtils.next())
                .setAsset(asset)
                .setStatus(DepositStatus.PENDING)
                .setEvent(event)
                .setUser(user)
                .setCreated(configService.getTimestamp());
        depositRepository.save(deposit);
    }

    public void credit(
            final Deposit deposit
    ) {
        Account account = getAndCreate(deposit.getUser(), deposit.getAsset());
        account.setBalance(account.getBalance().add(deposit.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(deposit.getAmount()));
        accountRepository.save(account);
        deposit.setStatus(DepositStatus.CREDITED);
        depositRepository.save(deposit);
    }
}