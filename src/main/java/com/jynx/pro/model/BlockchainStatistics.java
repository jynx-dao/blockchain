package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BlockchainStatistics {
    private Long timestamp;
}