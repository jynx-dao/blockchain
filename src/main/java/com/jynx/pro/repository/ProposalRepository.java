package com.jynx.pro.repository;

import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.entity.Proposal;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;

@Repository
public class ProposalRepository extends EntityRepository<Proposal> {

    /**
     * Get {@link Proposal}s by status
     *
     * @param status {@link ProposalStatus}
     *
     * @return {@link List<Proposal>}
     */
    public List<Proposal> findByStatus(
            final ProposalStatus status
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Proposal> query = cb.createQuery(getType());
        Root<Proposal> rootType = query.from(getType());
        Path<ProposalStatus> proposalStatus = rootType.get("status");
        query = query.select(rootType).where(cb.equal(proposalStatus, status));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Proposal> getType() {
        return Proposal.class;
    }
}