package com.jynx.pro.repository;

import com.jynx.pro.entity.Event;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;

@Repository
public class EventRepository extends EntityRepository<Event> {

    /**
     * Get {@link Event}s by confirmed = true or false
     *
     * @param confirmed true / false
     *
     * @return {@link List<Event>}
     */
    public List<Event> findByConfirmed(
            final Boolean confirmed
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(getType());
        Root<Event> rootType = query.from(getType());
        Path<Boolean> confirmed_attr = rootType.get("confirmed");
        query = query.select(rootType).where(cb.equal(confirmed_attr, confirmed));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Event> getType() {
        return Event.class;
    }
}