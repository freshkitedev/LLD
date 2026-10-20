package com.carrental;

import com.carrental.enums.CarStatus;
import com.carrental.enums.CarType;
import com.carrental.enums.PaymentMethod;
import com.carrental.enums.PaymentStatus;
import com.carrental.exception.ValidationException;
import com.carrental.model.Car;
import com.carrental.model.Customer;
import com.carrental.model.Payment;
import com.carrental.model.RentalPeriod;
import com.carrental.model.Reservation;
import com.carrental.payment.CreditCardPaymentProcessor;
import com.carrental.payment.PayPalPaymentProcessor;
import com.carrental.payment.PaymentProcessor;
import com.carrental.repository.InMemoryDatabase;
import com.carrental.service.CarInventoryService;
import com.carrental.service.CarSearchCriteria;
import com.carrental.service.CarSearchService;
import com.carrental.service.CustomerService;
import com.carrental.service.PaymentService;
import com.carrental.service.PricingService;
import com.carrental.service.ReservationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarRentalSystemTest {
    private InMemoryDatabase database;
    private CarInventoryService inventory;
    private CarSearchService search;
    private CustomerService customers;
    private PaymentService payments;
    private ReservationService reservations;

    @BeforeEach
    void setUp() {
        database = new InMemoryDatabase();
        inventory = new CarInventoryService(database);
        search = new CarSearchService(database);
        customers = new CustomerService(database);
        payments = paymentService(Map.of(
                PaymentMethod.CREDIT_CARD, new CreditCardPaymentProcessor(),
                PaymentMethod.PAYPAL, new PayPalPaymentProcessor()));
        reservations = reservationService(payments);
        inventory.addCar(car("CAR-1", "42.50", CarType.SEDAN));
        inventory.addCar(car("CAR-2", "75.00", CarType.SUV));
        customers.registerCustomer(customer("CUS-1", "DL-1"));
        customers.registerCustomer(customer("CUS-2", "DL-2"));
        customers.registerCustomer(customer("CUS-3", "DL-3"));
    }

    @Test
    void registersValidCustomer() {
        customers.registerCustomer(customer("CUS-4", "DL-4"));
        assertEquals("Test Customer", customers.getCustomer("CUS-4").getFullName());
    }

    @Test
    void rejectsDuplicateCustomerIdAndLicense() {
        assertThrows(ValidationException.class, () -> customers.registerCustomer(customer("CUS-1", "DL-X")));
        assertThrows(ValidationException.class, () -> customers.registerCustomer(customer("CUS-X", "DL-1")));
    }

    @Test
    void addsAndRetrievesCar() {
        assertEquals("CAR-1", inventory.getCar("CAR-1").getId());
    }

    @Test
    void searchesCarsByType() {
        List<Car> result = search.search(new CarSearchCriteria(CarType.SUV, null, null, null, null));
        assertEquals(List.of("CAR-2"), result.stream().map(Car::getId).toList());
    }

    @Test
    void searchesCarsByPriceRange() {
        List<Car> result = search.search(new CarSearchCriteria(null, new BigDecimal("40"),
                new BigDecimal("50"), null, null));
        assertEquals(List.of("CAR-1"), result.stream().map(Car::getId).toList());
    }

    @Test
    void findsCarsAvailableForPeriodAndExcludesMaintenanceCars() {
        RentalPeriod period = period(3, 5);
        reservations.createReservation("CUS-1", "CAR-1", period, PaymentMethod.CREDIT_CARD);
        assertTrue(search.search(new CarSearchCriteria(null, null, null,
                period.pickupDate(), period.returnDate())).stream().noneMatch(car -> car.getId().equals("CAR-1")));
        inventory.updateStatus("CAR-2", CarStatus.UNDER_MAINTENANCE);
        assertEquals(List.of("CAR-1"), search.search(new CarSearchCriteria(null, null, null, null, null))
            .stream().map(Car::getId).toList());
    }

    @Test
    void createsReservationWithCorrectHalfOpenRentalPrice() {
        Reservation reservation = reservations.createReservation("CUS-1", "CAR-1", period(3, 6),
                PaymentMethod.CREDIT_CARD);
        assertEquals(new BigDecimal("127.50"), reservation.getTotalPrice());
        assertEquals(PaymentStatus.SUCCESS, payments.getPaymentsForReservation(reservation.getId()).get(0).getStatus());
    }

    @Test
    void rejectsInvalidRentalDates() {
        assertThrows(ValidationException.class, () -> new RentalPeriod(LocalDate.now().plusDays(4),
                LocalDate.now().plusDays(4)));
        RentalPeriod pastPeriod = new RentalPeriod(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThrows(ValidationException.class, () -> reservations.createReservation("CUS-1", "CAR-1",
            pastPeriod, PaymentMethod.CREDIT_CARD));
    }

    @Test
    void rejectsOverlappingReservation() {
        RentalPeriod period = period(5, 8);
        reservations.createReservation("CUS-1", "CAR-1", period, PaymentMethod.CREDIT_CARD);
        assertThrows(ValidationException.class, () -> reservations.createReservation("CUS-2", "CAR-1",
                period(7, 9), PaymentMethod.PAYPAL));
    }

    @Test
    void allowsAdjacentNonOverlappingReservations() {
        reservations.createReservation("CUS-1", "CAR-1", period(5, 8), PaymentMethod.CREDIT_CARD);
        Reservation adjacent = reservations.createReservation("CUS-2", "CAR-1", period(8, 10),
                PaymentMethod.PAYPAL);
        assertNotNull(adjacent.getId());
    }

    @Test
    void modifiesReservationAndRecalculatesPrice() {
        Reservation original = reservations.createReservation("CUS-1", "CAR-1", period(3, 5),
                PaymentMethod.CREDIT_CARD);
        Reservation modified = reservations.modifyReservation(original.getId(), period(5, 8));
        assertEquals(new BigDecimal("127.50"), modified.getTotalPrice());
        assertEquals(period(5, 8), modified.getPeriod());
        assertEquals(PaymentStatus.REFUNDED, payments.getPaymentsForReservation(original.getId()).get(0).getStatus());
    }

    @Test
    void rejectsModificationConflictingWithAnotherReservation() {
        Reservation first = reservations.createReservation("CUS-1", "CAR-1", period(3, 5),
                PaymentMethod.CREDIT_CARD);
        reservations.createReservation("CUS-2", "CAR-1", period(7, 9), PaymentMethod.CREDIT_CARD);
        assertThrows(ValidationException.class,
                () -> reservations.modifyReservation(first.getId(), period(5, 8)));
    }

    @Test
    void cancellationRefundsAndReleasesDates() {
        Reservation reservation = reservations.createReservation("CUS-1", "CAR-1", period(4, 7),
                PaymentMethod.PAYPAL);
        assertEquals(com.carrental.enums.ReservationStatus.CANCELLED,
                reservations.cancelReservation(reservation.getId()).getStatus());
        assertEquals(PaymentStatus.REFUNDED, payments.getPaymentsForReservation(reservation.getId()).get(0).getStatus());
        assertNotNull(reservations.createReservation("CUS-2", "CAR-1", period(4, 7), PaymentMethod.CREDIT_CARD));
    }

    @Test
    void rejectsReservationWhenPaymentFails() {
        inventory.addCar(car("CAR-EXPENSIVE", "6000.00", CarType.LUXURY));
        assertThrows(ValidationException.class, () -> reservations.createReservation("CUS-1", "CAR-EXPENSIVE",
                period(2, 5), PaymentMethod.CREDIT_CARD));
        assertTrue(database.reservations().isEmpty());
        assertEquals(PaymentStatus.FAILED, database.paymentsForAll().get(0).getStatus());
    }

    @Test
    void preventsConcurrentOverlappingBookings() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> tryBook("CUS-1", ready, start));
            Future<Boolean> second = executor.submit(() -> tryBook("CUS-2", ready, start));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertEquals(1, database.reservations().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsDifferentCarsToProcessBookingsConcurrently() throws Exception {
        CountDownLatch enteredProcessors = new CountDownLatch(2);
        CountDownLatch releaseProcessors = new CountDownLatch(1);
        PaymentProcessor blockingProcessor = new PaymentProcessor() {
            @Override
            public boolean processPayment(String key, BigDecimal amount) {
                enteredProcessors.countDown();
                try {
                    return releaseProcessors.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            @Override
            public boolean refundPayment(String key, BigDecimal amount) { return true; }
        };
        PaymentService concurrentPayments = paymentService(Map.of(
                PaymentMethod.CREDIT_CARD, blockingProcessor, PaymentMethod.PAYPAL, blockingProcessor));
        ReservationService concurrentReservations = reservationService(concurrentPayments);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Reservation> first = executor.submit(() -> concurrentReservations.createReservation(
                    "CUS-1", "CAR-1", period(2, 4), PaymentMethod.CREDIT_CARD));
            Future<Reservation> second = executor.submit(() -> concurrentReservations.createReservation(
                    "CUS-2", "CAR-2", period(2, 4), PaymentMethod.PAYPAL));
            assertTrue(enteredProcessors.await(2, TimeUnit.SECONDS));
            releaseProcessors.countDown();
            assertNotNull(first.get());
            assertNotNull(second.get());
        } finally {
            releaseProcessors.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedPaymentIsRecordedWithoutReservation() {
        Payment payment = payments.processPayment("retry-check", "same-operation", new BigDecimal("12000"),
                PaymentMethod.CREDIT_CARD);
        Payment retry = payments.processPayment("retry-check", "same-operation", new BigDecimal("12000"),
                PaymentMethod.CREDIT_CARD);
        assertEquals(payment.getId(), retry.getId());
        assertEquals(1, payments.getPaymentsForReservation("retry-check").size());
    }

    @Test
    void rejectsDuplicateCancellationAndKeepsReservationRecord() {
        Reservation reservation = reservations.createReservation("CUS-1", "CAR-1", period(4, 6),
                PaymentMethod.CREDIT_CARD);
        reservations.cancelReservation(reservation.getId());
        assertThrows(ValidationException.class, () -> reservations.cancelReservation(reservation.getId()));
        assertTrue(database.reservations().containsKey(reservation.getId()));
    }

    @Test
    void rejectsReservationForInactiveCarAndExpiredLicensePeriod() {
        inventory.updateStatus("CAR-1", CarStatus.RETIRED);
        assertThrows(ValidationException.class, () -> reservations.createReservation("CUS-1", "CAR-1",
                period(3, 5), PaymentMethod.CREDIT_CARD));
        Customer expiringCustomer = new Customer("CUS-EXP", "Expiring License", "555-0199",
                "expire@example.com", "DL-EXP", LocalDate.now().plusDays(5));
        customers.registerCustomer(expiringCustomer);
        inventory.updateStatus("CAR-1", CarStatus.ACTIVE);
        assertThrows(ValidationException.class, () -> reservations.createReservation("CUS-EXP", "CAR-1",
                period(3, 7), PaymentMethod.CREDIT_CARD));
    }

    @Test
    void rejectsRemovalWhenReservationHistoryReferencesCar() {
        reservations.createReservation("CUS-1", "CAR-1", period(3, 5), PaymentMethod.CREDIT_CARD);
        assertThrows(ValidationException.class, () -> inventory.removeCar("CAR-1"));
    }

    private boolean tryBook(String customerId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            reservations.createReservation(customerId, "CAR-1", period(5, 8), PaymentMethod.CREDIT_CARD);
            return true;
        } catch (ValidationException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private PaymentService paymentService(Map<PaymentMethod, PaymentProcessor> processors) {
        return new PaymentService(database, processors);
    }

    private ReservationService reservationService(PaymentService paymentService) {
        return new ReservationService(database, inventory, customers, search, new PricingService(), paymentService);
    }

    private static RentalPeriod period(int pickupOffset, int returnOffset) {
        return new RentalPeriod(LocalDate.now().plusDays(pickupOffset), LocalDate.now().plusDays(returnOffset));
    }

    private static Car car(String id, String dailyPrice, CarType type) {
        return new Car(id, "Example", "Model", 2024, id + "-PLATE", type,
                new BigDecimal(dailyPrice), CarStatus.ACTIVE);
    }

    private static Customer customer(String id, String license) {
        return new Customer(id, "Test Customer", "555-0101", id.toLowerCase() + "@example.com",
                license, LocalDate.now().plusYears(5));
    }
}