package com.derrick.dronelocation.constants;

public class DataSource {

    public static final Integer MANUAL_INPUT = 1;

    public static final Integer TASK_EXECUTION = 8;

    public static final Integer AUTO_ROUTE = 9;

    private DataSource() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
