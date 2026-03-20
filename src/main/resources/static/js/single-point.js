$(document).ready(function() {
    var map = mapUtils.createMap('map');
    var markers = L.layerGroup().addTo(map);
    var messageCount = 0;
    var isSending = false;

    map.on('click', function(e) {
        if (isSending) {
            return;
        }
        
        var lat = e.latlng.lat;
        var lng = e.latlng.lng;
        
        var droneSn = $('#droneSn').val();
        var altitude = parseFloat($('#altitude').val());
        var speed = parseFloat($('#speed').val());
        
        var marker = mapUtils.createDroneMarker(lat, lng, map);
        markers.addLayer(marker);
        
        marker.bindPopup('<b>无人机位置</b><br>' +
                        'SN: ' + droneSn + '<br>' +
                        '纬度: ' + mapUtils.formatCoordinate(lat) + '<br>' +
                        '经度: ' + mapUtils.formatCoordinate(lng) + '<br>' +
                        '高度: ' + altitude + 'm<br>' +
                        '速度: ' + speed + 'm/s').openPopup();
        
        sendLocationData(droneSn, lat, lng, altitude, speed);
    });

    function sendLocationData(sn, lat, lng, altitude, speed) {
        if (isSending) {
            return;
        }
        
        isSending = true;
        
        $.ajax({
            url: '/api/location/send-simple',
            method: 'POST',
            data: {
                sn: sn,
                lat: lat,
                lng: lng,
                altitude: altitude,
                speed: speed
            },
            success: function(response) {
                messageCount++;
                addMessageLog(response.data, true);
                mapUtils.showAlert('位置数据发送成功', 'success');
            },
            error: function(xhr, status, error) {
                addMessageLog({ error: error }, false);
                mapUtils.showAlert('发送失败: ' + error, 'danger');
            },
            complete: function() {
                isSending = false;
            }
        });
    }

    function addMessageLog(data, success) {
        var timestamp = new Date().toLocaleString();
        var logHtml;
        
        if (success) {
            logHtml = '<div class="message-log-item message-log-success animate-fade-in">' +
                     '<div class="message-log-time">' + timestamp + '</div>' +
                     '<div class="message-log-content">' +
                     'SN: ' + data.sn + ', 纬度: ' + mapUtils.formatCoordinate(data.lat) + 
                     ', 经度: ' + mapUtils.formatCoordinate(data.lng) + 
                     ', 高度: ' + data.altitude + 'm, 速度: ' + data.speed + 'm/s' +
                     '</div></div>';
        } else {
            logHtml = '<div class="message-log-item message-log-error animate-fade-in">' +
                     '<div class="message-log-time">' + timestamp + '</div>' +
                     '<div class="message-log-content">发送失败: ' + data.error +
                     '</div></div>';
        }
        
        var logContainer = $('#messageLog');
        logContainer.prepend(logHtml);
        
        var maxLogs = 50;
        if (logContainer.children('.message-log-item').length > maxLogs) {
            logContainer.children('.message-log-item').eq(maxLogs).remove();
        }
    }

    $('#kafkaMessages').text(messageCount);
});