package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.handler.SocketHandler;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.BatchValidatorRequest;
import com.jynx.pro.request.CreateWithdrawalRequest;
import com.jynx.pro.request.DepositAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

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
    private DelegationRepository delegationRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private SocketHandler socketHandler;

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
        return save(account);
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
        save(account);
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
            save(account);
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
        save(account);
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
        save(account);
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
        save(account);
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
        save(takerAccount);
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
            save(makerAccount);
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
     * Distribute the treasury balance to users and validators
     *
     * @return {@link List<Asset>}
     */
    public List<Asset> distributeRewards(
            final BatchValidatorRequest request
    ) {
        log.debug(request.toString());
        List<Delegation> delegations = delegationRepository.findAll();
        BigDecimal totalDelegation = delegations.stream()
                .map(Delegation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<User> users = new HashSet<>();
        Set<Validator> validators = new HashSet<>();
        Map<UUID, BigDecimal> delegationByUser = new HashMap<>();
        Map<UUID, BigDecimal> delegationByValidator = new HashMap<>();
        for(Delegation delegation : delegations) {
            UUID userId = delegation.getStake().getUser().getId();
            UUID validatorId = delegation.getValidator().getId();
            delegationByUser.putIfAbsent(userId, BigDecimal.ZERO);
            delegationByValidator.putIfAbsent(validatorId, BigDecimal.ZERO);
            delegationByUser.put(userId, delegationByUser.get(userId).add(delegation.getAmount()));
            delegationByValidator.put(validatorId, delegationByValidator.get(validatorId).add(delegation.getAmount()));
            users.add(delegation.getStake().getUser());
            validators.add(delegation.getValidator());
        }
        List<Asset> assets = assetRepository.findAll();
        for(Asset asset : assets) {
            distributeUserRewards(users, totalDelegation, delegationByUser, asset);
            distributeValidatorRewards(validators, totalDelegation, delegationByValidator, asset);
            asset.setTreasuryBalance(BigDecimal.ZERO);
        }
        assetRepository.saveAll(assets);
        return assets;
    }

    /**
     * Distribute validator rewards
     *
     * @param validators {@link Set<Validator>}
     * @param totalDelegation total delegated stake
     * @param delegationByValidator delegation by validator ID
     * @param asset {@link Asset}
     */
    private void distributeValidatorRewards(
            final Set<Validator> validators,
            final BigDecimal totalDelegation,
            final Map<UUID, BigDecimal> delegationByValidator,
            final Asset asset
    ) {
        int dps = asset.getDecimalPlaces();
        for(Validator validator : validators) {
            BigDecimal validatorShare = delegationByValidator.get(validator.getId())
                    .divide(totalDelegation, dps, RoundingMode.HALF_UP);
            BigDecimal validatorReward = asset.getTreasuryBalance().multiply(validatorShare)
                    .multiply(configService.get().getNetworkFee()).multiply(BigDecimal.valueOf(validator.getScore()));
            Account account = getAndCreate(validator.getUser(), asset);
            Transaction tx = new Transaction()
                    .setType(TransactionType.REWARD_CREDIT)
                    .setAmount(validatorReward)
                    .setUser(validator.getUser())
                    .setAsset(asset)
                    .setTimestamp(configService.getTimestamp());
            account.setAvailableBalance(account.getAvailableBalance().add(validatorReward));
            account.setBalance(account.getBalance().add(validatorReward));
            transactionRepository.save(tx);
            save(account);
        }
    }

    /**
     * Distribute user rewards
     *
     * @param users {@link Set<User>}
     * @param totalDelegation total delegated stake
     * @param delegationByUser delegation by user ID
     * @param asset {@link Asset}
     */
    private void distributeUserRewards(
            final Set<User> users,
            final BigDecimal totalDelegation,
            final Map<UUID, BigDecimal> delegationByUser,
            final Asset asset
    ) {
        int dps = asset.getDecimalPlaces();
        for(User user : users) {
            BigDecimal userShare = delegationByUser.get(user.getId())
                    .divide(totalDelegation, dps, RoundingMode.HALF_UP);
            BigDecimal userReward = asset.getTreasuryBalance().multiply(userShare)
                    .multiply(BigDecimal.ONE.subtract(configService.get().getNetworkFee()));
            Account account = getAndCreate(user, asset);
            Transaction tx = new Transaction()
                    .setType(TransactionType.REWARD_CREDIT)
                    .setAmount(userReward)
                    .setUser(user)
                    .setAsset(asset)
                    .setTimestamp(configService.getTimestamp());
            account.setAvailableBalance(account.getAvailableBalance().add(userReward));
            account.setBalance(account.getBalance().add(userReward));
            transactionRepository.save(tx);
            save(account);
        }
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
        save(account);
        Transaction tx = new Transaction()
                .setType(TransactionType.SETTLEMENT)
                .setAmount(realisedProfit)
                .setUser(user)
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(tx);
    }

    /**
     * Save an account
     *
     * @param account {@link Account}
     *
     * @return {@link Account}
     */
    public Account save(
            final Account account
    ) {
        socketHandler.sendMessage(WebSocketChannelType.ACCOUNTS, account.getUser().getPublicKey(), account);
        return accountRepository.save(account);
    }
}
