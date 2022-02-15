package com.jynx.pro.service;

import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Oracle;
import com.jynx.pro.repository.OracleRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class OracleService {

    @Autowired
    private OracleRepository oracleRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    public void save(
            final List<Oracle> oracles,
            final Market market
    ) {
        oracles.forEach(o -> o.setMarket(market).setId(uuidUtils.next()));
        oracleRepository.saveAll(oracles);
    }
}