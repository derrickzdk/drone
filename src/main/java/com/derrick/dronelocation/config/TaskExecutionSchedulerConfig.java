package com.derrick.dronelocation.config;

import com.derrick.dronelocation.service.TaskExecutionScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
@Slf4j
public class TaskExecutionSchedulerConfig implements ApplicationRunner {

    private final TaskExecutionScheduler taskExecutionScheduler;

    public TaskExecutionSchedulerConfig(TaskExecutionScheduler taskExecutionScheduler) {
        this.taskExecutionScheduler = taskExecutionScheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        taskExecutionScheduler.startScheduling();
        log.info("任务执行调度器配置已加载");
    }

    @PreDestroy
    public void cleanup() {
        taskExecutionScheduler.shutdown();
        log.info("任务执行调度器已关闭");
    }
}
