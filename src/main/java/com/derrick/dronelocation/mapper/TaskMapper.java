package com.derrick.dronelocation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.derrick.dronelocation.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
