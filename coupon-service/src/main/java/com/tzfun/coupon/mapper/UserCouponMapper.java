package com.tzfun.coupon.mapper;

import com.tzfun.coupon.entity.UserCoupon;
import com.tzfun.coupon.vo.UserCouponVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户券包 Mapper。
 */
public interface UserCouponMapper {

    @Insert("INSERT INTO user_coupon(user_id, template_id, status, expire_time, create_time) " +
            "VALUES(#{userId}, #{templateId}, #{status}, #{expireTime}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserCoupon userCoupon);

    @Delete("DELETE FROM user_coupon WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM user_coupon WHERE id = #{id}")
    UserCoupon selectById(Long id);

    /** 我的卡包（仅未用），JOIN 模板补券名/面额。 */
    @Select("SELECT uc.id, uc.template_id, uc.status, uc.expire_time, uc.create_time, " +
            "t.name AS template_name, t.type, t.value, t.min_spend, t.max_discount " +
            "FROM user_coupon uc JOIN coupon_template t ON uc.template_id = t.id " +
            "WHERE uc.user_id = #{userId} AND uc.status = 0 ORDER BY uc.id DESC")
    List<UserCouponVO> listMyCoupons(@Param("userId") Long userId);

    /** 用券：条件更新防复用，行数=0 表示不可用。 */
    @Update("UPDATE user_coupon SET status = 1, use_order_id = #{orderId} " +
            "WHERE id = #{id} AND status = 0")
    int useCoupon(@Param("id") Long id, @Param("orderId") Long orderId);

    /** 退券（取消/超时）。 */
    @Update("UPDATE user_coupon SET status = 0, use_order_id = NULL " +
            "WHERE id = #{id} AND status = 1")
    int returnCoupon(@Param("id") Long id);

    /** 过期：未用且已过 expire_time 的券置为过期。 */
    @Update("UPDATE user_coupon SET status = 2 WHERE expire_time < NOW() AND status = 0")
    int expireCoupons();
}
