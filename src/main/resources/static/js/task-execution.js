$(document).ready(function() {
    var map = mapUtils.createMap('map');
    var markers = L.layerGroup().addTo(map);
    var polyline = null;
    var currentRouteData = null;
    var currentTaskId = null;
    var executionInterval = null;
    var isProcessing = false;

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
        
        $('#totalWaypoints').text('0');
        $('#currentIndex').text('0');
        $('#progressBar').css('width', '0%').text('0%');
        
        $('#startBtn').prop('disabled', true);
        $('#stopBtn').prop('disabled', true);
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
            executeRoute();
        }
    };

    function executeRoute() {
        var waypoints = currentRouteData.waypoints;
        var currentIndex = 0;
        
        $('#startBtn').prop('disabled', true);
        $('#stopBtn').prop('disabled', false);
        
        executionInterval = setInterval(function() {
            if (currentIndex >= waypoints.length) {
                stopExecution();
                mapUtils.showAlert('任务执行完成', 'success');
                return;
            }
            
            var wp = waypoints[currentIndex];
            sendLocationData(wp);
            
            currentIndex++;
            updateProgress(currentIndex, waypoints.length);
        }, 1000);
    }

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
        executionInterval = setInterval(function() {
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
                        $('#progressBar').css('width', data.progress + '%').text(data.progress.toFixed(1) + '%');
                        
                        if (!data.isExecuting) {
                            stopExecution();
                            mapUtils.showAlert('任务执行完成', 'success');
                        }
                    }
                },
                error: function(xhr, status, error) {
                    console.error('获取执行状态失败:', error);
                }
            });
        }, 1000);
    }

    function sendLocationData(waypoint) {
        $.ajax({
            url: '/api/location/send-simple',
            method: 'POST',
            data: {
                sn: currentRouteData.droneSn,
                lat: waypoint.lat,
                lng: waypoint.lng,
                altitude: waypoint.altitude || currentRouteData.flightHeight,
                speed: waypoint.speed || currentRouteData.flightSpeed
            },
            success: function(response) {
                console.log('发送成功:', response);
            },
            error: function(xhr, status, error) {
                console.error('发送失败:', error);
            }
        });
    }

    function updateProgress(current, total) {
        var progress = (current / total) * 100;
        $('#currentIndex').text(current);
        $('#progressBar').css('width', progress + '%').text(progress.toFixed(1) + '%');
    }

    window.stopExecution = function() {
        if (executionInterval) {
            clearInterval(executionInterval);
            executionInterval = null;
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
