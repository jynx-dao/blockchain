package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CheckTxResult {
    private int code;
    private String error;
}