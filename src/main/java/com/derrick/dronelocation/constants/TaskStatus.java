package com.derrick.dronelocation.constants;

public class TaskStatus {

    public static final String SAVED = "SAVED";

    public static final String EXECUTING = "EXECUTING";

    public static final String COMPLETED = "COMPLETED";

    private TaskStatus() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
