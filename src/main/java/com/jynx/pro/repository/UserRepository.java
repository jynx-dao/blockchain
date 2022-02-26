package com.jynx.pro.repository;

import com.jynx.pro.entity.User;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.Optional;

@Repository
public class UserRepository extends EntityRepository<User> {

    /**
     * Get {@link User} by public key
     *
     * @param publicKey the public key
     *
     * @return {@link Optional<User>}
     */
    public Optional<User> findByPublicKey(
            final String publicKey
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(getType());
        Root<User> rootType = query.from(getType());
        Path<String> userPublicKey = rootType.get("publicKey");
        query = query.select(rootType).where(cb.equal(userPublicKey, publicKey));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Class<User> getType() {
        return User.class;
    }
}