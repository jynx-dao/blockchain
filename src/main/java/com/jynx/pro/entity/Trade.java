package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_trade")
@Accessors(chain = true)
public class Trade {
    @Id
    private UUID id;
}
