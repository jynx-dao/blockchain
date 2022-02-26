package com.jynx.pro.repository;

import com.jynx.pro.constant.AssetType;
import com.jynx.pro.entity.Asset;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;

@Repository
public class AssetRepository extends EntityRepository<Asset> {

    /**
     * Get {@link Asset}s by address and type
     *
     * @param address the smart contract address
     * @param type the {@link AssetType}
     *
     * @return {@link List} of {@link Asset}s
     */
    public List<Asset> findByAddressAndType(
            final String address,
            final AssetType type
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Asset> query = cb.createQuery(getType());
        Root<Asset> rootType = query.from(getType());
        Path<AssetType> assetType = rootType.get("type");
        Path<String> assetAddress = rootType.get("address");
        query = query.select(rootType).where(cb.equal(assetType, type), cb.equal(assetAddress, address));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Asset> getType() {
        return Asset.class;
    }
}