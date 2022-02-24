package com.jynx.pro.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SingleItemResponse<T> {
    private T item;
}