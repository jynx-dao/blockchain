package com.jynx.pro.service;

import com.jynx.pro.constant.Blockchain;
import com.jynx.pro.constant.DepositStatus;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.DepositRepository;
import com.jynx.pro.repository.EventRepository;
import com.jynx.pro.repository.StakeRepository;
import com.jynx.pro.utils.PriceUtils;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class EventService {

    @Autowired
    private StakeService stakeService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private StakeRepository stakeRepository;
    @Autowired
    private DepositRepository depositRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private PriceUtils priceUtils;

    /**
     * Lookup matching event
     *
     * @param amount the amount
     * @param publicKey the public key
     * @param blockNumber the block number
     * @param txHash the transaction hash
     * @param eventType the {@link EventType}
     *
     * @return {@link Optional<Event>}
     */
    private Optional<Event> findMatchingEvent(
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash,
            final EventType eventType
    ) {
        return eventRepository.findByHash(txHash).stream().filter(e ->
                e.getAmount().doubleValue() == amount.doubleValue() &&
                        e.getUser().getPublicKey().equals(publicKey) &&
                        e.getBlockNumber().equals(blockNumber) &&
                        e.getType().equals(eventType)).findFirst();
    }

    /**
     * Save a new {@link Event}
     *
     * @param user the {@link User}
     * @param blockNumber the block number
     * @param txHash the transaction hash
     * @param amount the amount
     * @param type the {@link EventType}
     * @param assetAddress the asset address [optional]
     *
     * @return {@link Event}
     */
    public Event save(
            final User user,
            final Long blockNumber,
            final String txHash,
            final BigInteger amount,
            final EventType type,
            final String assetAddress
    ) {
        Optional<Event> existingEvent = findMatchingEvent(amount, user.getPublicKey(), blockNumber, txHash, type);
        if(existingEvent.isPresent()) {
            return existingEvent.get();
        }
        double modifier = Math.pow(10, 18);
        Event event = new Event()
                .setId(uuidUtils.next())
                .setUser(user)
                .setBlockchain(Blockchain.ETHEREUM)
                .setConfirmed(false)
                .setBlockNumber(blockNumber)
                .setHash(txHash)
                .setType(type)
                .setAmount(new BigDecimal(amount)
                        .divide(BigDecimal.valueOf(modifier), 8, RoundingMode.HALF_DOWN));
        if(assetAddress != null) {
            event.setAsset(assetAddress);
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
            event = eventRepository.save(event);
            depositRepository.save(deposit);
        } else {
            event = eventRepository.save(event);
        }
        return event;
    }

    /**
     * Save a new {@link Event}
     *
     * @param user the {@link User}
     * @param blockNumber the block number
     * @param txHash the transaction hash
     * @param amount the amount
     * @param type the {@link EventType}
     *
     * @return {@link Event}
     */
    public Event save(
            final User user,
            final Long blockNumber,
            final String txHash,
            final BigInteger amount,
            final EventType type
    ) {
        return save(user, blockNumber, txHash, amount, type, null);
    }

    /**
     * Confirm an event after sufficient Ethereum blocks have been mined
     *
     * @param event {@link Event}
     *
     * @return {@link Event}
     */
    public Event confirm(
            final Event event
    ) {
        List<EventType> stakeEvents = List.of(EventType.ADD_STAKE, EventType.REMOVE_STAKE);
        if (stakeEvents.contains(event.getType())) {
            Stake stake = stakeService.getAndCreate(event.getUser());
            String tendermintKey = "";
            try {
                Base64.encodeBase64String(Hex.decodeHex(event.getUser().getPublicKey()));
            } catch (DecoderException e) {
                log.error(e.getMessage(), e);
            }
            boolean isValidator = validatorService.isValidator(tendermintKey);
            if (event.getType().equals(EventType.ADD_STAKE)) {
                stake.setAmount(stake.getAmount().add(event.getAmount()));
            } else {
                stake.setAmount(stake.getAmount().subtract(event.getAmount()));
            }
            if(isValidator && stake.getAmount().doubleValue() < configService.get()
                    .getValidatorBond().doubleValue()) {
                validatorService.disable(tendermintKey);
            } else if(isValidator && stake.getAmount().doubleValue() >= configService.get()
                    .getValidatorBond().doubleValue()) {
                validatorService.enable(tendermintKey);
            }
            stakeRepository.save(stake);
        } else {
            Deposit deposit = depositRepository.findByEventId(event.getId())
                    .orElseThrow(() -> new JynxProException(ErrorCode.DEPOSIT_NOT_FOUND));
            accountService.credit(deposit);
        }
        event.setConfirmed(true);
        return eventRepository.save(event);
    }

    /**
     * Get unconfirmed {@link Event}s
     *
     * @return {@link List<Event>}
     */
    public List<Event> getUnconfirmed() {
        return eventRepository.findByConfirmed(false);
    }
}