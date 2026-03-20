$(document).ready(function() {
    var map = mapUtils.createMap('map');
    var markers = L.layerGroup().addTo(map);
    var polyline = null;
    var waypoints = [];
    var isProcessing = false;

    map.on('click', function(e) {
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        
        var lat = e.latlng.lat;
        var lng = e.latlng.lng;
        
        var sequence = waypoints.length + 1;
        var altitude = parseFloat($('#altitude').val());
        var speed = parseFloat($('#speed').val());
        
        var waypoint = {
            sequence: sequence,
            lat: lat,
            lng: lng,
            altitude: altitude,
            speed: speed
        };
        
        waypoints.push(waypoint);
        
        var marker = mapUtils.createWaypointMarker(lat, lng, map, '航点 ' + sequence);
        markers.addLayer(marker);
        
        updatePolyline();
        updateWaypointList();
        
        setTimeout(function() {
            isProcessing = false;
        }, 100);
    });

    function updatePolyline() {
        if (polyline) {
            map.removeLayer(polyline);
        }
        
        if (waypoints.length > 1) {
            var latlngs = waypoints.map(function(wp) {
                return [wp.lat, wp.lng];
            });
            polyline = mapUtils.createPolyline(latlngs, map);
        }
    }

    function updateWaypointList() {
        var listHtml;
        
        if (waypoints.length === 0) {
            listHtml = '<div class="alert alert-info">暂无航点，请点击地图添加</div>';
        } else {
            listHtml = '<table class="table table-sm table-striped">' +
                      '<thead><tr><th>序号</th><th>纬度</th><th>经度</th><th>高度</th><th>速度</th></tr></thead>' +
                      '<tbody>';
            
            waypoints.forEach(function(wp) {
                listHtml += '<tr>' +
                           '<td>' + wp.sequence + '</td>' +
                           '<td>' + mapUtils.formatCoordinate(wp.lat) + '</td>' +
                           '<td>' + mapUtils.formatCoordinate(wp.lng) + '</td>' +
                           '<td>' + wp.altitude + '</td>' +
                           '<td>' + wp.speed + '</td>' +
                           '</tr>';
            });
            
            listHtml += '</tbody></table>';
        }
        
        $('#waypointList').html(listHtml);
    }

    window.exportRoute = function() {
        if (waypoints.length === 0) {
            mapUtils.showAlert('请先添加航点', 'warning');
            return;
        }
        
        var routeData = {
            taskName: $('#taskName').val(),
            droneSn: $('#droneSn').val(),
            flightHeight: parseFloat($('#altitude').val()),
            flightSpeed: parseFloat($('#speed').val()),
            routeType: 'MANUAL',
            waypoints: waypoints
        };
        
        var json = JSON.stringify(routeData, null, 2);
        var blob = new Blob([json], { type: 'application/json' });
        var url = URL.createObjectURL(blob);
        
        var a = document.createElement('a');
        a.href = url;
        a.download = 'route_' + routeData.taskName + '_' + Date.now() + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        mapUtils.showAlert('航线导出成功', 'success');
    };

    window.saveTask = function() {
        if (waypoints.length === 0) {
            mapUtils.showAlert('请先添加航点', 'warning');
            return;
        }
        
        var taskData = {
            taskName: $('#taskName').val(),
            droneSn: $('#droneSn').val(),
            flightHeight: parseFloat($('#altitude').val()),
            flightSpeed: parseFloat($('#speed').val()),
            routeType: 'MANUAL',
            waypoints: waypoints
        };
        
        $.ajax({
            url: '/api/task/create',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(taskData),
            success: function(response) {
                if (response.success) {
                    mapUtils.showAlert('任务保存成功，任务ID: ' + response.data.id, 'success');
                } else {
                    mapUtils.showAlert('保存失败: ' + response.message, 'danger');
                }
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('保存失败: ' + error, 'danger');
            }
        });
    };

    window.clearRoute = function() {
        if (waypoints.length === 0) {
            return;
        }
        
        if (!confirm('确定要清除所有航点吗？')) {
            return;
        }
        
        waypoints = [];
        markers.clearLayers();
        
        if (polyline) {
            map.removeLayer(polyline);
            polyline = null;
        }
        
        updateWaypointList();
        mapUtils.showAlert('航线已清除', 'info');
    };

    window.undoLastPoint = function() {
        if (waypoints.length === 0) {
            mapUtils.showAlert('没有可撤销的航点', 'warning');
            return;
        }
        
        waypoints.pop();
        markers.clearLayers();
        
        waypoints.forEach(function(wp) {
            var marker = mapUtils.createWaypointMarker(wp.lat, wp.lng, map, '航点 ' + wp.sequence);
            markers.addLayer(marker);
        });
        
        updatePolyline();
        updateWaypointList();
        mapUtils.showAlert('已撤销最后一个航点', 'info');
    };
});
