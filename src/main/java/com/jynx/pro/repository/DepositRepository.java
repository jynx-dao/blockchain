package com.jynx.pro.repository;

import com.jynx.pro.entity.Deposit;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DepositRepository extends EntityRepository<Deposit> {

    /**
     * Get {@link Deposit} by event ID
     *
     * @param eventId the event ID
     *
     * @return {@link Optional<Deposit>}
     */
    public Optional<Deposit> findByEventId(
            final UUID eventId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Deposit> query = cb.createQuery(getType());
        Root<Deposit> rootType = query.from(getType());
        Path<UUID> event_id = rootType.join("event").get("id");
        query = query.select(rootType).where(cb.equal(event_id, eventId));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Class<Deposit> getType() {
        return Deposit.class;
    }
}