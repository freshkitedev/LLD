package com.carrental;

import com.carrental.ui.ConsoleUI;

/** Console application entry point. */
public final class CarRentalApplication {
    private CarRentalApplication() { }

    public static void main(String[] args) {
        new ConsoleUI(new CarRentalSystem()).run();
    }
}