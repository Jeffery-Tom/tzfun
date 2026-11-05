package com.tzfun.car.mapper;

import com.tzfun.car.entity.Store;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 门店表 Mapper。
 */
public interface StoreMapper {

    @Select("SELECT * FROM store ORDER BY id")
    List<Store> selectAll();

    @Select("SELECT * FROM store WHERE id = #{id}")
    Store selectById(Long id);

    @Select("SELECT * FROM store WHERE city = #{city} ORDER BY id")
    List<Store> selectByCity(String city);

    @Insert("INSERT INTO store(name, city, address, lng, lat, create_time) " +
            "VALUES(#{name}, #{city}, #{address}, #{lng}, #{lat}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Store store);

    @Update("UPDATE store SET name=#{name}, city=#{city}, address=#{address}, " +
            "lng=#{lng}, lat=#{lat} WHERE id=#{id}")
    int updateById(Store store);
}
