package com.sogeco.fleet.modules.partner.dto;

import com.sogeco.fleet.common.enums.PartnerType;
import com.sogeco.fleet.modules.partner.Partner;

public record PartnerResponse(
        Long id, String code, String name, PartnerType partnerType,
        String contactName, String phone, String email, String address,
        Long cityId, String cityName, String taxNumber, String notes, Boolean active) {

    public static PartnerResponse from(Partner p) {
        return new PartnerResponse(
                p.getId(), p.getCode(), p.getName(), p.getPartnerType(),
                p.getContactName(), p.getPhone(), p.getEmail(), p.getAddress(),
                p.getCity() == null ? null : p.getCity().getId(),
                p.getCity() == null ? null : p.getCity().getName(),
                p.getTaxNumber(), p.getNotes(), p.getActive());
    }
}
