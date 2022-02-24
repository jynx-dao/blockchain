package com.jynx.pro.service;

import com.jynx.pro.entity.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuctionService {

    /**
     * Check auction triggers
     *
     * @param market {@link Market}
     */
    public void checkTriggers(
            final Market market
    ) {
        // TODO - check if the market should be moved into auction
    }
}