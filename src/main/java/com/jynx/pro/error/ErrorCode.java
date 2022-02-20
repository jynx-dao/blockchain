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
    public static final String CANNOT_GET_SUPPLY = "An error occurred whilst trying to get the total supply.";
    public static final String ASSET_NOT_SUSPENDED = "You can only unsuspend a suspended asset.";
    public static final String ASSET_NOT_ACTIVE = "This asset is not active.";
    public static final String ORDER_NOT_FOUND = "The order was not found.";
    public static final String PERMISSION_DENIED = "You do not have permission to perform this action.";
    public static final String INVALID_ORDER_STATUS = "The order status is invalid.";
    public static final String INVALID_ORDER_TYPE = "The order type is invalid.";
    public static final String MARKET_NOT_ACTIVE = "This market is not active.";
    public static final String MARKET_NOT_SUSPENDED = "This market is not suspended/";
    public static final String INSUFFICIENT_MARGIN = "Insufficient margin.";
    public static final String MARGIN_NOT_ALLOCATED = "Margin has not been allocated";
    public static final String USER_NOT_FOUND = "The user was not found.";
    public static final String DEPOSIT_NOT_FOUND = "The deposit was not found.";
    public static final String CANNOT_ADD_ASSET = "Cannot add asset to bridge.";
    public static final String CANNOT_REMOVE_ASSET = "Cannot remove asset from bridge.";
    public static final String INSUFFICIENT_PASSIVE_VOLUME = "There is insufficient passive volume on the order book.";
    public static final String POST_ONLY_FAILED = "Order would immediately execute.";
    public static final String INVALID_TAKER_FEE = "Taker fee must be greater than maker fee.";
    public static final String EMPTY_ORDER_BOOK = "The order book is empty on at least one side.";
    public static final String TOO_MANY_DECIMAL_PLACES = "The maximum precision is 8 decimal places.";
    public static final String CANNOT_GET_DECIMAL_PLACES = "Failed to get the decimal places for the ERC20 token.";
    public static final String CANNOT_AMEND_WOULD_EXECUTE = "Cannot amend order as would immediately execute.";
    public static final String STOP_ORDER_NOT_SUPPORTED = "Stop loss orders are not supported.";
    public static final String ORDER_SIZE_MANDATORY = "Order size is mandatory.";
    public static final String ORDER_TYPE_MANDATORY = "Order type is mandatory.";
    public static final String ORDER_MARKET_MANDATORY = "Order market ID is mandatory.";
    public static final String ORDER_SIDE_MANDATORY = "Order side is mandatory.";
    public static final String ORDER_PRICE_MANDATORY = "Order price is mandatory when type is LIMIT.";
}
