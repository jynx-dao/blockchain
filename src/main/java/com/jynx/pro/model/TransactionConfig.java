package com.jynx.pro.model;

import com.jynx.pro.request.SignedRequest;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.function.Function;

@Data
@Accessors(chain = true)
public class TransactionConfig<T extends SignedRequest> {
    private Class<T> requestType;
    private Function<T, Object> deliverFn;
    private boolean protectedFn;
}