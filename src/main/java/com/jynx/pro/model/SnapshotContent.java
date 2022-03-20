package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class SnapshotContent<T> {
    private List<T> data;
    private String entityName;
}