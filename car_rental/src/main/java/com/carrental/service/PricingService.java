package com.carrental.service;

import com.carrental.model.Car;
import com.carrental.model.RentalPeriod;
import java.math.BigDecimal;

/** Calculates rental totals using whole rental days and decimal-safe arithmetic. */
public final class PricingService {
    public BigDecimal calculateTotal(Car car, RentalPeriod period) {
        return car.getDailyPrice().multiply(BigDecimal.valueOf(period.rentalDays()));
    }
}