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
import com.jynx.pro.request.BatchValidatorRequest;
import com.jynx.pro.request.CastVoteRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
     * Synchronise the state of {@link Proposal}s
     *
     * @return the updated {@link List<Proposal>}
     */
    public List<Proposal> sync(
            final BatchValidatorRequest request
    ) {
        log.debug(request.toString());
        List<Proposal> proposals = new ArrayList<>();
        proposals.addAll(open());
        proposals.addAll(reject());
        proposals.addAll(approve());
        proposals.addAll(enact());
        return proposals;
    }

    /**
     * Enact {@link Proposal}s
     */
    public List<Proposal> enact() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.APPROVED)
                .stream().filter(p -> p.getEnactmentTime() < configService.getTimestamp()).collect(Collectors.toList());
        for(Proposal proposal : proposals) {
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
                    proposal.setStatus(ProposalStatus.ENACTED);
                    marketService.add(proposal);
                    break;
                case AMEND_MARKET:
                    proposal.setStatus(ProposalStatus.ENACTED);
                    marketService.amend(proposal);
                    break;
                case SUSPEND_MARKET:
                    proposal.setStatus(ProposalStatus.ENACTED);
                    marketService.suspend(proposal);
                    break;
                case UNSUSPEND_MARKET:
                    proposal.setStatus(ProposalStatus.ENACTED);
                    marketService.unsuspend(proposal);
                    break;
            }
        }
        return proposalRepository.saveAll(proposals);
    }

    /**
     * Count the total votes cast on a {@link Proposal}
     *
     * @param proposal {@link Proposal}
     *
     * @return the total number of votes
     */
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

    /**
     * Count the total votes in-favour of a {@link Proposal}
     *
     * @param proposal {@link Proposal}
     *
     * @return the total number of votes
     */
    private BigDecimal getTotalVotesInFavour(
            final Proposal proposal
    ) {
        List<Vote> votes = voteRepository.findByProposal(proposal).stream()
                .filter(Vote::getInFavour)
                .collect(Collectors.toList());
        BigDecimal totalVotes = BigDecimal.ZERO;
        for(Vote vote : votes) {
            totalVotes = totalVotes.add(stakeService.getStakeForUser(vote.getUser()));
        }
        return totalVotes;
    }

    /**
     * Check if a {@link Proposal} has been enacted
     *
     * @param proposal {@link Proposal}
     */
    public void checkEnacted(
            final Proposal proposal
    ) {
        if(!ProposalStatus.ENACTED.equals(proposal.getStatus())) {
            throw new JynxProException(ErrorCode.PROPOSAL_NOT_ENACTED);
        }
    }

    /**
     * Check if a {@link Proposal} meets the participation threshold
     *
     * @param proposal {@link Proposal}
     *
     * @return true / false
     */
    private boolean meetsParticipationThreshold(
            final Proposal proposal
    ) {
        BigDecimal totalVotes = getTotalVotes(proposal);
        BigDecimal totalSupply = ethereumService.totalSupply(configService.get().getGovernanceTokenAddress());
        double threshold = totalVotes.doubleValue() / totalSupply.doubleValue();
        return threshold >= configService.get().getParticipationThreshold().doubleValue();
    }

    /**
     * Check if a {@link Proposal} has sufficient votes in-favour to pass
     *
     * @param proposal {@link Proposal}
     *
     * @return true / false
     */
    private boolean hasEnoughVotesInFavour(
            final Proposal proposal
    ) {
        BigDecimal totalVotes = getTotalVotes(proposal);
        BigDecimal totalVotesInFavour = getTotalVotesInFavour(proposal);
        double threshold = totalVotesInFavour.doubleValue() / totalVotes.doubleValue();
        return threshold >= configService.get().getApprovalThreshold().doubleValue();
    }

    /**
     * Approve {@link Proposal}s
     */
    public List<Proposal> approve() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.OPEN);
        for(Proposal proposal : proposals) {
            if(meetsParticipationThreshold(proposal) && hasEnoughVotesInFavour(proposal)) {
                proposal.setStatus(ProposalStatus.APPROVED);
            }
        }
        return proposalRepository.saveAll(proposals);
    }

    /**
     * Reject {@link Proposal}s
     */
    public List<Proposal> reject() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.OPEN)
                .stream().filter(p -> p.getClosingTime() < configService.getTimestamp()).collect(Collectors.toList());
        List<ProposalType> assetProposalTypes = List.of(ProposalType.ADD_ASSET, ProposalType.SUSPEND_ASSET,
                ProposalType.UNSUSPEND_ASSET);
        for(Proposal proposal : proposals) {
            if(!meetsParticipationThreshold(proposal) || !hasEnoughVotesInFavour(proposal)) {
                proposal.setStatus(ProposalStatus.REJECTED);
                if(assetProposalTypes.contains(proposal.getType())) {
                    assetService.reject(proposal);
                } else {
                    marketService.reject(proposal);
                }
            }
        }
        return proposalRepository.saveAll(proposals);
    }

    /**
     * Open {@link Proposal}s
     */
    public List<Proposal> open() {
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.CREATED)
                .stream().filter(p -> p.getOpenTime() < configService.getTimestamp()).collect(Collectors.toList());
        proposals.forEach(p -> p.setStatus(ProposalStatus.OPEN));
        return proposalRepository.saveAll(proposals);
    }

    /**
     * Cast a {@link Vote} on a {@link Proposal}
     *
     * @param request {@link CastVoteRequest}
     *
     * @return {@link Vote}
     */
    public Vote vote(
        final CastVoteRequest request
    ) {
        Proposal proposal = proposalRepository.findById(request.getId())
                .orElseThrow(() -> new JynxProException(ErrorCode.PROPOSAL_NOT_FOUND));
        List<Vote> votes = voteRepository.findByProposalAndUser(proposal, request.getUser());
        if(votes.size() > 0) {
            throw new JynxProException(ErrorCode.ALREADY_VOTED);
        }
        if(!List.of(ProposalStatus.OPEN, ProposalStatus.APPROVED).contains(proposal.getStatus()) &&
                !proposal.getUser().getId().equals(request.getUser().getId())) {
            throw new JynxProException(ErrorCode.VOTING_DISABLED);
        }
        Vote vote = new Vote()
                .setId(uuidUtils.next())
                .setProposal(proposal)
                .setUser(request.getUser())
                .setInFavour(request.getInFavour());
        return voteRepository.save(vote);
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
    public Proposal create(
            final User user,
            final Long openTime,
            final Long closingTime,
            final Long enactmentTime,
            final UUID linkedId,
            final ProposalType type
    ) {
        return create(user, openTime, closingTime, enactmentTime, linkedId, type, null);
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
     * @param nonce the nonce for the bridge
     */
    public Proposal create(
            final User user,
            final Long openTime,
            final Long closingTime,
            final Long enactmentTime,
            final UUID linkedId,
            final ProposalType type,
            final String nonce
    ) {
        Proposal proposal = new Proposal()
                .setUser(user)
                .setOpenTime(getOpenTime(openTime))
                .setClosingTime(getClosingTime(closingTime))
                .setEnactmentTime(getEnactmentTime(enactmentTime))
                .setId(uuidUtils.next())
                .setLinkedId(linkedId)
                .setType(type)
                .setStatus(ProposalStatus.CREATED)
                .setNonce(nonce);
        proposal = proposalRepository.save(proposal);
        CastVoteRequest castVoteRequest = new CastVoteRequest()
                .setId(proposal.getId())
                .setInFavour(true);
        castVoteRequest.setUser(user);
        vote(castVoteRequest);
        return proposal;
    }

    /**
     * Check if a {@link Proposal} already exists with a specified nonce
     *
     * @param nonce the nonce
     *
     * @return true / false
     */
    public boolean existsWithNonce(
            final String nonce
    ) {
        return proposalRepository.findByNonce(nonce).size() > 0;
    }
}