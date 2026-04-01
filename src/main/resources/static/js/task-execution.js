$(document).ready(function() {
    var map = mapUtils.createMap('map');
    var markers = L.layerGroup().addTo(map);
    var polyline = null;
    var currentRouteData = null;
    var currentTaskId = null;
    var executionWorker = null;
    var isProcessing = false;

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
                        taskSelect.append('<option value="' + task.id + '">' + task.taskName + ' (ID: ' + task.id + ', 航点数: ' + task.totalWaypoints + ')</option>');
                    });
                }
            },
            error: function(xhr, status, error) {
                console.error('加载任务列表失败:', error);
            }
        });
    }

    window.loadTaskRoute = function() {
        var taskId = $('#taskSelect').val();
        if (!taskId) {
            mapUtils.showAlert('请先选择任务', 'warning');
            return;
        }
        
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        
        $.ajax({
            url: '/api/task/' + taskId,
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    var task = response.data;
                    loadTaskWaypoints(taskId, task);
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
                    
                    displayRoute(routeData);
                    currentRouteData = routeData;
                    currentTaskId = task.id;
                    mapUtils.showAlert('任务航线加载成功', 'success');
                } else {
                    mapUtils.showAlert('加载航点失败', 'danger');
                }
                isProcessing = false;
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('加载航点失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    }

    window.loadRouteFile = function() {
        if (isProcessing) {
            return;
        }
        
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
                displayRoute(routeData);
                currentRouteData = routeData;
                mapUtils.showAlert('航线加载成功', 'success');
            } catch (error) {
                mapUtils.showAlert('文件格式错误: ' + error.message, 'danger');
            }
            isProcessing = false;
        };
        reader.readAsText(file);
    };

    function displayRoute(routeData) {
        clearRoute();
        
        if (!routeData.waypoints || routeData.waypoints.length === 0) {
            mapUtils.showAlert('航线数据为空', 'warning');
            return;
        }
        
        var latlngs = [];
        
        routeData.waypoints.forEach(function(wp) {
            var marker = mapUtils.createWaypointMarker(wp.lat, wp.lng, map, '航点 ' + wp.sequence);
            markers.addLayer(marker);
            latlngs.push([wp.lat, wp.lng]);
        });
        
        polyline = mapUtils.createPolyline(latlngs, map);
        mapUtils.fitBounds(map, latlngs);
        
        $('#totalWaypoints').text(routeData.waypoints.length);
        $('#currentIndex').text('0');
        $('#progressBar').css('width', '0%').text('0%');
        
        $('#startBtn').prop('disabled', false);
    }

    window.clearRoute = function() {
        markers.clearLayers();
        
        if (polyline) {
            map.removeLayer(polyline);
            polyline = null;
        }
        
        currentRouteData = null;
        currentTaskId = null;
        
        $('#totalWaypoints').text('0');
        $('#currentIndex').text('0');
        $('#progressBar').css('width', '0%').text('0%');
        
        $('#startBtn').prop('disabled', true);
        $('#stopBtn').prop('disabled', true);
        
        $('#taskSelect').val('');
        $('#routeFile').val('');
    }

    window.startExecution = function() {
        if (!currentRouteData) {
            mapUtils.showAlert('请先加载航线', 'warning');
            return;
        }
        
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        
        if (currentTaskId) {
            startTaskExecution(currentTaskId);
        } else {
            mapUtils.showAlert('请先从任务列表选择任务或保存航线为任务', 'warning');
            isProcessing = false;
        }
    };

    function startTaskExecution(taskId) {
        $.ajax({
            url: '/api/task-execution/start/' + taskId,
            method: 'POST',
            success: function(response) {
                if (response.success) {
                    $('#startBtn').prop('disabled', true);
                    $('#stopBtn').prop('disabled', false);
                    startMonitoring();
                    mapUtils.showAlert('任务开始执行', 'success');
                } else {
                    mapUtils.showAlert('启动失败: ' + response.message, 'danger');
                }
                isProcessing = false;
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('启动失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    }

    function startMonitoring() {
        if (executionWorker) {
            return;
        }
        
        executionWorker = new Worker('/static/js/task-execution-worker.js');
        var lastTickTime = Date.now();
        var expectedTickCount = 0;
        var heartbeatInterval = null;
        var isPageVisible = !document.hidden;
        
        document.addEventListener('visibilitychange', function() {
            isPageVisible = !document.hidden;
            if (isPageVisible && executionWorker) {
                console.log('页面重新可见，检查 Worker 状态');
                checkWorkerHealth();
            }
        });
        
        function checkWorkerHealth() {
            if (!executionWorker) {
                return;
            }
            
            executionWorker.postMessage({ type: 'PING' });
            
            setTimeout(function() {
                var timeSinceLastTick = Date.now() - lastTickTime;
                if (timeSinceLastTick > 5000) {
                    console.warn('Worker 可能被节流，重新启动');
                    restartWorker();
                }
            }, 1000);
        }
        
        function restartWorker() {
            if (executionWorker) {
                executionWorker.postMessage({ type: 'STOP' });
                executionWorker.terminate();
            }
            
            executionWorker = new Worker('/static/js/task-execution-worker.js');
            executionWorker.onmessage = handleWorkerMessage;
            executionWorker.postMessage({ type: 'START' });
            lastTickTime = Date.now();
            console.log('Worker 已重新启动');
        }
        
        function handleWorkerMessage(e) {
            const { type, timestamp, tickCount } = e.data;
            
            if (type === 'TICK') {
                lastTickTime = timestamp;
                expectedTickCount = tickCount;
                
                if (!currentTaskId) {
                    stopExecution();
                    return;
                }
                
                $.ajax({
                    url: '/api/task-execution/status/' + currentTaskId,
                    method: 'GET',
                    success: function(response) {
                        if (response.success) {
                            var data = response.data;
                            $('#currentIndex').text(data.currentIndex);
                            $('#loopCount').text(data.loopCount);
                            var progressText = data.progress.toFixed(1) + '%';
                            if (data.loopCount > 0) {
                                progressText += ' (第' + data.loopCount + '轮)';
                            }
                            $('#progressBar').css('width', data.progress + '%').text(progressText);
                        }
                    },
                    error: function(xhr, status, error) {
                        console.error('获取执行状态失败:', error);
                    }
                });
            } else if (type === 'PONG') {
                console.log('Worker 心跳正常，tickCount:', tickCount);
            }
        }
        
        executionWorker.onmessage = handleWorkerMessage;
        executionWorker.postMessage({ type: 'START' });
        
        heartbeatInterval = setInterval(function() {
            if (!isPageVisible) {
                checkWorkerHealth();
            }
        }, 3000);
        
        executionWorker.heartbeatInterval = heartbeatInterval;
    }

    function updateProgress(current, total, loopCount) {
        var progress = (current / total) * 100;
        $('#currentIndex').text(current);
        $('#loopCount').text(loopCount);
        var progressText = progress.toFixed(1) + '%';
        if (loopCount > 0) {
            progressText += ' (第' + loopCount + '轮)';
        }
        $('#progressBar').css('width', progress + '%').text(progressText);
    }

    window.stopExecution = function() {
        if (executionWorker) {
            executionWorker.postMessage({ type: 'STOP' });
            if (executionWorker.heartbeatInterval) {
                clearInterval(executionWorker.heartbeatInterval);
            }
            executionWorker.terminate();
            executionWorker = null;
        }
        
        if (currentTaskId) {
            $.ajax({
                url: '/api/task-execution/stop/' + currentTaskId,
                method: 'POST',
                success: function(response) {
                    if (response.success) {
                        mapUtils.showAlert('任务已停止', 'info');
                    }
                },
                error: function(xhr, status, error) {
                    console.error('停止失败:', error);
                }
            });
        }
        
        $('#startBtn').prop('disabled', false);
        $('#stopBtn').prop('disabled', true);
        isProcessing = false;
    };

    window.saveAsTask = function() {
        if (!currentRouteData) {
            mapUtils.showAlert('请先加载航线', 'warning');
            return;
        }
        
        if (isProcessing) {
            return;
        }
        
        var taskName = prompt('请输入任务名称:', '航线任务_' + Date.now());
        if (!taskName) {
            return;
        }
        
        isProcessing = true;
        
        var taskData = {
            taskName: taskName,
            droneSn: currentRouteData.droneSn,
            flightHeight: currentRouteData.flightHeight,
            flightSpeed: currentRouteData.flightSpeed,
            routeType: 'MANUAL',
            waypoints: currentRouteData.waypoints
        };
        
        $.ajax({
            url: '/api/task/create',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(taskData),
            success: function(response) {
                if (response.success) {
                    currentTaskId = response.data.id;
                    mapUtils.showAlert('任务保存成功，任务ID: ' + currentTaskId, 'success');
                    loadTaskList();
                } else {
                    mapUtils.showAlert('保存失败: ' + response.message, 'danger');
                }
                isProcessing = false;
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('保存失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    };
});
