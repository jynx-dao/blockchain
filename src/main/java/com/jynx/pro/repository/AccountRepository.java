package com.jynx.pro.repository;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.User;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountRepository extends EntityRepository<Account> {

    /**
     * Find {@link Account} by {@link User} and {@link Asset}
     *
     * @param user {@link User}
     * @param asset {@link Asset}
     *
     * @return {@link Optional} {@link Account}
     */
    public Optional<Account> findByUserAndAsset(
            final User user,
            final Asset asset
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Account> query = cb.createQuery(getType());
        Root<Account> rootType = query.from(getType());
        Path<UUID> userId = rootType.join("user").get("id");
        Path<UUID> assetId = rootType.join("asset").get("id");
        query = query.select(rootType).where(cb.equal(userId, user.getId()), cb.equal(assetId, asset.getId()));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Find {@link Account}s by {@link Asset} and balance greater than specified amount
     *
     * @param asset {@link Asset}
     * @param availableBalance specified balance
     *
     * @return {@link List} of {@link Account}s
     */
    public List<Account> findByAssetAndAvailableBalanceGreaterThan(
            final Asset asset,
            final BigDecimal availableBalance
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Account> query = cb.createQuery(getType());
        Root<Account> rootType = query.from(getType());
        Path<UUID> assetId = rootType.join("asset").get("id");
        Path<BigDecimal> accountAvailableBalance = rootType.get("availableBalance");
        query = query.select(rootType).where(cb.equal(assetId, asset.getId()),
                cb.greaterThan(accountAvailableBalance, availableBalance));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Find {@link Account}s by {@link Asset}
     *
     * @param asset {@link Asset}
     *
     * @return {@link List<Account>}
     */
    public List<Account> findByAsset(
            final Asset asset
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Account> query = cb.createQuery(getType());
        Root<Account> rootType = query.from(getType());
        Path<UUID> assetId = rootType.join("asset").get("id");
        query = query.select(rootType).where(cb.equal(assetId, asset.getId()));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Returns the entity type
     *
     * @return {@link Class<Account>}
     */
    @Override
    public Class<Account> getType() {
        return Account.class;
    }
}