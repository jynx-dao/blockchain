package com.jynx.pro.service;

import com.jynx.pro.constant.Blockchain;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.Deposit;
import com.jynx.pro.entity.Event;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.DepositRepository;
import com.jynx.pro.repository.EventRepository;
import com.jynx.pro.repository.StakeRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

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
    private UUIDUtils uuidUtils;

    public Event save(
            final User user,
            final Long blockNumber,
            final String txHash,
            final BigInteger amount,
            final EventType type,
            final String asset
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
                .setAmount(BigDecimal.valueOf(amount.doubleValue())
                        .divide(BigDecimal.valueOf(modifier), 8, RoundingMode.HALF_DOWN));
        if(asset != null) {
            event.setAsset(asset);
        }
        return eventRepository.save(event);
    }

    public Event save(
            final User user,
            final Long blockNumber,
            final String txHash,
            final BigInteger amount,
            final EventType type
    ) {
        return save(user, blockNumber, txHash, amount, type, null);
    }

    public Event confirm(
            final Event event
    ) {
        List<EventType> stakeEvents = List.of(EventType.ADD_STAKE, EventType.REMOVE_STAKE);
        if(stakeEvents.contains(event.getType())) {
            Stake stake = stakeService.getAndCreate(event.getUser());
            if(event.getType().equals(EventType.ADD_STAKE)) {
                stake.setAmount(stake.getAmount().add(event.getAmount()));
            } else {
                stake.setAmount(stake.getAmount().subtract(event.getAmount()));
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

    public List<Event> getUnconfirmed() {
        return eventRepository.findByConfirmed(false);
    }
}