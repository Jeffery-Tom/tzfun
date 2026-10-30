package com.tzfun.tzfun.mapper;

import com.tzfun.tzfun.entity.Car;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车辆表 Mapper。
 */
public interface CarMapper {

    @Select("<script>" +
            "SELECT * FROM car" +
            "<where>" +
            "  <if test='storeId != null'>AND store_id = #{storeId}</if>" +
            "  <if test='carTypeId != null'>AND car_type_id = #{carTypeId}</if>" +
            "</where>" +
            " ORDER BY id" +
            "</script>")
    List<Car> selectList(@Param("storeId") Long storeId, @Param("carTypeId") Long carTypeId);
}
