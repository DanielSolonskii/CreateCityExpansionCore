package com.example.examplemod.economy;

public final class EconomyTypes {
    private EconomyTypes() {}

    public enum InfrastructureType { CONTRACT_INTAKE, WAREHOUSE_GATEWAY }
    public enum GatewayMode { INPUT, OUTPUT }
    public enum ContractKind { ORDINARY, MASTER_SMART, MASTER_SPECIAL }
    public enum ContractMode { EXCLUSIVE, COMMUNITY }
    public enum ShipmentStatus { PREPARING, IN_TRANSIT, UNLOADING, COMPLETED, CANCELLED, FAILED }
    public enum IntakeAttributionError { NONE, STATION_NOT_ASSIGNED, STATION_MISSING, TRAIN_NOT_PRESENT, TRAIN_NOT_REGISTERED, SHIPMENT_NOT_FOUND, WRONG_INTAKE, WRONG_ITEM, CONTRACT_INVALID }
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
}
