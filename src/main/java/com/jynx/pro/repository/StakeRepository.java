package com.jynx.pro.repository;

import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.*;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StakeRepository extends EntityRepository<Stake> {

    /**
     * Get {@link Stake} by user
     *
     * @param user {@link User}
     *
     * @return {@link Optional<Stake>}
     */
    public Optional<Stake> findByUser(
            final User user
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Stake> query = cb.createQuery(getType());
        Root<Stake> rootType = query.from(getType());
        Path<UUID> userId = rootType.join("user").get("id");
        query = query.select(rootType).where(new Predicate[]{ cb.equal(userId, user.getId()) });
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Class<Stake> getType() {
        return Stake.class;
    }
}