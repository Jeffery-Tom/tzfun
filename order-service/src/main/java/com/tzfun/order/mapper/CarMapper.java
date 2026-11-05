package com.tzfun.order.mapper;

import com.tzfun.order.entity.Car;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /** 取一辆在库车辆。 */
    @Select("SELECT * FROM car WHERE store_id = #{storeId} AND car_type_id = #{carTypeId} " +
            "AND status = '在库' ORDER BY id LIMIT 1")
    Car selectAvailable(@Param("storeId") Long storeId, @Param("carTypeId") Long carTypeId);

    /** 条件更新：在库 -> 出租中，防重复分配。 */
    @Update("UPDATE car SET status = '出租中' WHERE id = #{id} AND status = '在库'")
    int markRented(Long id);

    /** 归还：出租中 -> 在库。 */
    @Update("UPDATE car SET status = '在库' WHERE id = #{id}")
    int markAvailable(Long id);
}
