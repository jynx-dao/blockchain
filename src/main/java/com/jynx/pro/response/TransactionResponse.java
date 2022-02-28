package com.jynx.pro.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class TransactionResponse<T> extends TendermintResponse {
    public String hash;
    public T item;
}