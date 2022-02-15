package com.jynx.pro.error;

public class ErrorCode {
    public static final String PROPOSAL_NOT_FOUND = "The proposal was not found.";
    public static final String ALREADY_VOTED = "You have already voted on this proposal.";
    public static final String CONFIG_NOT_FOUND = "Network config was not found.";
    public static final String ASSET_EXISTS_ALREADY = "This asset already exists.";
    public static final String VOTING_DISABLED = "You cannot vote on this proposal.";
    public static final String PROPOSAL_NOT_ENACTED = "This proposal is not enacted.";
    public static final String ASSET_NOT_FOUND = "The asset was not found.";
    public static final String MAX_BULK_EXCEEDED = "The maximum amount of items in a bulk request were exceeded.";
    public static final String CLOSE_BEFORE_OPEN = "Proposal closes before it opens.";
    public static final String ENACT_BEFORE_CLOSE = "Proposal enacts before voting closes.";
    public static final String INSUFFICIENT_PROPOSER_STAKE = "You do not have enough stake to make a proposal.";
    public static final String MARKET_NOT_FOUND = "The market was not found.";
    public static final String ORACLE_NOT_DEFINED = "Oracles must be defined to add a market.";
    public static final String CANNOT_GET_JYNX_SUPPLY = "An error occurred whilst trying to get the supply of JYNX tokens.";
    public static final String ASSET_NOT_SUSPENDED = "You can only unsuspend a suspended asset.";
    public static final String ASSET_NOT_ACTIVE = "This asset is not active.";
    public static final String UNKNOWN_MARKET_SIDE = "Market side must be buy or sell.";
    public static final String ORDER_NOT_FOUND = "The order was not found.";
    public static final String PERMISSION_DENIED = "You do not have permission to perform this action.";
    public static final String INVALID_ORDER_STATUS = "The order status is invalid.";
    public static final String INVALID_ORDER_TYPE = "The order type is invalid.";
    public static final String MARKET_NOT_ACTIVE = "This market is not active.";
}
