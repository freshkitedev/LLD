package com.carrental.ui;

import com.carrental.CarRentalSystem;
import com.carrental.enums.CarStatus;
import com.carrental.enums.CarType;
import com.carrental.enums.PaymentMethod;
import com.carrental.exception.NotFoundException;
import com.carrental.exception.ValidationException;
import com.carrental.model.Car;
import com.carrental.model.Customer;
import com.carrental.model.RentalPeriod;
import com.carrental.model.Reservation;
import com.carrental.service.CarSearchCriteria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/** Reads console commands and renders results without owning business rules. */
public final class ConsoleUI {
    private final CarRentalSystem system;
    private final Scanner scanner;

    public ConsoleUI(CarRentalSystem system) {
        this(system, new Scanner(System.in));
    }

    public ConsoleUI(CarRentalSystem system, Scanner scanner) {
        this.system = system;
        this.scanner = scanner;
    }

    public void run() {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = readLine("Choose an option: ");
            try {
                switch (choice) {
                    case "1" -> registerCustomer(null);
                    case "2" -> addCar();
                    case "3" -> system.getCarInventoryService().getAllCars().forEach(System.out::println);
                    case "4" -> searchCars();
                    case "5" -> createReservation();
                    case "6" -> System.out.println(system.getReservationService().getReservation(
                            readLine("Reservation ID: ")));
                    case "7" -> system.getReservationService().getReservationsForCustomer(
                            readLine("Customer ID: ")).forEach(System.out::println);
                    case "8" -> modifyReservation();
                    case "9" -> cancelReservation();
                    case "10" -> viewPayments();
                    case "11" -> updateCarStatus();
                    case "12" -> running = false;
                        case "13" -> System.out.println(system.getCustomerService().getCustomer(
                            readLine("Customer ID: ")));
                        default -> System.out.println("Invalid menu choice. Enter a number from 1 to 13.");
                }
            } catch (ValidationException | NotFoundException exception) {
                System.out.println("Operation failed: " + exception.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private void printMenu() {
        System.out.println("\n=== Car Rental System ===");
        System.out.println("1. Register Customer");
        System.out.println("2. Add Car");
        System.out.println("3. View All Cars");
        System.out.println("4. Search Available Cars");
        System.out.println("5. Create Reservation");
        System.out.println("6. View Reservation");
        System.out.println("7. View Customer Reservations");
        System.out.println("8. Modify Reservation");
        System.out.println("9. Cancel Reservation");
        System.out.println("10. View Payment Details");
        System.out.println("11. Update Car Operational Status");
        System.out.println("12. Exit");
        System.out.println("13. View Customer Details");
    }

    private void registerCustomer(String knownId) {
        String id = knownId == null ? readLine("Customer ID: ") : knownId;
        Customer customer = new Customer(id, readLine("Full name: "), readLine("Contact number: "),
                readLine("Email: "), readLine("Driver's license number: "), readDate("License expiry (yyyy-mm-dd): "));
        system.getCustomerService().registerCustomer(customer);
        System.out.println("Customer registered: " + customer);
    }

    private void addCar() {
        Car car = new Car(readLine("Car ID: "), readLine("Make: "), readLine("Model: "),
                readInteger("Manufacturing year: "), readLine("License plate: "),
                readEnum("Car type " + java.util.Arrays.toString(CarType.values()) + ": ", CarType.class),
                readMoney("Daily rental price: "), CarStatus.ACTIVE);
        system.getCarInventoryService().addCar(car);
        System.out.println("Car added: " + car);
    }

    private void searchCars() {
        CarType type = readOptionalEnum("Car type (blank for any): ", CarType.class);
        BigDecimal minimum = readOptionalMoney("Minimum daily price (blank for any): ");
        BigDecimal maximum = readOptionalMoney("Maximum daily price (blank for any): ");
        String pickupText = readLine("Pickup date yyyy-mm-dd (blank for no date filter): ");
        LocalDate pickup = null;
        LocalDate returning = null;
        if (!pickupText.isBlank()) {
            pickup = parseDatePrompt(pickupText);
            returning = readDate("Return date yyyy-mm-dd: ");
        }
        system.getCarSearchService().search(new CarSearchCriteria(type, minimum, maximum, pickup, returning))
                .forEach(System.out::println);
    }

    private void createReservation() {
        String customerId = readLine("Customer ID: ");
        try {
            system.getCustomerService().getCustomer(customerId);
        } catch (NotFoundException exception) {
            if (!readLine("Customer is not registered. Register now? (y/n): ").equalsIgnoreCase("y")) {
                throw exception;
            }
            registerCustomer(customerId);
        }
        String carId = readLine("Car ID: ");
        RentalPeriod period = new RentalPeriod(readDate("Pickup date yyyy-mm-dd: "),
                readDate("Return date yyyy-mm-dd: "));
        PaymentMethod method = readEnum("Payment method " + java.util.Arrays.toString(PaymentMethod.values()) + ": ",
                PaymentMethod.class);
        Reservation reservation = system.getReservationService().createReservation(customerId, carId, period, method);
        System.out.println("Reservation confirmed: " + reservation);
        system.getPaymentService().getPaymentsForReservation(reservation.getId()).forEach(System.out::println);
    }

    private void modifyReservation() {
        String id = readLine("Reservation ID: ");
        RentalPeriod period = new RentalPeriod(readDate("New pickup date yyyy-mm-dd: "),
                readDate("New return date yyyy-mm-dd: "));
        System.out.println("Reservation updated: " + system.getReservationService().modifyReservation(id, period));
    }

    private void cancelReservation() {
        System.out.println("Reservation cancelled: " + system.getReservationService().cancelReservation(
                readLine("Reservation ID: ")));
    }

    private void viewPayments() {
        String reservationId = readLine("Reservation ID: ");
        var payments = system.getPaymentService().getPaymentsForReservation(reservationId);
        if (payments.isEmpty()) {
            System.out.println("No payment records found for that reservation.");
        } else {
            payments.forEach(System.out::println);
        }
    }

    private void updateCarStatus() {
        String carId = readLine("Car ID: ");
        CarStatus status = readEnum("New status " + java.util.Arrays.toString(CarStatus.values()) + ": ", CarStatus.class);
        system.getCarInventoryService().updateStatus(carId, status);
        System.out.println("Car status updated.");
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private LocalDate readDate(String prompt) {
        while (true) {
            try {
                return parseDate(readLine(prompt));
            } catch (DateTimeParseException exception) {
                System.out.println("Enter a valid date in yyyy-mm-dd format.");
            }
        }
    }

    private LocalDate parseDate(String value) {
        return LocalDate.parse(value);
    }

    private LocalDate parseDatePrompt(String initialValue) {
        String value = initialValue;
        while (true) {
            try {
                return parseDate(value);
            } catch (DateTimeParseException exception) {
                System.out.println("Enter a valid date in yyyy-mm-dd format.");
                value = readLine("Pickup date yyyy-mm-dd (blank for no date filter): ");
                if (value.isBlank()) {
                    return null;
                }
            }
        }
    }

    private int readInteger(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readLine(prompt));
            } catch (NumberFormatException exception) {
                System.out.println("Enter a whole number.");
            }
        }
    }

    private BigDecimal readMoney(String prompt) {
        while (true) {
            try {
                return new BigDecimal(readLine(prompt));
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid decimal amount.");
            }
        }
    }

    private BigDecimal readOptionalMoney(String prompt) {
        String value = readLine(prompt);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            System.out.println("Invalid amount; this price filter will be ignored.");
            return null;
        }
    }

    private <E extends Enum<E>> E readEnum(String prompt, Class<E> type) {
        while (true) {
            try {
                return Enum.valueOf(type, readLine(prompt).toUpperCase());
            } catch (IllegalArgumentException exception) {
                System.out.println("Choose one of the listed values.");
            }
        }
    }

    private <E extends Enum<E>> E readOptionalEnum(String prompt, Class<E> type) {
        String value = readLine(prompt);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            System.out.println("Invalid value; this filter will be ignored.");
            return null;
        }
    }
}