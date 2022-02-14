package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_validator")
@Accessors(chain = true)
public class Validator {
    @Id
    private UUID id;
}