package com.derrick.dronelocation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/single-point")
    public String singlePoint() {
        return "single-point";
    }

    @GetMapping("/route-generation")
    public String routeGeneration() {
        return "route-generation";
    }

    @GetMapping("/task-execution")
    public String taskExecution() {
        return "task-execution";
    }

    @GetMapping("/auto-route")
    public String autoRoute() {
        return "auto-route";
    }

    @GetMapping("/task-list")
    public String taskList() {
        return "task-list";
    }
}
