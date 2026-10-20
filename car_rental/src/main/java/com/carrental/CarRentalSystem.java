package com.carrental;

import com.carrental.enums.CarStatus;
import com.carrental.enums.CarType;
import com.carrental.enums.PaymentMethod;
import com.carrental.model.Car;
import com.carrental.model.Customer;
import com.carrental.payment.CreditCardPaymentProcessor;
import com.carrental.payment.PayPalPaymentProcessor;
import com.carrental.repository.InMemoryDatabase;
import com.carrental.service.CarInventoryService;
import com.carrental.service.CarSearchService;
import com.carrental.service.CustomerService;
import com.carrental.service.PaymentService;
import com.carrental.service.PricingService;
import com.carrental.service.ReservationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Application facade that composes services and seeds a small runnable example. */
public final class CarRentalSystem {
    private final InMemoryDatabase database;
    private final CarInventoryService carInventoryService;
    private final CarSearchService carSearchService;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final ReservationService reservationService;

    public CarRentalSystem() {
        database = new InMemoryDatabase();
        carInventoryService = new CarInventoryService(database);
        carSearchService = new CarSearchService(database);
        customerService = new CustomerService(database);
        paymentService = new PaymentService(database, Map.of(
                PaymentMethod.CREDIT_CARD, new CreditCardPaymentProcessor(),
                PaymentMethod.PAYPAL, new PayPalPaymentProcessor()));
        reservationService = new ReservationService(database, carInventoryService, customerService,
                carSearchService, new PricingService(), paymentService);
        seedSampleData();
    }

    private void seedSampleData() {
        carInventoryService.addCar(new Car("C-100", "Toyota", "Corolla", 2023, "ABC-100",
                CarType.SEDAN, new BigDecimal("48.00"), CarStatus.ACTIVE));
        carInventoryService.addCar(new Car("C-200", "Honda", "CR-V", 2024, "ABC-200",
                CarType.SUV, new BigDecimal("75.00"), CarStatus.ACTIVE));
        carInventoryService.addCar(new Car("C-300", "Ford", "Focus", 2022, "ABC-300",
                CarType.HATCHBACK, new BigDecimal("39.00"), CarStatus.UNDER_MAINTENANCE));
        customerService.registerCustomer(new Customer("U-100", "Alex Morgan", "555-0100",
                "alex@example.com", "DL-100", LocalDate.now().plusYears(3)));
    }

    public InMemoryDatabase getDatabase() { return database; }
    public CarInventoryService getCarInventoryService() { return carInventoryService; }
    public CarSearchService getCarSearchService() { return carSearchService; }
    public CustomerService getCustomerService() { return customerService; }
    public PaymentService getPaymentService() { return paymentService; }
    public ReservationService getReservationService() { return reservationService; }
}