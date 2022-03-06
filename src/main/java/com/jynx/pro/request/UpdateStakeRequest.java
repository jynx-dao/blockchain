package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigInteger;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class UpdateStakeRequest extends SignedRequest {
    private BigInteger amount;
    private String targetKey;
    private Long blockNumber;
    private String txHash;
}