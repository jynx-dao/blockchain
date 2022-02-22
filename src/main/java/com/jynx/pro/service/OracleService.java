package com.jynx.pro.service;

import com.jynx.pro.constant.OracleStatus;
import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Oracle;
import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.repository.OracleRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class OracleService {

    @Autowired
    private OracleRepository oracleRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    public void slash(
            final List<Oracle> oracles
    ) {
        for(Oracle oracle : oracles) {
            BigDecimal ratio = oracle.getMarket().getOracleSlashingRatio();
            BigDecimal amountToSlash = oracle.getMarket().getOracleBond().multiply(ratio);
            Account account = accountService.getAndCreate(oracle.getUser(), oracle.getMarket().getSettlementAsset());
            // TODO - do we need a Transaction for this because we're debiting funds?
            account.setOracleBond(account.getOracleBond().subtract(amountToSlash));
            if(account.getOracleBond().doubleValue() == 0d) {
                oracle.setStatus(OracleStatus.SUSPENDED);
                oracleRepository.save(oracle);
            }
            accountRepository.save(account);
        }
    }

    public void joinMarket() {
        // TODO - allow user to become an oracle provider
    }

    public void leaveMarket() {
        // TODO - allow user to quit as oracle provider
    }

    public void topUpBond() {
        // TODO - allow an old oracle provider to top-up their bond
    }
}