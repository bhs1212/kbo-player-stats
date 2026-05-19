package com.kbo.stats.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
public class CalendarEventDto {
    private Long id;
    private String title;
    private String start;
    private String backgroundColor;
    private String borderColor;
    private Map<String, Object> extendedProps;
}
