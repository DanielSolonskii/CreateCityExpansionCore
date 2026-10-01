package com.example.examplemod.railway;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import com.example.examplemod.status.CcecStatus;

import java.util.UUID;

/**
 * Server-authoritative projection of one registered train, plus everything the management
 * UI needs to answer "whose is it, where is it, and what is it doing" without a second request.
 *
 * @param createTrainId  Create-side identity; stable across CCEC re-registration.
 * @param ownerLabel     city name for city trains, player name for a personal registration.
 * @param currentStation name of the station the train currently sits at, empty when none.
 * @param activeTrip     {@code RailwayTripLedger.Status} token while a trip is running, else empty.
 * @param activeShipment {@code ShipmentStatus} token while a shipment is not settled, else empty.
 * @param canManage      the viewer may change type/line/registration of this train.
 */
public record RailwayTrainSnapshot(UUID fleetTrainId, String name, RailwayTypes.TrainType type,
                                   @Nullable UUID activeLineId, String lineName, String statusKey,
                                   boolean cityOwned, boolean automationSuspended,
                                   @Nullable ResourceLocation dimension, double x, double z,
        String nextStation, double speed, CcecStatus uiStatus,
        UUID createTrainId, String ownerLabel, String currentStation,
        String activeTrip, String activeShipment, boolean canManage) {}
