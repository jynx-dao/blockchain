package com.jynx.pro.service;

import com.jynx.pro.constant.DepositStatus;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.constant.TransactionType;
import com.jynx.pro.entity.*;
import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.repository.AssetRepository;
import com.jynx.pro.repository.DepositRepository;
import com.jynx.pro.repository.TransactionRepository;
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
    private TransactionRepository transactionRepository;
    @Autowired
    private AssetRepository assetRepository;
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
    @Autowired
    private EthereumService ethereumService;

    public Optional<Account> get(
            final User user,
            final Asset asset
    ) {
        return accountRepository.findByUserAndAsset(user, asset);
    }

    public Account getAndCreate(
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
        return accountRepository.save(account);
    }

    public void allocateMargin(
            final BigDecimal margin,
            final User user,
            final Asset asset
    ) {
        Account account = getAndCreate(user, asset);
        account.setAvailableBalance(account.getAvailableBalance().add(account.getMarginBalance()));
        account.setMarginBalance(margin);
        account.setAvailableBalance(account.getAvailableBalance().subtract(margin));
        accountRepository.save(account);
    }

    public void deposit(
            final String assetAddress,
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        User user = userService.getAndCreate(publicKey);
        Event event = eventService.save(user, blockNumber,
                txHash, amount, EventType.DEPOSIT_ASSET, assetAddress);
        Asset asset = assetService.getByAddress(assetAddress);
        int dps = ethereumService.decimalPlaces(asset.getAddress());
        Deposit deposit = new Deposit()
                .setAmount(priceUtils.fromBigInteger(amount, dps))
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
        Transaction transaction = new Transaction()
                .setId(uuidUtils.next())
                .setType(TransactionType.DEPOSIT)
                .setAmount(deposit.getAmount())
                .setUser(deposit.getUser())
                .setAsset(deposit.getAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(transaction);
        accountRepository.save(account);
        deposit.setStatus(DepositStatus.CREDITED);
        depositRepository.save(deposit);
    }

    public void processFees(
            final BigDecimal size,
            final BigDecimal price,
            final User maker,
            final User taker,
            final Market market
    ) {
        BigDecimal takerAmount = size.multiply(price).multiply(market.getTakerFee());
        BigDecimal makerAmount = size.multiply(price).multiply(market.getMakerFee());
        Account takerAccount = getAndCreate(taker, market.getSettlementAsset());
        Account makerAccount = getAndCreate(maker, market.getSettlementAsset());
        takerAccount.setBalance(takerAccount.getBalance().subtract(takerAmount));
        makerAccount.setBalance(makerAccount.getBalance().add(makerAmount));
        takerAccount.setAvailableBalance(takerAccount.getAvailableBalance().subtract(takerAmount));
        makerAccount.setAvailableBalance(makerAccount.getAvailableBalance().add(makerAmount));
        BigDecimal treasuryAmount = takerAmount.subtract(makerAmount);
        market.getSettlementAsset().setTreasuryBalance(
                market.getSettlementAsset().getTreasuryBalance().add(treasuryAmount));
        accountRepository.save(takerAccount);
        accountRepository.save(makerAccount);
        assetRepository.save(market.getSettlementAsset());
        Transaction takerTx = new Transaction()
                .setId(uuidUtils.next())
                .setType(TransactionType.FEE)
                .setAmount(takerAmount.multiply(BigDecimal.valueOf(-1)))
                .setUser(taker)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        Transaction makerTx = new Transaction()
                .setId(uuidUtils.next())
                .setType(TransactionType.FEE)
                .setAmount(makerAmount)
                .setUser(maker)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(takerTx);
        transactionRepository.save(makerTx);
    }

    public void bookProfit(
            final User user,
            final Market market,
            final BigDecimal realisedProfit
    ) {
        Account account = getAndCreate(user, market.getSettlementAsset());
        account.setBalance(account.getBalance().add(realisedProfit));
        account.setAvailableBalance(account.getAvailableBalance().add(realisedProfit));
        account.setMarginBalance(account.getMarginBalance().subtract(realisedProfit));
        accountRepository.save(account);
        Transaction tx = new Transaction()
                .setId(uuidUtils.next())
                .setType(TransactionType.SETTLEMENT)
                .setAmount(realisedProfit)
                .setUser(user)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(tx);
    }
}
