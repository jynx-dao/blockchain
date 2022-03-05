package com.jynx.pro.service;

import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.StakeRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;

@Slf4j
@Service
public class StakeService {

    @Autowired
    private StakeRepository stakeRepository;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UserService userService;
    @Autowired
    private EventService eventService;
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
        double stake = getStakeForUser(user).doubleValue();
        if(stake < configService.get().getMinProposerStake()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_PROPOSER_STAKE);
        }
    }

    public Stake getAndCreate(
            final User user
    ) {
        return stakeRepository.findByUser(user)
                .orElse(new Stake()
                        .setUser(user)
                        .setId(uuidUtils.next())
                        .setAmount(BigDecimal.ZERO));
    }

    public void remove(
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        // TODO - don't duplicate events
        eventService.save(userService.getAndCreate(publicKey), blockNumber,
                txHash, amount, EventType.REMOVE_STAKE);
    }

    public void add(
            final BigInteger amount,
            final String publicKey,
            final Long blockNumber,
            final String txHash
    ) {
        // TODO - don't duplicate events
        eventService.save(userService.getAndCreate(publicKey), blockNumber,
                txHash, amount, EventType.ADD_STAKE);
    }
}