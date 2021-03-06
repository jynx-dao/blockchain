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
import com.jynx.pro.request.DepositAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
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
    private WithdrawalRepository withdrawalRepository;
    @Autowired
    private OrderService orderService;

    /**
     * Get an account by user and asset
     *
     * @param user {@link User}
     * @param asset {@link Asset}
     *
     * @return {@link Optional<Account>}
     */
    public Optional<Account> get(
            final User user,
            final Asset asset
    ) {
        return accountRepository.findByUserAndAsset(user, asset);
    }

    /**
     * Get an account by user and asset, and create if doesn't exist
     *
     * @param user the {@link User}
     * @param asset the {@link Asset}
     *
     * @return {@link Account}
     */
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

    /**
     * Allocate margin to a user's {@link Account}
     *
     * @param user the {@link User}
     * @param market the relevant {@link Market}
     * @param margin the margin to allocate
     */
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

    /**
     * Allocate margin to a user's {@link Account}
     *
     * @param user the {@link User}
     * @param market the relevant {@link Market}
     */
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
     * Create a new deposit {@link Event}
     *
     * @param request {@link DepositAssetRequest}
     *
     * @return {@link Event}
     */
    public Event deposit(
            final DepositAssetRequest request
    ) {
        return eventService.save(userService.getAndCreate(request.getTargetKey()), request.getBlockNumber(),
                request.getTxHash(), request.getAmount(), EventType.DEPOSIT_ASSET, request.getAssetAddress());
    }

    /**
     * Credit a {@link Deposit} to a user's account
     *
     * @param deposit {@link Deposit}
     */
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

    /**
     * Credit / debit maker and taker fees when a trade happens
     *
     * @param quantity the trade size
     * @param price the price of the rade
     * @param taker the taker {@link User}
     * @param market the relevant {@link Market}
     */
    public void processFees(
            final BigDecimal quantity,
            final BigDecimal price,
            final User taker,
            final Market market
    ) {
        processFees(quantity, price, null, taker, market);
    }

    /**
     * Credit / debit maker and taker fees when a trade happens
     *
     * @param quantity the trade size
     * @param price the price of the rade
     * @param maker the maker {@link User}
     * @param taker the taker {@link User}
     * @param market the relevant {@link Market}
     */
    public void processFees(
            final BigDecimal quantity,
            final BigDecimal price,
            final User maker,
            final User taker,
            final Market market
    ) {
        BigDecimal makerAmount = BigDecimal.ZERO;
        BigDecimal takerAmount = quantity.multiply(price).multiply(market.getTakerFee());
        Account takerAccount = getAndCreate(taker, market.getSettlementAsset());
        takerAccount.setBalance(takerAccount.getBalance().subtract(takerAmount));
        takerAccount.setAvailableBalance(takerAccount.getAvailableBalance().subtract(takerAmount));
        accountRepository.save(takerAccount);
        Transaction takerTx = new Transaction()
                .setType(TransactionType.FEE)
                .setAmount(takerAmount.multiply(BigDecimal.valueOf(-1)))
                .setUser(taker)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        Position takerPosition = positionService.getAndCreate(taker, market);
        takerPosition.setRealisedPnl(takerPosition.getRealisedPnl().subtract(takerAmount));
        positionService.save(takerPosition);
        transactionRepository.save(takerTx);
        if(maker != null) {
            makerAmount = quantity.multiply(price).multiply(market.getMakerFee());
            Account makerAccount = getAndCreate(maker, market.getSettlementAsset());
            makerAccount.setBalance(makerAccount.getBalance().add(makerAmount));
            makerAccount.setAvailableBalance(makerAccount.getAvailableBalance().add(makerAmount));
            accountRepository.save(makerAccount);
            Transaction makerTx = new Transaction()
                    .setType(TransactionType.FEE)
                    .setAmount(makerAmount)
                    .setUser(maker)
                    .setAsset(market.getSettlementAsset())
                    .setTimestamp(configService.getTimestamp());
            Position makerPosition = positionService.getAndCreate(maker, market);
            makerPosition.setRealisedPnl(makerPosition.getRealisedPnl().add(makerAmount));
            positionService.save(makerPosition);
            transactionRepository.save(makerTx);
        }
        BigDecimal treasuryAmount = takerAmount.subtract(makerAmount);
        market.getSettlementAsset().setTreasuryBalance(
                market.getSettlementAsset().getTreasuryBalance().add(treasuryAmount));
        assetRepository.save(market.getSettlementAsset());
    }

    /**
     * Book realised profit or loss against a user's account
     *
     * @param user the {@link User}
     * @param market the relevant {@link Market}
     * @param realisedProfit the user's PNL
     */
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
