package com.jynx.pro.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class MultipleItemResponse<T> {
    private List<T> items = new ArrayList<>();
}