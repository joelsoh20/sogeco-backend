package com.sogeco.fleet.modules.client.dto;

import com.sogeco.fleet.modules.client.Client;

public record ClientResponse(
        Long id,
        String code,
        String companyName,
        String contactName,
        String phone,
        String email,
        String address,
        Long cityId,
        String cityName,
        String taxNumber,
        Integer paymentTermsDays,
        String notes,
        Boolean active
) {
    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getCode(),
                client.getCompanyName(),
                client.getContactName(),
                client.getPhone(),
                client.getEmail(),
                client.getAddress(),
                client.getCity() == null ? null : client.getCity().getId(),
                client.getCity() == null ? null : client.getCity().getName(),
                client.getTaxNumber(),
                client.getPaymentTermsDays(),
                client.getNotes(),
                client.getActive());
    }
}
