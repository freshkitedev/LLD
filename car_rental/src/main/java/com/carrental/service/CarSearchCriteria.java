package com.carrental.service;

import com.carrental.enums.CarType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Optional filters for an inventory search. */
public record CarSearchCriteria(CarType type, BigDecimal minimumDailyPrice, BigDecimal maximumDailyPrice,
                                LocalDate pickupDate, LocalDate returnDate) { }