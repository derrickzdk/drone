$(document).ready(function() {
    var currentPage = 0;
    var pageSize = 10;
    var totalPages = 0;
    var isLoading = false;
    var cache = {};

    loadTasks();

    window.searchTasks = function() {
        if (isLoading) {
            return;
        }
        
        currentPage = 0;
        loadTasks();
    };

    function loadTasks() {
        isLoading = true;
        
        var taskName = $('#searchName').val();
        var startTime = $('#startTime').val();
        var endTime = $('#endTime').val();
        
        var cacheKey = taskName + '_' + startTime + '_' + endTime;
        
        if (cache[cacheKey]) {
            displayTasks(cache[cacheKey]);
            isLoading = false;
            return;
        }

        var params = {
            page: currentPage,
            size: pageSize,
            sortBy: 'createdTime',
            sortDir: 'desc'
        };

        if (taskName) {
            params.taskName = taskName;
        }

        if (startTime) {
            params.startTime = startTime;
        }

        if (endTime) {
            params.endTime = endTime;
        }

        $.ajax({
            url: '/api/task/search',
            method: 'GET',
            data: params,
            success: function(response) {
                if (response.success) {
                    cache[cacheKey] = response.data;
                    displayTasks(response.data);
                    totalPages = response.totalPages;
                    updatePagination(response.currentPage, response.totalPages);
                } else {
                    mapUtils.showAlert('加载失败: ' + response.message, 'danger');
                }
                isLoading = false;
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('加载失败: ' + error, 'danger');
                isLoading = false;
            }
        });
    }

    function displayTasks(tasks) {
        var tbody = $('#taskTableBody');
        tbody.empty();

        if (tasks.length === 0) {
            tbody.append('<tr><td colspan="7" class="text-center">暂无任务数据</td></tr>');
            return;
        }

        var fragment = document.createDocumentFragment();
        
        tasks.forEach(function(task) {
            var row = document.createElement('tr');
            row.innerHTML = '<td>' + task.id + '</td>' +
                           '<td>' + task.taskName + '</td>' +
                           '<td>' + task.droneSn + '</td>' +
                           '<td>' + task.totalWaypoints + '</td>' +
                           '<td>' + mapUtils.getStatusBadge(task.status) + '</td>' +
                           '<td>' + mapUtils.formatDate(task.createdTime) + '</td>' +
                           '<td>' +
                           '<button class="btn btn-sm btn-info me-2" onclick="viewTask(' + task.id + ')">' +
                           '<i class="fas fa-eye"></i> 查看' +
                           '</button>' +
                           '<button class="btn btn-sm btn-danger" onclick="deleteTask(' + task.id + ')">' +
                           '<i class="fas fa-trash"></i> 删除' +
                           '</button>' +
                           '</td>';
            fragment.appendChild(row);
        });
        
        tbody.append(fragment);
    }

    function updatePagination(current, total) {
        var pagination = $('#pagination');
        pagination.empty();

        if (total <= 1) {
            return;
        }

        var prevDisabled = current === 0 ? 'disabled' : '';
        var nextDisabled = current === total - 1 ? 'disabled' : '';

        var html = '<li class="page-item ' + prevDisabled + '">' +
                   '<a class="page-link" href="#" onclick="goToPage(' + (current - 1) + ')">' +
                   '<i class="fas fa-chevron-left"></i>' +
                   '</a></li>';

        for (var i = 0; i < total; i++) {
            var active = i === current ? 'active' : '';
            html += '<li class="page-item ' + active + '">' +
                     '<a class="page-link" href="#" onclick="goToPage(' + i + ')">' + (i + 1) + '</a></li>';
        }

        html += '<li class="page-item ' + nextDisabled + '">' +
                 '<a class="page-link" href="#" onclick="goToPage(' + (current + 1) + ')">' +
                 '<i class="fas fa-chevron-right"></i>' +
                 '</a></li>';

        pagination.html(html);
    }

    window.goToPage = function(page) {
        if (page < 0 || page >= totalPages) {
            return;
        }
        
        currentPage = page;
        loadTasks();
    };

    window.viewTask = function(taskId) {
        $.ajax({
            url: '/api/task/' + taskId,
            method: 'GET',
            success: function(response) {
                if (response.success) {
                    var task = response.data;
                    var taskInfo = '任务ID: ' + task.id + '\n' +
                                  '任务名称: ' + task.taskName + '\n' +
                                  '无人机SN: ' + task.droneSn + '\n' +
                                  '飞行高度: ' + task.flightHeight + 'm\n' +
                                  '飞行速度: ' + task.flightSpeed + 'm/s\n' +
                                  '航线类型: ' + task.routeType + '\n' +
                                  '航点数量: ' + task.totalWaypoints + '\n' +
                                  '状态: ' + task.status + '\n' +
                                  '创建时间: ' + mapUtils.formatDate(task.createdTime);
                    alert(taskInfo);
                } else {
                    mapUtils.showAlert('获取任务失败: ' + response.message, 'danger');
                }
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('获取任务失败: ' + error, 'danger');
            }
        });
    };

    window.deleteTask = function(taskId) {
        if (!confirm('确定要删除这个任务吗？')) {
            return;
        }

        $.ajax({
            url: '/api/task/' + taskId,
            method: 'DELETE',
            success: function(response) {
                if (response.success) {
                    mapUtils.showAlert('任务删除成功', 'success');
                    Object.keys(cache).forEach(function(key) {
                        delete cache[key];
                    });
                    loadTasks();
                } else {
                    mapUtils.showAlert('删除失败: ' + response.message, 'danger');
                }
            },
            error: function(xhr, status, error) {
                mapUtils.showAlert('删除失败: ' + error, 'danger');
            }
        });
    };
});
