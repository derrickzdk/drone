package com.derrick.dronelocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.derrick.dronelocation.constants.DeletedStatus;
import com.derrick.dronelocation.constants.TaskStatus;
import com.derrick.dronelocation.entity.Task;
import com.derrick.dronelocation.entity.Waypoint;
import com.derrick.dronelocation.mapper.TaskMapper;
import com.derrick.dronelocation.mapper.WaypointMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TaskExecutionService extends ServiceImpl<TaskMapper, Task> {

    private final WaypointMapper waypointMapper;

    private final ConcurrentHashMap<Long, TaskExecutionState> executionStates = new ConcurrentHashMap<>();

    public TaskExecutionService(WaypointMapper waypointMapper) {
        this.waypointMapper = waypointMapper;
    }

    public void startTaskExecution(Long taskId) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, taskId);
        queryWrapper.eq(Task::getDeleted, DeletedStatus.NOT_DELETED);
        Task task = this.getOne(queryWrapper);

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        if (TaskStatus.EXECUTING.equals(task.getStatus())) {
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

    public java.util.Map<Long, TaskExecutionState> getExecutionStates() {
        return new java.util.concurrent.ConcurrentHashMap<>(executionStates);
    }

    public static class TaskExecutionState {
        private final Long taskId;
        private final Task task;
        private final List<Waypoint> waypoints;
        private int currentIndex;
        private int loopCount;

        public TaskExecutionState(Long taskId, Task task, List<Waypoint> waypoints) {
            this.taskId = taskId;
            this.task = task;
            this.waypoints = waypoints;
            this.currentIndex = 0;
            this.loopCount = 0;
        }

        public Waypoint getNextWaypoint() {
            if (waypoints.isEmpty()) {
                return null;
            }
            
            if (currentIndex >= waypoints.size()) {
                currentIndex = 0;
                loopCount++;
                log.info("任务循环执行，任务ID: {}, 开始第 {} 轮循环", taskId, loopCount);
            }
            
            return waypoints.get(currentIndex++);
        }

        public boolean isCompleted() {
            return waypoints.isEmpty();
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

        public int getLoopCount() {
            return loopCount;
        }
    }
}
