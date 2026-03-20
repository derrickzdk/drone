package com.derrick.dronelocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.derrick.dronelocation.constants.DataSource;
import com.derrick.dronelocation.constants.DeletedStatus;
import com.derrick.dronelocation.constants.DefaultDirection;
import com.derrick.dronelocation.constants.DefaultValues;
import com.derrick.dronelocation.constants.TaskStatus;
import com.derrick.dronelocation.entity.Task;
import com.derrick.dronelocation.entity.Waypoint;
import com.derrick.dronelocation.mapper.TaskMapper;
import com.derrick.dronelocation.mapper.WaypointMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TaskExecutionService extends ServiceImpl<TaskMapper, Task> {

    private final WaypointMapper waypointMapper;
    private final KafkaProducerService kafkaProducerService;

    private final ConcurrentHashMap<Long, TaskExecutionState> executionStates = new ConcurrentHashMap<>();

    public TaskExecutionService(WaypointMapper waypointMapper, KafkaProducerService kafkaProducerService) {
        this.waypointMapper = waypointMapper;
        this.kafkaProducerService = kafkaProducerService;
    }

    public void startTaskExecution(Long taskId) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, taskId);
        queryWrapper.eq(Task::getDeleted, DeletedStatus.NOT_DELETED);
        Task task = this.getOne(queryWrapper);

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        if (!TaskStatus.SAVED.equals(task.getStatus()) && !TaskStatus.COMPLETED.equals(task.getStatus())) {
            throw new RuntimeException("任务状态不允许执行");
        }

        LambdaQueryWrapper<Waypoint> waypointQueryWrapper = new LambdaQueryWrapper<>();
        waypointQueryWrapper.eq(Waypoint::getTaskId, taskId);
        waypointQueryWrapper.eq(Waypoint::getDeleted, DeletedStatus.NOT_DELETED);
        waypointQueryWrapper.orderByAsc(Waypoint::getSequenceNum);
        List<Waypoint> waypoints = waypointMapper.selectList(waypointQueryWrapper);

        if (waypoints == null || waypoints.isEmpty()) {
            throw new RuntimeException("任务没有航点数据");
        }

        TaskExecutionState state = new TaskExecutionState(taskId, task, waypoints);
        executionStates.put(taskId, state);

        task.setStatus(TaskStatus.EXECUTING);
        this.updateById(task);
        log.info("开始执行任务，任务ID: {}, 航点数: {}", taskId, waypoints.size());
    }

    public void stopTaskExecution(Long taskId) {
        TaskExecutionState state = executionStates.remove(taskId);
        if (state != null) {
            LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Task::getId, taskId);
            Task task = this.getOne(queryWrapper);
            if (task != null) {
                task.setStatus(TaskStatus.COMPLETED);
                this.updateById(task);
            }
            log.info("停止执行任务，任务ID: {}, 已发送航点: {}/{}", 
                    taskId, state.getCurrentIndex(), state.getTotalWaypoints());
        }
    }

    public boolean isTaskExecuting(Long taskId) {
        return executionStates.containsKey(taskId);
    }

    public TaskExecutionState getExecutionState(Long taskId) {
        return executionStates.get(taskId);
    }

    @Scheduled(fixedRate = 1000)
    public void executeTasks() {
        executionStates.forEach((taskId, state) -> {
            try {
                if (state.isCompleted()) {
                    stopTaskExecution(taskId);
                    return;
                }

                Waypoint waypoint = state.getNextWaypoint();
                if (waypoint != null) {
                    kafkaProducerService.sendLocationDataWithNewFormat(
                            state.getTask().getDroneSn(),
                            waypoint.getLatitude().doubleValue(),
                            waypoint.getLongitude().doubleValue(),
                            waypoint.getAltitude() != null ? waypoint.getAltitude().doubleValue() : DefaultValues.DEFAULT_FLIGHT_HEIGHT.doubleValue(),
                            waypoint.getSpeed() != null ? waypoint.getSpeed().doubleValue() : DefaultValues.DEFAULT_FLIGHT_SPEED.doubleValue(),
                            DataSource.TASK_EXECUTION,
                            DefaultDirection.DEFAULT_DIRECTION
                    );
                    
                    log.debug("发送航点数据，任务ID: {}, 序号: {}, 经度: {}, 纬度: {}", 
                            taskId, waypoint.getSequenceNum(), 
                            waypoint.getLongitude(), waypoint.getLatitude());
                }
            } catch (Exception e) {
                log.error("执行任务失败，任务ID: {}", taskId, e);
                stopTaskExecution(taskId);
            }
        });
    }

    public static class TaskExecutionState {
        private final Long taskId;
        private final Task task;
        private final List<Waypoint> waypoints;
        private int currentIndex;

        public TaskExecutionState(Long taskId, Task task, List<Waypoint> waypoints) {
            this.taskId = taskId;
            this.task = task;
            this.waypoints = waypoints;
            this.currentIndex = 0;
        }

        public Waypoint getNextWaypoint() {
            if (currentIndex >= waypoints.size()) {
                return null;
            }
            return waypoints.get(currentIndex++);
        }

        public boolean isCompleted() {
            return currentIndex >= waypoints.size();
        }

        public Long getTaskId() {
            return taskId;
        }

        public Task getTask() {
            return task;
        }

        public int getCurrentIndex() {
            return currentIndex;
        }

        public int getTotalWaypoints() {
            return waypoints.size();
        }
    }
}
