package com.tzfun.tzfun.mapper;

import com.tzfun.tzfun.entity.CarType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 车型表 Mapper。
 */
public interface CarTypeMapper {

    @Select("SELECT * FROM car_type ORDER BY id")
    List<CarType> selectAll();

    @Insert("INSERT INTO car_type(name, brand, seats, transmission, daily_price, image, create_time) " +
            "VALUES(#{name}, #{brand}, #{seats}, #{transmission}, #{dailyPrice}, #{image}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CarType carType);

    @Update("UPDATE car_type SET name=#{name}, brand=#{brand}, seats=#{seats}, " +
            "transmission=#{transmission}, daily_price=#{dailyPrice}, image=#{image} WHERE id=#{id}")
    int updateById(CarType carType);

    @Select("SELECT COUNT(*) FROM car WHERE store_id = #{storeId} AND car_type_id = #{carTypeId} AND status = '在库'")
    int countAvailable(@Param("storeId") Long storeId, @Param("carTypeId") Long carTypeId);
}
