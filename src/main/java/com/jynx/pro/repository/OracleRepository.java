package com.jynx.pro.repository;

import com.jynx.pro.entity.Oracle;
import org.springframework.stereotype.Repository;

@Repository
public class OracleRepository extends EntityRepository<Oracle> {
    @Override
    public Class<Oracle> getType() {
        return Oracle.class;
    }
}