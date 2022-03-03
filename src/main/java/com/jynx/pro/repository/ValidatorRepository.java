package com.jynx.pro.repository;

import com.jynx.pro.entity.Validator;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.Optional;

@Repository
public class ValidatorRepository extends EntityRepository<Validator> {
    @Override
    public Class<Validator> getType() {
        return Validator.class;
    }

    public Optional<Validator> findByPublicKey(
            final String publicKey
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Validator> query = cb.createQuery(getType());
        Root<Validator> rootType = query.from(getType());
        Path<String> userPublicKey = rootType.get("publicKey");
        query = query.select(rootType).where(cb.equal(userPublicKey, publicKey));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }
}