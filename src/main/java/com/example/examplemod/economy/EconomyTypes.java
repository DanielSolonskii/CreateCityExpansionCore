package com.example.examplemod.economy;

public final class EconomyTypes {
    private EconomyTypes() {}

    public enum InfrastructureType { CONTRACT_INTAKE, WAREHOUSE_GATEWAY }
    public enum GatewayMode { INPUT, OUTPUT }
    public enum ContractKind { ORDINARY, MASTER_SMART, MASTER_SPECIAL }
    public enum ContractMode { EXCLUSIVE, COMMUNITY }
    public enum ShipmentStatus { PREPARING, IN_TRANSIT, UNLOADING, COMPLETED, CANCELLED, FAILED }
    public enum IntakeAttributionError { NONE, INVALID_MANIFEST, INVALID_SHIPMENT, WRONG_INTAKE, WRONG_ITEM, WRONG_CONTRACTOR, CONTRACT_INVALID, CONTRACT_EXPIRED, CONTRACT_COMPLETED, EXCESS_CARGO, SHIPMENT_ALREADY_USED, IMPORT_ORDER_INVALID, WRONG_RECEIVING_CITY }
    public enum ManifestState { ACTIVE, COMPLETED, EXPIRED, INVALID }
    public enum ShipmentTokenKind { EXPORT_CONTRACT, IMPORT_ORDER }
    public enum ShipmentTokenState { ACTIVE, CONSUMED, INVALID }
    public enum ContractState {
        OPEN, ACCEPTED, IN_DELIVERY, DELIVERED, COMPLETED, CANCELLED, FAILED, DISPUTED;

        public boolean terminal() {
            return this == COMPLETED || this == CANCELLED || this == FAILED;
        }
    }
    public enum ListingType { BUY, SELL }
    public enum ListingState {
        OPEN, RESERVED, HANDED_OVER, COMPLETED, CANCELLED, DISPUTED;

        public boolean terminal() {
            return this == COMPLETED || this == CANCELLED;
        }
    }
    public enum DemandLevel { LOW, NORMAL, HIGH, CRITICAL }
    public enum MarketDirection { SELL_TO_MASTER, BUY_FROM_MASTER }
    public enum MarketOfferStatus { OPEN, RESERVED, DISPATCHED, IN_PROGRESS, COMPLETED, EXPIRED, CANCELLED, FAILED;
        public boolean terminal(){return this==COMPLETED||this==EXPIRED||this==CANCELLED||this==FAILED;}
    }
}
