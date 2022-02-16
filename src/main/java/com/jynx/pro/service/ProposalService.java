package com.jynx.pro.service;

import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.constant.ProposalType;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.entity.User;
import com.jynx.pro.entity.Vote;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.ProposalRepository;
import com.jynx.pro.repository.VoteRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProposalService {

    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private MarketService marketService;
    @Autowired
    private ProposalRepository proposalRepository;
    @Autowired
    private VoteRepository voteRepository;
    @Autowired
    private StakeService stakeService;
    @Autowired
    private UUIDUtils uuidUtils;

    /**
     * Enact {@link Proposal}s
     */
    public void enact() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.APPROVED)
                .stream().filter(p -> p.getEnactmentTime() < configService.getTimestamp()).collect(Collectors.toList());
        for(Proposal proposal : proposals) {
            proposal.setStatus(ProposalStatus.ENACTED);
            switch (proposal.getType()) {
                case ADD_ASSET:
                    assetService.add(proposal);
                    break;
                case SUSPEND_ASSET:
                    assetService.suspend(proposal);
                    break;
                case UNSUSPEND_ASSET:
                    assetService.unsuspend(proposal);
                    break;
                case ADD_MARKET:
                    marketService.add(proposal);
                    break;
                case AMEND_MARKET:
                    marketService.amend(proposal);
                    break;
                case SUSPEND_MARKET:
                    marketService.suspend(proposal);
                    break;
                case UNSUSPEND_MARKET:
                    marketService.unsuspend(proposal);
                    break;
            }
        }
        proposalRepository.saveAll(proposals);
    }

    private BigDecimal getTotalVotes(
            final Proposal proposal
    ) {
        List<Vote> votes = voteRepository.findByProposal(proposal);
        BigDecimal totalVotes = BigDecimal.ZERO;
        for(Vote vote : votes) {
            totalVotes = totalVotes.add(stakeService.getStakeForUser(vote.getUser()));
        }
        return totalVotes;
    }

    public void checkEnacted(
            final Proposal proposal
    ) {
        if(!ProposalStatus.ENACTED.equals(proposal.getStatus())) {
            throw new JynxProException(ErrorCode.PROPOSAL_NOT_ENACTED);
        }
    }

    private boolean isAboveThreshold(
            final Proposal proposal
    ) {
        BigDecimal totalVotes = getTotalVotes(proposal);
        BigDecimal totalSupply = ethereumService.totalSupply(configService.get().getGovernanceTokenAddress());
        double threshold = totalVotes.doubleValue() / totalSupply.doubleValue();
        return threshold >= configService.get().getParticipationThreshold().doubleValue();
    }

    /**
     * Approve {@link Proposal}s
     */
    public void approve() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.OPEN);
        for(Proposal proposal : proposals) {
            if(isAboveThreshold(proposal)) {
                proposal.setStatus(ProposalStatus.APPROVED);
            }
        }
        proposalRepository.saveAll(proposals);
    }

    /**
     * Reject {@link Proposal}s
     */
    public void reject() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.OPEN)
                .stream().filter(p -> p.getClosingTime() < configService.getTimestamp()).collect(Collectors.toList());
        for(Proposal proposal : proposals) {
            if(!isAboveThreshold(proposal)) {
                proposal.setStatus(ProposalStatus.REJECTED);
                if(ProposalType.ADD_ASSET.equals(proposal.getType())) {
                    assetService.reject(proposal);
                } else if(ProposalType.ADD_MARKET.equals(proposal.getType())) {
                    marketService.reject(proposal);
                }
            }
        }
        proposalRepository.saveAll(proposals);
    }

    /**
     * Open {@link Proposal}s
     */
    public void open() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.CREATED)
                .stream().filter(p -> p.getOpenTime() < configService.getTimestamp()).collect(Collectors.toList());
        proposals.forEach(p -> p.setStatus(ProposalStatus.OPEN));
        proposalRepository.saveAll(proposals);
    }

    /**
     * Cast a {@link Vote} on a {@link Proposal}
     *
     * @param inFavour for / against
     * @param id the {@link  Proposal} ID
     * @param user the authenticated {@link User}
     */
    public void vote(
            final boolean inFavour,
            final UUID id,
            final User user
    ) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.PROPOSAL_NOT_FOUND));
        List<Vote> votes = voteRepository.findByProposalAndUser(proposal, user);
        if(votes.size() > 0) {
            throw new JynxProException(ErrorCode.ALREADY_VOTED);
        }
        if(!proposal.getStatus().equals(ProposalStatus.OPEN) && !proposal.getUser().getId().equals(user.getId())) {
            throw new JynxProException(ErrorCode.VOTING_DISABLED);
        }
        Vote vote = new Vote()
                .setId(uuidUtils.next())
                .setProposal(proposal)
                .setUser(user)
                .setInFavour(inFavour);
        voteRepository.save(vote);
    }

    /**
     * Get {@link Proposal}s with given {@link ProposalStatus}
     *
     * @param status the {@link ProposalStatus}
     * @param page the page number
     * @param size the page size
     * @param sortBy the sort field
     * @param direction the sort direction
     * @return list of {@link Proposal}
     */
    public List<Proposal> getByStatus(
            final ProposalStatus status,
            final int page,
            final int size,
            final String sortBy,
            final Sort.Direction direction
    ) {
        return proposalRepository.findByStatus(status, PageRequest.of(page, size, Sort.by(direction, sortBy)));
    }

    /**
     * Get all {@link Proposal}s
     *
     * @param page the page number
     * @param size the page size
     * @param sortBy the sort field
     * @param direction the sort direction
     * @return list of {@link Proposal}
     */
    public List<Proposal> getAll(
            final int page,
            final int size,
            final String sortBy,
            final Sort.Direction direction
    ) {
        return proposalRepository.findAll(PageRequest.of(page, size, Sort.by(direction, sortBy))).toList();
    }

    /**
     * Check the {@link Proposal} times are valid
     *
     * @param openTime opening time
     * @param closingTime closing time
     * @param enactmentTime enactment time
     */
    public void checkProposalTimes(
            final long openTime,
            final long closingTime,
            final long enactmentTime
    ) {
        if(closingTime < openTime) {
            throw new JynxProException(ErrorCode.CLOSE_BEFORE_OPEN);
        }
        if(enactmentTime < closingTime) {
            throw new JynxProException(ErrorCode.ENACT_BEFORE_CLOSE);
        }
    }

    /**
     * Return the valid open time for a proposal
     *
     * @param openTime the timestamp
     *
     * @return the timestamp
     */
    public Long getOpenTime(
            final Long openTime
    ) {
        Long ts = configService.getTimestamp();
        if(openTime - ts < configService.get().getMinOpenDelay()) {
            return ts + configService.get().getMinOpenDelay();
        }
        return openTime;
    }

    /**
     * Return the valid closing time for a proposal
     *
     * @param closingTime the timestamp
     *
     * @return the timestamp
     */
    public Long getClosingTime(
            final Long closingTime
    ) {
        Long ts = configService.getTimestamp();
        if(closingTime - ts < configService.get().getMinClosingDelay()) {
            return ts + configService.get().getMinClosingDelay();
        }
        return closingTime;
    }

    /**
     * Return the valid enactment time for a proposal
     *
     * @param enactmentTime the timestamp
     *
     * @return the timestamp
     */
    public Long getEnactmentTime(
            final Long enactmentTime
    ) {
        Long ts = configService.getTimestamp();
        if(enactmentTime - ts < configService.get().getMinEnactmentDelay()) {
            return ts + configService.get().getMinEnactmentDelay();
        }
        return enactmentTime;
    }

    /**
     * Create a new {@link Proposal}
     *
     * @param user the creator {@link User}
     * @param openTime the opening time
     * @param closingTime the closing time
     * @param enactmentTime the enactment time
     * @param linkedId the linked object
     * @param type the {@link ProposalType}
     */
    public void create(
            final User user,
            final Long openTime,
            final Long closingTime,
            final Long enactmentTime,
            final UUID linkedId,
            final ProposalType type
    ) {
        Proposal proposal = new Proposal()
                .setUser(user)
                .setOpenTime(getOpenTime(openTime))
                .setClosingTime(getClosingTime(closingTime))
                .setEnactmentTime(getEnactmentTime(enactmentTime))
                .setId(uuidUtils.next())
                .setLinkedId(linkedId)
                .setType(type)
                .setStatus(ProposalStatus.CREATED);
        proposal = proposalRepository.save(proposal);
        vote(true, proposal.getId(), user);
    }
}