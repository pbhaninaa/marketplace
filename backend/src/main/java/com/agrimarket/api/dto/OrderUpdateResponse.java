package com.agrimarket.api.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderUpdateResponse {
    private String type;
    private Object data;
}