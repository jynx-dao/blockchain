package com.jynx.pro.repository;

import com.jynx.pro.entity.Trade;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class TradeRepository extends EntityRepository<Trade> {
    @Override
    public Class<Trade> getType() {
        return Trade.class;
    }
}