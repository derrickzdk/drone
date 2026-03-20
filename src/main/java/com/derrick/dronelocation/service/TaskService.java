package com.derrick.dronelocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.derrick.dronelocation.constants.DeletedStatus;
import com.derrick.dronelocation.constants.TaskStatus;
import com.derrick.dronelocation.dto.RouteData;
import com.derrick.dronelocation.dto.TaskDTO;
import com.derrick.dronelocation.entity.Task;
import com.derrick.dronelocation.mapper.TaskMapper;
import com.derrick.dronelocation.mapper.WaypointMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskService extends ServiceImpl<TaskMapper, Task> {

    private final WaypointMapper waypointMapper;
    private final RouteService routeService;

    public TaskService(WaypointMapper waypointMapper, RouteService routeService) {
        this.waypointMapper = waypointMapper;
        this.routeService = routeService;
    }

    @Transactional
    public Task createTask(TaskDTO taskDTO, RouteData routeData) {
        Task task = Task.builder()
                .taskName(taskDTO.getTaskName())
                .droneSn(taskDTO.getDroneSn())
                .flightHeight(taskDTO.getFlightHeight())
                .flightSpeed(taskDTO.getFlightSpeed())
                .routeType(routeData.getRouteType())
                .totalWaypoints(routeData.getWaypoints() != null ? routeData.getWaypoints().size() : 0)
                .routeData(routeService.serializeRouteData(routeData))
                .status(TaskStatus.SAVED)
                .deleted(DeletedStatus.NOT_DELETED)
                .build();

        this.save(task);
        log.info("创建任务成功，任务ID: {}", task.getId());

        if (routeData.getWaypoints() != null && !routeData.getWaypoints().isEmpty()) {
            routeService.saveWaypoints(task.getId(), routeData.getWaypoints());
        }

        return task;
    }

    @Transactional
    public Task updateTask(Long taskId, TaskDTO taskDTO) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        task.setTaskName(taskDTO.getTaskName());
        task.setDroneSn(taskDTO.getDroneSn());
        task.setFlightHeight(taskDTO.getFlightHeight());
        task.setFlightSpeed(taskDTO.getFlightSpeed());

        this.updateById(task);
        return task;
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        task.setDeleted(DeletedStatus.DELETED);
        this.updateById(task);
        log.info("删除任务成功，任务ID: {}", taskId);
    }

    public Task getTaskById(Long taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return task;
    }

    public TaskDTO getTaskDTOById(Long taskId) {
        Task task = getTaskById(taskId);
        return convertToDTO(task);
    }

    public List<TaskDTO> getAllTasks() {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getDeleted, DeletedStatus.NOT_DELETED);
        queryWrapper.orderByDesc(Task::getCreatedTime);
        return this.list(queryWrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public IPage<TaskDTO> searchTasks(String taskName, LocalDateTime startTime, LocalDateTime endTime, int page, int size, String sortBy, String sortDir) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getDeleted, DeletedStatus.NOT_DELETED);

        if (taskName != null && !taskName.isEmpty()) {
            queryWrapper.like(Task::getTaskName, taskName);
        }

        if (startTime != null) {
            queryWrapper.ge(Task::getCreatedTime, startTime);
        }

        if (endTime != null) {
            queryWrapper.le(Task::getCreatedTime, endTime);
        }

        if ("desc".equalsIgnoreCase(sortDir)) {
            queryWrapper.orderByDesc(Task::getCreatedTime);
        } else {
            queryWrapper.orderByAsc(Task::getCreatedTime);
        }

        Page<Task> pageParam = new Page<>(page, size);
        IPage<Task> taskPage = this.page(pageParam, queryWrapper);

        IPage<TaskDTO> dtoPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        dtoPage.setRecords(taskPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));

        return dtoPage;
    }

    @Transactional
    public void updateTaskStatus(Long taskId, String status) {
        Task task = getTaskById(taskId);
        task.setStatus(status);
        this.updateById(task);
        log.info("更新任务状态，任务ID: {}, 状态: {}", taskId, status);
    }

    public List<TaskDTO> getTasksByDroneSn(String droneSn) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getDroneSn, droneSn);
        queryWrapper.eq(Task::getDeleted, DeletedStatus.NOT_DELETED);
        queryWrapper.orderByDesc(Task::getCreatedTime);
        return this.list(queryWrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByStatus(String status) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getStatus, status);
        queryWrapper.eq(Task::getDeleted, DeletedStatus.NOT_DELETED);
        queryWrapper.orderByDesc(Task::getCreatedTime);
        return this.list(queryWrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private TaskDTO convertToDTO(Task task) {
        return TaskDTO.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .droneSn(task.getDroneSn())
                .flightHeight(task.getFlightHeight())
                .flightSpeed(task.getFlightSpeed())
                .routeType(task.getRouteType())
                .totalWaypoints(task.getTotalWaypoints())
                .status(task.getStatus())
                .createdTime(task.getCreatedTime())
                .updatedTime(task.getUpdatedTime())
                .build();
    }
}
