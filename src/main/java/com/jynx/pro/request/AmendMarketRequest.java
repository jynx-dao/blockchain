package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class AmendMarketRequest extends ProposalRequest {
    private UUID id;
    private BigDecimal marginRequirement;
    private Integer tickSize;
    private Integer stepSize;
    private Integer settlementFrequency;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
    private BigDecimal liquidationFee;
    private List<AddMarketRequest.AuctionTrigger> auctionTriggers = new ArrayList<>();
}