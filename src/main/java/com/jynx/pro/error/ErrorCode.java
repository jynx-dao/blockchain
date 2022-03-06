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
    public static final String DEPOSIT_NOT_FOUND = "The deposit was not found.";
    public static final String CANNOT_ADD_ASSET = "Cannot add asset to bridge.";
    public static final String CANNOT_REMOVE_ASSET = "Cannot remove asset from bridge.";
    public static final String INSUFFICIENT_PASSIVE_VOLUME = "There is insufficient passive volume on the order book.";
    public static final String POST_ONLY_FAILED = "Order would immediately execute.";
    public static final String INVALID_TAKER_FEE = "Taker fee must be greater than maker fee.";
    public static final String TOO_MANY_DECIMAL_PLACES = "The maximum precision is 8 decimal places.";
    public static final String CANNOT_GET_DECIMAL_PLACES = "Failed to get the decimal places for the ERC20 token.";
    public static final String CANNOT_AMEND_WOULD_EXECUTE = "Cannot amend order as would immediately execute.";
    public static final String ORDER_QUANTITY_MANDATORY = "Order quantity is mandatory.";
    public static final String ORDER_TYPE_MANDATORY = "Order type is mandatory.";
    public static final String ORDER_MARKET_MANDATORY = "Order market ID is mandatory.";
    public static final String ORDER_SIDE_MANDATORY = "Order side is mandatory.";
    public static final String ORDER_PRICE_MANDATORY = "Order price is mandatory when type is LIMIT.";
    public static final String NEGATIVE_QUANTITY = "Quantity cannot be negative.";
    public static final String NEGATIVE_PRICE = "Price cannot be negative.";
    public static final String INVALID_LIQUIDATION_FEE = "Liquidation fee must be less than 50% of margin requirement.";
    public static final String AMOUNT_MANDATORY = "Amount is mandatory.";
    public static final String AMOUNT_NEGATIVE = "Amount cannot be negative.";
    public static final String DESTINATION_MANDATORY = "Destination is mandatory.";
    public static final String ASSET_ID_MANDATORY = "Asset ID is mandatory.";
    public static final String INSUFFICIENT_BALANCE = "Insufficient balance.";
    public static final String ID_MANDATORY = "ID is mandatory.";
    public static final String WITHDRAWAL_NOT_FOUND = "Withdrawal was not found.";
    public static final String WITHDRAWAL_NOT_PENDING = "Withdrawal not pending.";
    public static final String CANNOT_WITHDRAW_ASSETS = "Cannot withdraw assets from bridge.";
    public static final String SIGNED_DATA_UNSUPPORTED = "Signed data oracles are not supported.";
    public static final String CANNOT_GET_BINANCE_PRICE = "Cannot get price from Binance.";
    public static final String CANNOT_GET_COINBASE_PRICE = "Cannot get price from Coinbase.";
    public static final String CANNOT_GET_POLYGON_PRICE = "Cannot get price from Polygon.";
    public static final String USER_NOT_FOUND = "User was not found.";
    public static final String ACCOUNT_NOT_FOUND = "Account was not found.";
    public static final String INTERVAL_MANDATORY = "Interval is mandatory.";
    public static final String TX_NOT_CREATED = "Write transaction not created.";
    public static final String FAILED_TO_BUILD_URL = "Could not build Tendermint request URL.";
    public static final String CREATE_WITHDRAWAL_FAILED = "Failed to create withdrawal.";
    public static final String CANCEL_WITHDRAWAL_FAILED = "Failed to cancel withdrawal.";
    public static final String CREATE_ORDER_FAILED = "Failed to create order.";
    public static final String CANCEL_ORDER_FAILED = "Failed to cancel order.";
    public static final String AMEND_ORDER_FAILED = "Failed to amend order.";
    public static final String CONFIRM_ETHEREUM_EVENTS_FAILED = "Failed to confirm Ethereum events.";
    public static final String ADD_MARKET_FAILED = "Failed to add market.";
    public static final String AMEND_MARKET_FAILED = "Failed to amend market.";
    public static final String SUSPEND_MARKET_FAILED = "Failed to suspend market.";
    public static final String UNSUSPEND_MARKET_FAILED = "Failed to unsuspend market.";
    public static final String ADD_ASSET_FAILED = "Failed to add asset.";
    public static final String SUSPEND_ASSET_FAILED = "Failed to suspend asset.";
    public static final String UNSUSPEND_ASSET_FAILED = "Failed to unsuspend asset.";
    public static final String SETTLE_MARKETS_FAILED = "Failed to settle markets.";
    public static final String SYNC_PROPOSALS_FAILED = "Failed to sync proposals.";
    public static final String CAST_VOTE_FAILED = "Failed to cast vote.";
    public static final String UNKNOWN_ERROR = "An unknown error occurred.";
    public static final String SIGNATURE_INVALID = "The signature is invalid.";
    public static final String INVALID_APP_STATE = "Blockchain started with invalid app state.";
    public static final String NONCE_ALREADY_USED = "Nonce already used.";
    public static final String NONCE_MANDATORY = "Nonce is mandatory.";
    public static final String ADD_STAKE_FAILED = "Failed to add stake.";
    public static final String REMOVE_STAKE_FAILED = "Failed to remove stake.";
    public static final String DEPOSIT_ASSET_FAILED = "Failed to deposit asset.";
}
