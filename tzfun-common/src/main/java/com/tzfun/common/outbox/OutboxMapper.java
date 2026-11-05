package com.tzfun.common.outbox;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 本地消息表 Mapper：insert / selectUnsent / markSent（条件更新保证幂等）。
 */
@Mapper
public interface OutboxMapper {

    @Insert("INSERT INTO outbox_event(aggregate_type, aggregate_id, event_type, payload, status, create_time) " +
            "VALUES(#{aggregateType}, #{aggregateId}, #{eventType}, #{payload}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OutboxEvent event);

    @Select("SELECT * FROM outbox_event WHERE status = 0 ORDER BY id LIMIT 100")
    List<OutboxEvent> selectUnsent();

    @Update("UPDATE outbox_event SET status = 1, sent_time = NOW() WHERE id = #{id} AND status = 0")
    int markSent(Long id);
}
