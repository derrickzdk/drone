$(document).ready(function() {
    var map = mapUtils.createMap('map');
    var drawnItems = new L.FeatureGroup();
    map.addLayer(drawnItems);
    
    var drawControl = new L.Control.Draw({
        draw: {
            polygon: {
                allowIntersection: false,
                showArea: true
            },
            rectangle: true,
            circle: false,
            circlemarker: false,
            marker: false,
            polyline: false
        },
        edit: {
            featureGroup: drawnItems,
            remove: true
        }
    });
    map.addControl(drawControl);
    
    var markers = L.layerGroup().addTo(map);
    var polyline = null;
    var currentRouteData = null;
    var isProcessing = false;

    map.on(L.Draw.Event.CREATED, function(e) {
        if (isProcessing) {
            return;
        }
        
        var layer = e.layer;
        drawnItems.addLayer(layer);
    });

    map.on(L.Draw.Event.DELETED, function(e) {
        clearRoute();
    });

    window.generateRoute = function() {
        if (drawnItems.getLayers().length === 0) {
            mapUtils.showAlert('请先在地图上绘制区域', 'warning');
            return;
        }
        
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        
        var layer = drawnItems.getLayers()[0];
        var bounds = null;
        
        if (layer instanceof L.Rectangle) {
            var latlngs = layer.getLatLngs()[0];
            bounds = latlngs.map(function(ll) {
                return { lat: ll.lat, lng: ll.lng };
            });
        } else if (layer instanceof L.Polygon) {
            var latlngs = layer.getLatLngs()[0];
            bounds = latlngs.map(function(ll) {
                return { lat: ll.lat, lng: ll.lng };
            });
        } else {
            mapUtils.showAlert('不支持的图形类型', 'warning');
            isProcessing = false;
            return;
        }
        
        var request = {
            droneSn: $('#droneSn').val(),
            flightHeight: parseFloat($('#altitude').val()),
            flightSpeed: parseFloat($('#speed').val()),
            routeType: 'AUTO',
            waypointSpacing: parseFloat($('#spacing').val()),
            patternType: $('#patternType').val(),
            bounds: bounds
        };
        
        $.ajax({
            url: '/api/auto-route/generate',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(request),
            success: function(response) {
                if (response.success) {
                    displayRoute(response.data);
                    currentRouteData = response.data;
                    updateRouteInfo(response.data);
                    mapUtils.showAlert('航线生成成功', 'success');
                } else {
                    mapUtils.showAlert('生成失败: ' + response.message, 'danger');
                }
                isProcessing = false;
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('生成失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
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
    }

    function updateRouteInfo(routeData) {
        var waypointCount = routeData.waypoints.length;
        var totalDistance = mapUtils.calculateTotalDistance(routeData.waypoints);
        var speed = routeData.flightSpeed;
        var estimatedTime = totalDistance / speed / 60;
        
        $('#waypointCount').text(waypointCount);
        $('#estimatedDistance').text(totalDistance.toFixed(0));
        $('#estimatedTime').text(estimatedTime.toFixed(1));
        
        var patternType = $('#patternType').val();
        $('#patternTypeDisplay').text(patternType === 'GRID' ? '蛇形' : '螺旋形');
    }

    window.exportRoute = function() {
        if (!currentRouteData) {
            mapUtils.showAlert('请先生成航线', 'warning');
            return;
        }
        
        var json = JSON.stringify(currentRouteData, null, 2);
        var blob = new Blob([json], { type: 'application/json' });
        var url = URL.createObjectURL(blob);
        
        var a = document.createElement('a');
        a.href = url;
        a.download = 'auto_route_' + currentRouteData.taskName + '_' + Date.now() + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        mapUtils.showAlert('航线导出成功', 'success');
    };

    window.saveTask = function() {
        if (!currentRouteData) {
            mapUtils.showAlert('请先生成航线', 'warning');
            return;
        }
        
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        
        var taskData = {
            taskName: $('#taskName').val(),
            droneSn: $('#droneSn').val(),
            flightHeight: parseFloat($('#altitude').val()),
            flightSpeed: parseFloat($('#speed').val()),
            routeType: 'AUTO',
            waypoints: currentRouteData.waypoints
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
                isProcessing = false;
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('保存失败: ' + error, 'danger');
                isProcessing = false;
            }
        });
    };

    window.clearRoute = function() {
        markers.clearLayers();
        
        if (polyline) {
            map.removeLayer(polyline);
            polyline = null;
        }
        
        currentRouteData = null;
        
        $('#waypointCount').text('0');
        $('#estimatedDistance').text('0');
        $('#estimatedTime').text('0');
        $('#patternTypeDisplay').text('-');
    };
});
