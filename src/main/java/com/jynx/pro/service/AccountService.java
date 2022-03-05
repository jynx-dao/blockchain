package com.jynx.pro.service;

import com.jynx.pro.constant.DepositStatus;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.constant.TransactionType;
import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.CreateWithdrawalRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.PriceUtils;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountService {

    private static final int WITHDRAWAL_BATCH_SIZE = 100;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private DepositRepository depositRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private PositionService positionService;
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
    @Autowired
    private WithdrawalRepository withdrawalRepository;
    @Autowired
    private OrderService orderService;

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
            final User user,
            final Market market,
            final BigDecimal margin
    ) {
        Account account = getAndCreate(user, market.getSettlementAsset());
        account.setAvailableBalance(account.getAvailableBalance().add(account.getMarginBalance()));
        account.setMarginBalance(margin);
        account.setAvailableBalance(account.getAvailableBalance().subtract(margin));
        accountRepository.save(account);
    }

    public void allocateMargin(
            final User user,
            final Market market
    ) {
        BigDecimal margin = orderService.getMarginRequirement(market, user);
        allocateMargin(user, market, margin);
    }

    /**
     * Reconcile the user balance if it's negative
     *
     * @param user {@link User}
     * @param market {@link Market}
     */
    public void reconcileNegativeBalance(
            final User user,
            final Market market
    ) {
        Account account = getAndCreate(user, market.getSettlementAsset());
        Position position = positionService.getAndCreate(user, market);
        if(account.getBalance().doubleValue() < 0) {
            if(market.getInsuranceFund().doubleValue() < account.getBalance().abs().doubleValue()) {
                positionService.claimLossBySocialization(market, account);
            } else {
                positionService.claimLossFromInsuranceFund(market, account);
            }
            account.setBalance(BigDecimal.ZERO);
            account.setAvailableBalance(BigDecimal.ZERO);
            account.setMarginBalance(BigDecimal.ZERO);
            accountRepository.save(account);
            positionService.reconcileLiquidatedPosition(position);
        }
    }

    /**
     * Request a new withdrawal
     *
     * @param request {@link CreateWithdrawalRequest}
     *
     * @return {@link Withdrawal}
     */
    public Withdrawal createWithdrawal(
            final CreateWithdrawalRequest request
    ) {
        validate(request);
        Asset asset = assetService.get(request.getAssetId());
        Account account = getAndCreate(request.getUser(), asset);
        if(account.getAvailableBalance().doubleValue() < request.getAmount().doubleValue()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);
        Transaction transaction = new Transaction()
                .setType(TransactionType.WITHDRAWAL)
                .setAmount(request.getAmount())
                .setUser(request.getUser())
                .setAsset(asset)
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(transaction);
        Withdrawal withdrawal = new Withdrawal()
                .setAmount(request.getAmount())
                .setCreated(configService.getTimestamp())
                .setId(uuidUtils.next())
                .setStatus(WithdrawalStatus.PENDING)
                .setDestination(request.getDestination())
                .setUser(request.getUser())
                .setAsset(asset);
        return withdrawalRepository.save(withdrawal);
    }

    /**
     * Validates {@link  SingleItemRequest}
     *
     * @param request {@link SingleItemRequest}
     */
    private void validate(
            final SingleItemRequest request
    ) {
        if(request.getId() == null) {
            throw new JynxProException(ErrorCode.ID_MANDATORY);
        }
    }

    /**
     * Validates {@link  CreateWithdrawalRequest}
     *
     * @param request {@link CreateWithdrawalRequest}
     */
    private void validate(
            final CreateWithdrawalRequest request
    ) {
        if(request.getAmount() == null) {
            throw new JynxProException(ErrorCode.AMOUNT_MANDATORY);
        }
        if(request.getAmount().doubleValue() < 0) {
            throw new JynxProException(ErrorCode.AMOUNT_NEGATIVE);
        }
        if(request.getDestination() == null) {
            throw new JynxProException(ErrorCode.DESTINATION_MANDATORY);
        }
        if(request.getAssetId() == null) {
            throw new JynxProException(ErrorCode.ASSET_ID_MANDATORY);
        }
    }

    /**
     * Cancel pending withdrawal
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link Withdrawal}
     */
    public Withdrawal cancelWithdrawal(
            final SingleItemRequest request
    ) {
        validate(request);
        Withdrawal withdrawal = withdrawalRepository.findById(request.getId())
                .orElseThrow(() -> new JynxProException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        if(!withdrawal.getStatus().equals(WithdrawalStatus.PENDING)) {
            throw new JynxProException(ErrorCode.WITHDRAWAL_NOT_PENDING);
        }
        Account account = getAndCreate(request.getUser(), withdrawal.getAsset());
        withdrawal.setStatus(WithdrawalStatus.CANCELED);
        account.setAvailableBalance(account.getAvailableBalance().add(withdrawal.getAmount()));
        account.setBalance(account.getBalance().add(withdrawal.getAmount()));
        accountRepository.save(account);
        Transaction transaction = new Transaction()
                .setType(TransactionType.DEPOSIT)
                .setAmount(withdrawal.getAmount())
                .setUser(request.getUser())
                .setAsset(withdrawal.getAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(transaction);
        return withdrawalRepository.save(withdrawal);
    }

    /**
     * Process all pending withdrawals
     */
    public void processWithdrawals() {
        List<Withdrawal> withdrawals = withdrawalRepository.findByStatus(WithdrawalStatus.PENDING);
        List<List<Withdrawal>> batches = ListUtils.partition(withdrawals, WITHDRAWAL_BATCH_SIZE);
        for(List<Withdrawal> batch : batches) {
            List<String> destinations = batch.stream().map(Withdrawal::getDestination)
                    .collect(Collectors.toList());
            List<BigInteger> amounts = batch.stream().map(w -> priceUtils.toBigInteger(w.getAmount()))
                    .collect(Collectors.toList());
            List<String> assets = batch.stream().map(w -> w.getAsset().getAddress())
                    .collect(Collectors.toList());
            TransactionReceipt transactionReceipt = ethereumService.withdrawAssets(destinations, amounts, assets);
            for(Withdrawal withdrawal : batch) {
                withdrawal.setStatus(WithdrawalStatus.DEBITED);
                withdrawal.setTxHash(transactionReceipt.getTransactionHash());
            }
            withdrawalRepository.saveAll(batch);
        }
    }

    public void deposit(
            final String assetAddress,
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        User user = userService.getAndCreate(publicKey);
        // TODO - don't duplicate events
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
            final BigDecimal quantity,
            final BigDecimal price,
            final User maker,
            final User taker,
            final Market market
    ) {
        BigDecimal takerAmount = quantity.multiply(price).multiply(market.getTakerFee());
        BigDecimal makerAmount = quantity.multiply(price).multiply(market.getMakerFee());
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
                .setType(TransactionType.FEE)
                .setAmount(takerAmount.multiply(BigDecimal.valueOf(-1)))
                .setUser(taker)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        Transaction makerTx = new Transaction()
                .setType(TransactionType.FEE)
                .setAmount(makerAmount)
                .setUser(maker)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        Position makerPosition = positionService.getAndCreate(maker, market);
        Position takerPosition = positionService.getAndCreate(taker, market);
        makerPosition.setRealisedPnl(makerPosition.getRealisedPnl().add(makerAmount));
        takerPosition.setRealisedPnl(takerPosition.getRealisedPnl().subtract(takerAmount));
        positionService.save(makerPosition);
        positionService.save(takerPosition);
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
        accountRepository.save(account);
        Transaction tx = new Transaction()
                .setType(TransactionType.SETTLEMENT)
                .setAmount(realisedProfit)
                .setUser(user)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(tx);
    }
}
