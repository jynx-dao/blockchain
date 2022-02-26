package com.jynx.pro.repository;

import com.jynx.pro.entity.Proposal;
import com.jynx.pro.entity.User;
import com.jynx.pro.entity.Vote;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class VoteRepository extends EntityRepository<Vote> {

    /**
     * Get {@link Vote}s by proposal and user
     *
     * @param proposal {@link Proposal}
     * @param user {@link User}
     *
     * @return {@link List<Vote>}
     */
    public List<Vote> findByProposalAndUser(
            final Proposal proposal,
            final User user
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Vote> query = cb.createQuery(getType());
        Root<Vote> rootType = query.from(getType());
        Path<UUID> userId = rootType.join("user").get("id");
        Path<UUID> proposalId = rootType.join("proposal").get("id");
        query = query.select(rootType).where(cb.equal(userId, user.getId()), cb.equal(proposalId, proposal.getId()));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Vote}s by proposal
     *
     * @param proposal {@link Proposal}
     *
     * @return {@link List<Vote>}
     */
    public List<Vote> findByProposal(
            final Proposal proposal
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Vote> query = cb.createQuery(getType());
        Root<Vote> rootType = query.from(getType());
        Path<UUID> proposalId = rootType.join("proposal").get("id");
        query = query.select(rootType).where(cb.equal(proposalId, proposal.getId()));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Vote> getType() {
        return Vote.class;
    }
}