package com.jynx.pro.service;

import com.jynx.pro.constant.Blockchain;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.Event;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.EventRepository;
import com.jynx.pro.repository.StakeRepository;
import com.jynx.pro.repository.UserRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@Slf4j
@Service
public class StakeService {

    @Autowired
    private StakeRepository stakeRepository;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    /**
     * Gets the total amount of {@link Stake} for a {@link User}
     *
     * @param user the {@link User}
     *
     * @return the amount of stake
     */
    public BigDecimal getStakeForUser(
            final User user
    ) {
        return stakeRepository.findByUser(user).orElse(new Stake().setAmount(BigDecimal.ZERO)).getAmount();
    }

    /**
     * Throw error if {@link User} does not have enough {@link Stake} to create a proposal
     *
     * @param user the {@link User}
     */
    public void checkProposerStake(
            final User user
    ) {
        if(getStakeForUser(user).doubleValue() < configService.get().getMinProposerStake()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_PROPOSER_STAKE);
        }
    }

    private void saveEvent(
            final User user,
            final Long blockNumber,
            final String txHash,
            final String address,
            final BigInteger amount,
            final EventType type
    ) {
        double modifier = Math.pow(10, 18);
        Event event = new Event()
                .setId(uuidUtils.next())
                .setUser(user)
                .setBlockchain(Blockchain.ETHEREUM)
                .setConfirmed(false)
                .setBlockNumber(blockNumber)
                .setHash(txHash)
                .setType(type)
                .setAddress(address)
                .setAmount(BigDecimal.valueOf(amount.doubleValue())
                        .divide(BigDecimal.valueOf(modifier), 4, RoundingMode.HALF_DOWN));
        eventRepository.save(event);
    }

    private Stake getOrCreateStake(
            final User user
    ) {
        return stakeRepository.findByUser(user)
                .orElse(new Stake()
                        .setUser(user)
                        .setId(uuidUtils.next())
                        .setAmount(BigDecimal.ZERO));
    }

    public void confirmEvent(
            final Event event
    ) {
        if(event.getType().equals(EventType.ADD_STAKE)) {
            Stake stake = getOrCreateStake(event.getUser());
            stake.setAmount(stake.getAmount().add(event.getAmount()));
            stakeRepository.save(stake);
            event.setConfirmed(true);
            eventRepository.save(event);
        } else if(event.getType().equals(EventType.REMOVE_STAKE)) {
            Stake stake = getOrCreateStake(event.getUser());
            stake.setAmount(stake.getAmount().subtract(event.getAmount()));
            stakeRepository.save(stake);
            event.setConfirmed(true);
            eventRepository.save(event);
        } else if(event.getType().equals(EventType.DEPOSIT_ASSET)) {
            // TODO - credit deposit
        }
    }

    private User getAndCreateUser(
            final String publicKey
    ) {
        User user = userRepository.findByPublicKey(publicKey)
                .orElse(new User()
                        .setId(uuidUtils.next())
                        .setPublicKey(publicKey)
                        .setReputationScore(1L)
                        .setUsername(publicKey));
        return userRepository.save(user);
    }

    public void remove(
            final String address,
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        User user = getAndCreateUser(publicKey);
        saveEvent(user, blockNumber, txHash, address, amount, EventType.REMOVE_STAKE);
    }

    public void add(
            final String address,
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        User user = getAndCreateUser(publicKey);
        saveEvent(user, blockNumber, txHash, address, amount, EventType.ADD_STAKE);
    }
}