$(document).ready(function() {
    var map = mapUtils.createMap('map');

    // 多任务状态 Map：taskId -> { taskName, droneSn, totalWaypoints, routeData, webSocket, heartbeatInterval, reconnectAttempts, websocketUrl, markers, polyline, color }
    var executingTasks = {};
    var isProcessing = false;

    // 待预览状态（文件上传路径，未执行）
    var pendingRouteData = null;
    var pendingMarkers = null;
    var pendingPolyline = null;

    // 多任务颜色盘
    var taskColors = ['#0d6efd', '#dc3545', '#198754', '#fd7e14', '#6f42c1', '#20c997', '#d63384', '#0dcaf0'];
    var nextColorIndex = 0;

    loadTaskList();

    function loadTaskList() {
        $.ajax({
            url: '/api/task/list',
            method: 'GET',
            success: function(response) {
                if (response.success && response.data) {
                    var taskSelect = $('#taskSelect');
                    taskSelect.empty();
                    taskSelect.append('<option value="">请选择任务...</option>');
                    response.data.forEach(function(task) {
                        var executing = !!executingTasks[task.id];
                        var label = task.taskName + ' (SN: ' + task.droneSn + ', 航点数: ' + task.totalWaypoints + ')';
                        if (executing) label += ' [执行中]';
                        taskSelect.append(
                            '<option value="' + task.id + '"' + (executing ? ' disabled' : '') + '>' + label + '</option>'
                        );
                    });
                }
            },
            error: function(xhr, status, error) {
                console.error('加载任务列表失败:', error);
            }
        });
    }

    // ─── 加载任务航线并自动开始推送 ───────────────────────────────────────────

    window.loadTaskRoute = function() {
        var taskId = parseInt($('#taskSelect').val());
        if (!taskId) {
            mapUtils.showAlert('请先选择任务', 'warning');
            return;
        }
        if (executingTasks[taskId]) {
            mapUtils.showAlert('该任务已在执行中', 'warning');
            return;
        }
        if (isProcessing) return;
        isProcessing = true;

        $.ajax({
            url: '/api/task/' + taskId,
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    loadTaskWaypoints(taskId, response.data);
                } else {
                    mapUtils.showAlert('加载任务失败: ' + response.message, 'danger');
                    isProcessing = false;
                }
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('加载任务失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    };

    function loadTaskWaypoints(taskId, task) {
        $.ajax({
            url: '/api/task/' + taskId + '/waypoints',
            method: 'GET',
            success: function(response) {
                if (response.success && response.data) {
                    var waypoints = response.data.map(function(wp) {
                        return {
                            sequence: wp.sequenceNum,
                            lat: wp.latitude,
                            lng: wp.longitude,
                            altitude: wp.altitude || task.flightHeight,
                            speed: wp.speed || task.flightSpeed
                        };
                    });

                    var routeData = {
                        taskName: task.taskName,
                        droneSn: task.droneSn,
                        flightHeight: task.flightHeight,
                        flightSpeed: task.flightSpeed,
                        routeType: task.routeType,
                        waypoints: waypoints
                    };

                    // 分配颜色
                    var color = taskColors[nextColorIndex % taskColors.length];
                    nextColorIndex++;

                    // 绘制到地图
                    var markers = L.layerGroup().addTo(map);
                    var latlngs = [];
                    waypoints.forEach(function(wp) {
                        var marker = mapUtils.createWaypointMarker(wp.lat, wp.lng, map, '航点 ' + wp.sequence);
                        markers.addLayer(marker);
                        latlngs.push([wp.lat, wp.lng]);
                    });
                    var polyline = mapUtils.createPolyline(latlngs, map, color);
                    mapUtils.fitBounds(map, latlngs);

                    // 自动开始推送
                    startTaskExecution(taskId, task, routeData, markers, polyline, color);
                } else {
                    mapUtils.showAlert('加载航点失败', 'danger');
                    isProcessing = false;
                }
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('加载航点失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    }

    function startTaskExecution(taskId, taskInfo, routeData, markers, polyline, color) {
        $.ajax({
            url: '/api/task-execution/start/' + taskId,
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    executingTasks[taskId] = {
                        taskName: taskInfo.taskName,
                        droneSn: taskInfo.droneSn,
                        totalWaypoints: taskInfo.totalWaypoints || routeData.waypoints.length,
                        routeData: routeData,
                        webSocket: null,
                        heartbeatInterval: null,
                        reconnectAttempts: 0,
                        websocketUrl: null,
                        markers: markers,
                        polyline: polyline,
                        color: color
                    };

                    renderTaskCard(taskId, executingTasks[taskId]);
                    updateExecutingCount();
                    $('#stopAllBtn').prop('disabled', false);

                    if (response.data && response.data.websocketUrl) {
                        connectWebSocket(taskId, response.data.websocketUrl);
                    }

                    mapUtils.showAlert('任务 "' + taskInfo.taskName + '" 开始执行', 'success');
                    loadTaskList();
                } else {
                    // 启动失败，清除已绘制的地图图层
                    if (markers) markers.clearLayers();
                    if (polyline) map.removeLayer(polyline);
                    mapUtils.showAlert('启动失败: ' + response.message, 'danger');
                }
                isProcessing = false;
            },
            error: function(xhr, status, error) {
                if (markers) markers.clearLayers();
                if (polyline) map.removeLayer(polyline);
                mapUtils.showAlert('启动失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    }

    // ─── 任务卡片渲染 ──────────────────────────────────────────────────────────

    function renderTaskCard(taskId, taskState) {
        var color = taskState.color;
        var cardHtml =
            '<div class="card mb-2" id="task-card-' + taskId + '" style="border-left: 4px solid ' + color + ';">' +
                '<div class="card-body py-2 px-3">' +
                    '<div class="d-flex justify-content-between align-items-center">' +
                        '<div>' +
                            '<span class="fw-bold">' + taskState.taskName + '</span>' +
                            '<span class="badge ms-2 text-white" style="background-color:' + color + '">SN: ' + taskState.droneSn + '</span>' +
                            '<span class="badge bg-secondary ms-1">共 ' + taskState.totalWaypoints + ' 航点</span>' +
                        '</div>' +
                        '<button class="btn btn-sm btn-outline-danger" onclick="stopTaskById(' + taskId + ')">' +
                            '<i class="fas fa-stop"></i> 停止' +
                        '</button>' +
                    '</div>' +
                    '<div class="mt-2">' +
                        '<div class="d-flex gap-3 small text-muted mb-1">' +
                            '<span>当前航点：<strong id="current-idx-' + taskId + '">0</strong></span>' +
                            '<span>循环轮次：<strong id="loop-cnt-' + taskId + '">0</strong></span>' +
                        '</div>' +
                        '<div class="progress" style="height:16px;">' +
                            '<div class="progress-bar" id="prog-bar-' + taskId + '" role="progressbar" ' +
                                'style="width:0%;background-color:' + color + '">0%</div>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';

        $('#noTasksMsg').hide();
        $('#executingTasksList').append(cardHtml);
    }

    function updateExecutingCount() {
        var count = Object.keys(executingTasks).length;
        $('#executingCount').text(count);
        if (count === 0) {
            $('#noTasksMsg').show();
            $('#stopAllBtn').prop('disabled', true);
        }
    }

    // ─── WebSocket（每个任务独立） ──────────────────────────────────────────────

    function connectWebSocket(taskId, url) {
        var taskState = executingTasks[taskId];
        if (!taskState) return;

        taskState.websocketUrl = url;

        if (taskState.webSocket) {
            taskState.webSocket.close();
        }

        try {
            var ws = new WebSocket(url);
            taskState.webSocket = ws;

            ws.onopen = function() {
                console.log('WS connected, taskId:', taskId);
                taskState.reconnectAttempts = 0;
                startHeartbeat(taskId);
            };

            ws.onmessage = function(event) {
                try {
                    var message = JSON.parse(event.data);
                    handleWebSocketMessage(taskId, message);
                } catch (e) {
                    console.error('WS parse error, taskId:', taskId, e);
                }
            };

            ws.onclose = function(event) {
                console.log('WS closed, taskId:', taskId, 'code:', event.code);
                stopHeartbeat(taskId);
                if (executingTasks[taskId] && taskState.reconnectAttempts < 15) {
                    taskState.reconnectAttempts++;
                    var delay = Math.min(2000 * Math.pow(2, taskState.reconnectAttempts - 1), 15000);
                    console.log('Reconnect attempt', taskState.reconnectAttempts, 'for task', taskId, 'in', delay, 'ms');
                    setTimeout(function() {
                        if (executingTasks[taskId]) {
                            connectWebSocket(taskId, taskState.websocketUrl);
                        }
                    }, delay);
                } else if (executingTasks[taskId]) {
                    mapUtils.showAlert('任务 "' + taskState.taskName + '" 重连耗尽，等待服务端超时处理', 'warning');
                }
            };

            ws.onerror = function(error) {
                console.error('WS error, taskId:', taskId, error);
            };
        } catch (e) {
            console.error('Failed to create WS, taskId:', taskId, e);
        }
    }

    function handleWebSocketMessage(taskId, message) {
        switch (message.type) {
            case 'HEARTBEAT_ACK':
                break;
            case 'STATUS_UPDATE':
                if (message.data) updateTaskProgress(taskId, message.data);
                break;
            case 'TASK_STOPPED':
                mapUtils.showAlert('任务 "' + (executingTasks[taskId] ? executingTasks[taskId].taskName : taskId) + '" 已被服务端停止', 'info');
                cleanupTask(taskId);
                break;
            case 'ERROR':
                mapUtils.showAlert('任务错误: ' + message.message, 'danger');
                break;
            default:
                console.log('Unknown WS message type:', message.type);
        }
    }

    function updateTaskProgress(taskId, data) {
        $('#current-idx-' + taskId).text(data.currentIndex);
        $('#loop-cnt-' + taskId).text(data.loopCount);
        var progressText = data.progress.toFixed(1) + '%';
        if (data.loopCount > 0) progressText += ' (第' + data.loopCount + '轮)';
        $('#prog-bar-' + taskId).css('width', data.progress + '%').text(progressText);
    }

    // ─── 心跳（每个任务独立） ──────────────────────────────────────────────────

    function startHeartbeat(taskId) {
        var taskState = executingTasks[taskId];
        if (!taskState) return;
        stopHeartbeat(taskId);
        taskState.heartbeatInterval = setInterval(function() {
            if (taskState.webSocket && taskState.webSocket.readyState === WebSocket.OPEN) {
                taskState.webSocket.send(JSON.stringify({ type: 'HEARTBEAT', timestamp: Date.now() }));
            }
        }, 10000);
    }

    function stopHeartbeat(taskId) {
        var taskState = executingTasks[taskId];
        if (taskState && taskState.heartbeatInterval) {
            clearInterval(taskState.heartbeatInterval);
            taskState.heartbeatInterval = null;
        }
    }

    // ─── 停止任务 ──────────────────────────────────────────────────────────────

    window.stopTaskById = function(taskId) {
        var taskState = executingTasks[taskId];
        if (!taskState) return;

        var taskName = taskState.taskName;
        stopHeartbeat(taskId);

        $.ajax({
            url: '/api/task-execution/stop/' + taskId,
            method: 'POST',
            complete: function() {
                cleanupTask(taskId);
                mapUtils.showAlert('任务 "' + taskName + '" 已停止', 'info');
            }
        });
    };

    window.stopAllExecution = function() {
        var taskIds = Object.keys(executingTasks).map(Number);
        if (taskIds.length === 0) return;
        taskIds.forEach(function(taskId) {
            window.stopTaskById(taskId);
        });
    };

    function cleanupTask(taskId) {
        var taskState = executingTasks[taskId];
        if (!taskState) return;

        stopHeartbeat(taskId);

        if (taskState.webSocket) {
            taskState.webSocket.onclose = null; // 防止触发重连
            taskState.webSocket.close();
            taskState.webSocket = null;
        }

        if (taskState.markers) taskState.markers.clearLayers();
        if (taskState.polyline) map.removeLayer(taskState.polyline);

        delete executingTasks[taskId];

        $('#task-card-' + taskId).remove();
        updateExecutingCount();
        loadTaskList();
    }

    // ─── 文件上传路径 ──────────────────────────────────────────────────────────

    window.loadRouteFile = function() {
        if (isProcessing) return;

        var fileInput = document.getElementById('routeFile');
        var file = fileInput.files[0];

        if (!file) {
            mapUtils.showAlert('请选择航线文件', 'warning');
            return;
        }

        isProcessing = true;
        var reader = new FileReader();
        reader.onload = function(e) {
            try {
                var routeData = JSON.parse(e.target.result);
                displayFileRoute(routeData);
                mapUtils.showAlert('航线加载成功，请点击"保存为任务"后自动开始执行', 'success');
            } catch (error) {
                mapUtils.showAlert('文件格式错误: ' + error.message, 'danger');
            }
            isProcessing = false;
        };
        reader.readAsText(file);
    };

    function displayFileRoute(routeData) {
        if (!routeData.waypoints || routeData.waypoints.length === 0) {
            mapUtils.showAlert('航线数据为空', 'warning');
            return;
        }

        clearPendingRoute();

        var markers = L.layerGroup().addTo(map);
        var latlngs = [];
        routeData.waypoints.forEach(function(wp) {
            var marker = mapUtils.createWaypointMarker(wp.lat, wp.lng, map, '航点 ' + wp.sequence);
            markers.addLayer(marker);
            latlngs.push([wp.lat, wp.lng]);
        });
        var polyline = mapUtils.createPolyline(latlngs, map, '#6c757d');
        mapUtils.fitBounds(map, latlngs);

        pendingRouteData = routeData;
        pendingMarkers = markers;
        pendingPolyline = polyline;
    }

    window.clearRoute = function() {
        clearPendingRoute();
        $('#taskSelect').val('');
        $('#routeFile').val('');
    };

    function clearPendingRoute() {
        if (pendingMarkers) { pendingMarkers.clearLayers(); pendingMarkers = null; }
        if (pendingPolyline) { map.removeLayer(pendingPolyline); pendingPolyline = null; }
        pendingRouteData = null;
    }

    // ─── 保存为任务（文件上传后自动执行） ─────────────────────────────────────

    window.saveAsTask = function() {
        if (!pendingRouteData) {
            mapUtils.showAlert('请先通过文件上传加载航线', 'warning');
            return;
        }
        if (isProcessing) return;

        var taskName = prompt('请输入任务名称:', '航线任务_' + Date.now());
        if (!taskName) return;

        isProcessing = true;

        var taskData = {
            taskName: taskName,
            droneSn: pendingRouteData.droneSn,
            flightHeight: pendingRouteData.flightHeight,
            flightSpeed: pendingRouteData.flightSpeed,
            routeType: 'MANUAL',
            waypoints: pendingRouteData.waypoints
        };

        $.ajax({
            url: '/api/task/create',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(taskData),
            success: function(response) {
                if (response.success) {
                    var newTaskId = response.data.id;
                    mapUtils.showAlert('任务保存成功（ID: ' + newTaskId + '），正在启动执行...', 'success');
                    loadTaskList();

                    // 为已绘制的 pending 路线换上正式颜色
                    var color = taskColors[nextColorIndex % taskColors.length];
                    nextColorIndex++;
                    if (pendingPolyline) { map.removeLayer(pendingPolyline); }
                    var latlngs = pendingRouteData.waypoints.map(function(wp) { return [wp.lat, wp.lng]; });
                    var newPolyline = mapUtils.createPolyline(latlngs, map, color);

                    var savedMarkers = pendingMarkers;
                    var savedRouteData = pendingRouteData;
                    pendingMarkers = null;
                    pendingPolyline = null;
                    pendingRouteData = null;

                    var taskInfo = {
                        taskName: taskName,
                        droneSn: taskData.droneSn,
                        totalWaypoints: taskData.waypoints.length,
                        id: newTaskId
                    };
                    startTaskExecution(newTaskId, taskInfo, savedRouteData, savedMarkers, newPolyline, color);
                } else {
                    mapUtils.showAlert('保存失败: ' + response.message, 'danger');
                    isProcessing = false;
                }
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('保存失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    };
});
