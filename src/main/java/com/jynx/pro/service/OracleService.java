package com.jynx.pro.service;

import com.jynx.pro.constant.OracleType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.repository.OracleRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
    @Autowired
    private BinanceService binanceService;
    @Autowired
    private PolygonService polygonService;
    @Autowired
    private CoinbaseService coinbaseService;

    /**
     * Get the settlement price for given market
     *
     * @param market {@link Market}
     *
     * @return the settlement price
     */
    public BigDecimal getSettlementValue(
            final Market market
    ) {
        OracleType oracleType = market.getOracle().getType();
        if(oracleType.equals(OracleType.POLYGON)) {
            return polygonService.getPriceAt(market.getOracle().getKey(), getSettlementTime(market));
        } else if(oracleType.equals(OracleType.COINBASE)) {
            return coinbaseService.getPriceAt(market.getOracle().getKey(), getSettlementTime(market));
        } else if(oracleType.equals(OracleType.BINANCE)) {
            return binanceService.getPriceAt(market.getOracle().getKey(), getSettlementTime(market));
        }
        return getSettlementValueFromSignedData(market);
    }

    private BigDecimal getSettlementValueFromSignedData(
            final Market market
    ) {
        // TODO - if the signed data has not been provided then the oracle provider must be slashed
        return BigDecimal.ZERO;
    }

    private long getSettlementTime(
            final Market market
    ) {
        return market.getLastSettlement() + (60 * 60 * 8);
    }
}