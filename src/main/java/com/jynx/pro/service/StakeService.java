package com.jynx.pro.service;

import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.StakeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StakeService {

    @Autowired
    private StakeRepository stakeRepository;
    @Autowired
    private ConfigService configService;

    /**
     * Gets the total amount of {@link Stake} for a {@link User}
     *
     * @param user the {@link User}
     *
     * @return the amount of stake
     */
    public Long getStakeForUser(
            final User user
    ) {
        return stakeRepository.findByUser(user).orElse(new Stake().setAmount(0L)).getAmount();
    }

    /**
     * Throw error if {@link User} does not have enough {@link Stake} to create a proposal
     *
     * @param user the {@link User}
     */
    public void checkProposerStake(
            final User user
    ) {
        if(getStakeForUser(user) < configService.get().getMinProposerStake()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_PROPOSER_STAKE);
        }
    }
}