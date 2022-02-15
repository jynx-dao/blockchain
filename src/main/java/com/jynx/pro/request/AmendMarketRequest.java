package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class AmendMarketRequest extends ProposalRequest {
    private UUID id;
    private BigDecimal initialMargin;
    private BigDecimal maintenanceMargin;
    private Integer tickSize;
    private Integer stepSize;
    private Integer settlementFrequency;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
}