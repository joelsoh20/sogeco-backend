package com.sogeco.fleet.modules.agency.dto;

import com.sogeco.fleet.common.enums.SiteType;
import com.sogeco.fleet.modules.agency.Agency;

import java.math.BigDecimal;

public record AgencyResponse(
        Long id,
        String code,
        String name,
        Long cityId,
        String cityName,
        Long quartierId,
        String quartierName,
        SiteType siteType,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean active
) {
    public static AgencyResponse from(Agency agency) {
        return new AgencyResponse(
                agency.getId(),
                agency.getCode(),
                agency.getName(),
                agency.getCity().getId(),
                agency.getCity().getName(),
                agency.getQuartier() == null ? null : agency.getQuartier().getId(),
                agency.getQuartier() == null ? null : agency.getQuartier().getName(),
                agency.getSiteType(),
                agency.getAddress(),
                agency.getPhone(),
                agency.getLatitude(),
                agency.getLongitude(),
                agency.getActive());
    }
}
