package com.example.examplemod.railway;

import com.example.examplemod.mixin.CarriageBogeyAccessor;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.track.TrackMaterial;

import java.util.HashSet;
import java.util.Set;

/** Mirrors Create Navigation's bogey compatibility intersection without occupying a Create package. */
final class CreateNavigationAccess {
    private CreateNavigationAccess() {}

    static Set<TrackMaterial.TrackType> validTrackTypes(Train train) {
        Set<TrackMaterial.TrackType> valid = new HashSet<>();
        for (int index = 0; index < train.carriages.size(); index++) {
            Carriage carriage = train.carriages.get(index);
            CarriageBogey leading = carriage.leadingBogey();
            var leadingTypes = ((CarriageBogeyAccessor) (Object) leading).ccec$getType()
                    .getValidPathfindingTypes(leading.getStyle());
            if (index == 0) valid.addAll(leadingTypes); else valid.retainAll(leadingTypes);
            if (carriage.isOnTwoBogeys()) {
                CarriageBogey trailing = carriage.trailingBogey();
                valid.retainAll(((CarriageBogeyAccessor) (Object) trailing).ccec$getType()
                        .getValidPathfindingTypes(trailing.getStyle()));
            }
        }
        return valid;
    }
}
