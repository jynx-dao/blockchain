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

    /**
     * Return the type of the entity
     *
     * @return {@link Class<T>}
     */
    public abstract Class<T> getType();

    /**
     * Get the appropriate {@link EntityManager}
     *
     * @return {@link EntityManager}
     */
    protected EntityManager getEntityManager() {
        return databaseTransactionManager.getWriteEntityManager();
    }

    /**
     * Generic method to retrieve entity by ID
     *
     * @param id the entity ID
     *
     * @return {@link Optional<T>}
     */
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

    /**
     * Generic method to save an entity
     *
     * @param item {@link T}
     *
     * @return {@link T}
     */
    public T save(
            final T item
    ) {
        getEntityManager().persist(item);
        return item;
    }

    /**
     * Generic method to save multiple entities
     *
     * @param items {@link List<T>}
     *
     * @return {@link List<T>}
     */
    public List<T> saveAll(
            final List<T> items
    ) {
        items.forEach(getEntityManager()::persist);
        return items;
    }

    /**
     * Generic method to return all entities
     *
     * @return {@link List<T>}
     */
    public List<T> findAll() {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(getType());
        Root<T> rootEntry = cq.from(getType());
        CriteriaQuery<T> all = cq.select(rootEntry);
        TypedQuery<T> allQuery = getEntityManager().createQuery(all);
        return allQuery.getResultList();
    }

    /**
     * Generic method to delete all entities
     */
    public void deleteAll() {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaDelete<T> query = cb.createCriteriaDelete(getType());
        query.from(getType());
        getEntityManager().createQuery(query).executeUpdate();
    }
}
