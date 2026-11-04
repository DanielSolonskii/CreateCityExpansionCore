package com.example.examplemod.cargo.center;

/** Permission to cancel is separate from proof that escrow may be released. */
public final class ShipmentCancellation {
    private ShipmentCancellation() {}
    public static boolean allowed(CargoCommerceBook.Order order,CargoShipmentRegistry.Stage stage,
                                  boolean packagingClaimed,int parcels,boolean payerManages){
        return payerManages&&order!=null&&stage!=CargoShipmentRegistry.Stage.COMPLETED
            &&stage!=CargoShipmentRegistry.Stage.CANCELLED
            &&(order.state()==CargoCommerceBook.State.HELD||order.state()==CargoCommerceBook.State.ATTEMPTED
                ||order.state()==CargoCommerceBook.State.BLOCKED);
    }
    public static boolean immediateRefund(CargoCommerceBook.Order order,CargoShipmentRegistry.Stage stage,
                                          boolean packagingClaimed,int parcels){
        return order!=null&&order.state()==CargoCommerceBook.State.HELD
            &&stage==CargoShipmentRegistry.Stage.REQUESTED&&!packagingClaimed&&parcels==0;
    }
}
