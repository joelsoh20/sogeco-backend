package com.sogeco.fleet.common.event;

/**
 * Alerte creee. Publie par le service, consomme par la diffusion
 * WebSocket et les notifications : le metier ignore tout du transport.
 */
public record AlertTriggeredEvent(Long alertId) {
}
