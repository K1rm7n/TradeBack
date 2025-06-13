package com.tradeback.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class IndicatorRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Interval is required")
    private String interval;

    @NotBlank(message = "First indicator type is required")
    private String firstIndicatorType;

    @NotNull(message = "First period is required")
    @Min(value = 2, message = "Period must be at least 2")
    @Max(value = 200, message = "Period must not exceed 200")
    private Integer firstPeriod;

    @NotBlank(message = "Second indicator type is required")
    private String secondIndicatorType;

    @NotNull(message = "Second period is required")
    @Min(value = 2, message = "Period must be at least 2")
    @Max(value = 200, message = "Period must not exceed 200")
    private Integer secondPeriod;

    @NotBlank(message = "Third indicator type is required")
    private String thirdIndicatorType;

    @NotNull(message = "Third period is required")
    @Min(value = 2, message = "Period must be at least 2")
    @Max(value = 200, message = "Period must not exceed 200")
    private Integer thirdPeriod;
}