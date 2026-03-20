package com.derrick.dronelocation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.derrick.dronelocation.entity.Waypoint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WaypointMapper extends BaseMapper<Waypoint> {
}
