var mapUtils = {
    createMap: function(elementId, center, zoom) {
        var defaultCenter = center || [39.9042, 116.4074];
        var defaultZoom = zoom || 10;
        
        var map = L.map(elementId).setView(defaultCenter, defaultZoom);
        
        L.tileLayer('https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
            attribution: '&copy; 高德地图',
            maxZoom: 19
        }).addTo(map);
        
        return map;
    },

    createDroneMarker: function(lat, lng, map) {
        var droneIcon = L.divIcon({
            className: 'drone-marker',
            iconSize: [24, 24],
            iconAnchor: [12, 12]
        });
        
        return L.marker([lat, lng], { icon: droneIcon }).addTo(map);
    },

    createWaypointMarker: function(lat, lng, map, label) {
        var waypointIcon = L.divIcon({
            className: 'waypoint-marker',
            iconSize: [18, 18],
            iconAnchor: [9, 9]
        });
        
        var marker = L.marker([lat, lng], { icon: waypointIcon }).addTo(map);
        
        if (label) {
            marker.bindPopup(label);
        }
        
        return marker;
    },

    createPolyline: function(latlngs, map, color) {
        var options = {
            color: color || '#06b6d4',
            weight: 3,
            opacity: 0.8,
            dashArray: '10, 5'
        };
        
        return L.polyline(latlngs, options).addTo(map);
    },

    createPolygon: function(latlngs, map, color) {
        var options = {
            color: color || '#06b6d4',
            weight: 2,
            opacity: 0.3,
            fillOpacity: 0.1
        };
        
        return L.polygon(latlngs, options).addTo(map);
    },

    fitBounds: function(map, latlngs) {
        if (latlngs && latlngs.length > 0) {
            var bounds = L.latLngBounds(latlngs);
            map.fitBounds(bounds, { padding: [50, 50] });
        }
    },

    calculateDistance: function(lat1, lng1, lat2, lng2) {
        var R = 6371000;
        var dLat = (lat2 - lat1) * Math.PI / 180;
        var dLng = (lng2 - lng1) * Math.PI / 180;
        var a = Math.sin(dLat / 2) * Math.sin(dLng / 2);
        var c = Math.cos(dLat / 2) * Math.cos(dLng / 2);
        var d = Math.sqrt((1 - c) * (1 - c));
        return R * d;
    },

    calculateTotalDistance: function(waypoints) {
        if (!waypoints || waypoints.length < 2) {
            return 0;
        }
        
        var totalDistance = 0;
        for (var i = 0; i < waypoints.length - 1; i++) {
            totalDistance += this.calculateDistance(
                waypoints[i].lat,
                waypoints[i].lng,
                waypoints[i + 1].lat,
                waypoints[i + 1].lng
            );
        }
        
        return totalDistance;
    },

    formatCoordinate: function(value, decimals) {
        decimals = decimals || 8;
        return parseFloat(value).toFixed(decimals);
    },

    formatDate: function(date) {
        if (!date) return '-';
        var d = new Date(date);
        var year = d.getFullYear();
        var month = String(d.getMonth() + 1).padStart(2, '0');
        var day = String(d.getDate()).padStart(2, '0');
        var hours = String(d.getHours()).padStart(2, '0');
        var minutes = String(d.getMinutes()).padStart(2, '0');
        var seconds = String(d.getSeconds()).padStart(2, '0');
        return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes + ':' + seconds;
    },

    getStatusBadge: function(status) {
        var badges = {
            'SAVED': '<span class="badge badge-success">已保存</span>',
            'EXECUTING': '<span class="badge badge-info">执行中</span>',
            'COMPLETED': '<span class="badge badge-warning">已完成</span>'
        };
        return badges[status] || '<span class="badge badge-secondary">未知</span>';
    },

    showAlert: function(message, type) {
        type = type || 'info';
        var alertClass = 'alert alert-' + type;
        var alertHtml = '<div class="' + alertClass + ' alert-dismissible" role="alert">' +
                       '<strong>[' + new Date().toLocaleTimeString() + ']</strong> ' + message +
                       '<button type="button" class="btn-close" data-bs-dismiss="alert">&times;</button>' +
                       '</div>';
        
        var alertContainer = $('#alertContainer');
        if (alertContainer.length === 0) {
            alertContainer = $('<div id="alertContainer"></div>').prependTo('body');
        }
        
        alertContainer.prepend(alertHtml);
        
        setTimeout(function() {
            alertContainer.find('.alert').first().fadeOut(300, function() {
                $(this).remove();
            });
        }, 5000);
    }
};