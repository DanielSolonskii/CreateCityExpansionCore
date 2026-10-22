# City territory access v1 / claim limit — 2026-10-18

Implemented in the existing CityManager, City Relations UI and protection path.

- Maximum 25 claimed plots per city, including the pylon's seed plot. The server rejects the 26th new plot with LIMIT_REACHED; duplicate requests remain ALREADY_OWNED. Existing saved territories above 25 are retained, but cannot expand.
- New independent outgoing access toggle per target city in City Pylon → Relations. Only owner/admin can modify it; actor permissions are rechecked inside CityManager. Every current member of the allowed city can build/break/use blocks through existing protection events. Removing membership or revoking permission takes effect on the next server check.
- Access is directional. Partnership/trust/friendship alone does not grant access. Foreign access does not authorize claim, city administration, role changes, project administration or treasury actions. Own-city role rules remain unchanged.
- Additive TerritoryAccess NBT list (From/To UUID) persists grants. Missing list means no grants; stale/self/nonexistent-city grants are rejected on load. City deletion cleans grants in both directions.
- Protection and relations handlers now use canonical Overworld CityManager while preserving the queried block's dimension.
- Relations rows show outgoing Land open/closed control, incoming permission text and explanatory tooltip; draw and hit test share ActionBounds. Existing trust button remains separate. RU/EN strings included.
- Relations command/state payload IDs advance to v2, adding access booleans and explicit ALLOW/DENY actions. Matching client/server mod update is required. Existing SavedData/UUID/claim coordinates and economy remain unchanged.

Validation:
- compileJava --offline --no-configuration-cache: passed.
- checkCityRelations: 64 checks passed (grant authority, default denial for partners, directionality, guest resident, leaving city, persistence, old-save defaults, revoke, deletion; 25th succeeds/26th fails, duplicate, oversized saved cities).
- checkCityTerritory: 2109 existing checks passed.
- jar --offline --no-configuration-cache: passed; final artifact build/libs/createcityexpansioncore-1.0.2.1.jar.
- Full check suite and Minecraft runtime not run. No production deployment or restart performed.

Runtime checklist: two cities A/B; initially B cannot modify/use A land; A admin enables access for B and B owner/resident can build/use; A still cannot build in B until separately enabled; A resident cannot change the toggle; revoke; restart/recheck; claim up to 25 and confirm 26th refusal; inspect an existing >25-plot city remains intact. Verify relations row/tooltip and scrolling at GUI scales 2–4.

Scope limitation: this extends existing break/place/right-click protection. It does not add explosion/piston/fluid/Create automation protection or a zone/ACL system.
