package com.jynx.pro.repository;

import com.jynx.pro.manager.DatabaseTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class EntityRepository<T> {

    @Autowired
    protected DatabaseTransactionManager databaseTransactionManager;

    public abstract Class<T> getType();

    protected EntityManager getEntityManager() {
        return databaseTransactionManager.getWriteEntityManager();
    }

    public Optional<T> findById(
            final UUID id
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getType());
        Root<T> rootType = query.from(getType());
        query = query.select(rootType).where(new Predicate[]{ cb.equal(rootType.get("id"), id) });
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    public T save(
            final T item
    ) {
        getEntityManager().persist(item);
        return item;
    }

    public List<T> saveAll(
            final List<T> items
    ) {
        items.forEach(getEntityManager()::persist);
        return items;
    }

    public List<T> findAll() {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(getType());
        Root<T> rootEntry = cq.from(getType());
        CriteriaQuery<T> all = cq.select(rootEntry);
        TypedQuery<T> allQuery = getEntityManager().createQuery(all);
        return allQuery.getResultList();
    }

    public void deleteAll() {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaDelete<T> query = cb.createCriteriaDelete(getType());
        query.from(getType());
        getEntityManager().createQuery(query).executeUpdate();
    }
}
