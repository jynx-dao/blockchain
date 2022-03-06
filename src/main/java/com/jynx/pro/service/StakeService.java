package com.jynx.pro.service;

import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.Event;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.StakeRepository;
import com.jynx.pro.request.UpdateStakeRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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

    /**
     * Get {@link Stake} for {@link User} and create if it doesn't exist
     *
     * @param user {@link User}
     *
     * @return {@link Stake}
     */
    public Stake getAndCreate(
            final User user
    ) {
        return stakeRepository.findByUser(user)
                .orElse(new Stake()
                        .setUser(user)
                        .setId(uuidUtils.next())
                        .setAmount(BigDecimal.ZERO));
    }

    /**
     * Add an {@link Event} to remove stake from public key
     *
     * @param request {@link UpdateStakeRequest}
     *
     * @return {@link Event}
     */
    public Event remove(
            final UpdateStakeRequest request
    ) {
        return eventService.save(userService.getAndCreate(request.getPublicKey()), request.getBlockNumber(),
                request.getTxHash(), request.getAmount(), EventType.REMOVE_STAKE);
    }

    /**
     * Add an {@link Event} to add stake to public key
     *
     * @param request {@link UpdateStakeRequest}
     *
     * @return {@link Event}
     */
    public Event add(
            final UpdateStakeRequest request
    ) {
        return eventService.save(userService.getAndCreate(request.getPublicKey()), request.getBlockNumber(),
                request.getTxHash(), request.getAmount(), EventType.ADD_STAKE);
    }
}