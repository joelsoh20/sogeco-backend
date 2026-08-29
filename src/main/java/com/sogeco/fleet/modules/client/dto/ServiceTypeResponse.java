package com.sogeco.fleet.modules.client.dto;

import com.sogeco.fleet.modules.client.ServiceType;

public record ServiceTypeResponse(
        Long id, String code, String label, String description, Boolean billable, Boolean active) {

    public static ServiceTypeResponse from(ServiceType type) {
        return new ServiceTypeResponse(
                type.getId(), type.getCode(), type.getLabel(),
                type.getDescription(), type.getBillable(), type.getActive());
    }
}
