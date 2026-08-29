package com.sogeco.fleet.common.event;

import com.sogeco.fleet.modules.tracking.dto.LivePosition;

/** Position traitee et prete a etre diffusee. */
public record PositionReceivedEvent(LivePosition position) {
}
