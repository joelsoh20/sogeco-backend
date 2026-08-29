package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

public record ClientMarginSummary(Long clientId, String clientName, BigDecimal margin) {
}
