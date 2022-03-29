package com.jynx.pro.repository;

import com.jynx.pro.entity.BlockValidator;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

@Repository
public class BlockValidatorRepository extends EntityRepository<BlockValidator> {

    /**
     * Get {@link BlockValidator}s by height
     *
     * @param blockHeight the block height
     *
     * @return {@link List <BlockValidator>}
     */
    public List<BlockValidator> getByBlockHeight(
            final Long blockHeight
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<BlockValidator> query = cb.createQuery(BlockValidator.class);
        Root<BlockValidator> rootType = query.from(BlockValidator.class);
        query = query.select(rootType).where(cb.equal(rootType.get("blockHeight"), blockHeight));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<BlockValidator> getType() {
        return BlockValidator.class;
    }
}