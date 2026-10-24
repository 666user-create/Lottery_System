package org.example.lottery_system.dao.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.lottery_system.controller.param.OperationLogQueryParam;
import org.example.lottery_system.dao.dataobject.OperationLogDO;

import java.util.List;

/*
* 操作日志 Mapper
*
* 写入只有 insert 一个口子，由 OperationLogInterceptor 异步调用；
* 查询为只读，供管理端操作日志页面使用。
* 分页沿用工程既有写法：offset/pageSize 单独传参（PageParam.offset() 不是 getter，不能用 #{param.offset}）。
* */
@Mapper
public interface OperationLogMapper {

    @Insert("insert into operation_log (user_id, identity, module, operation, http_method, path, " +
            " result_code, result_msg, http_status, cost_ms, ip) " +
            " values (#{userId}, #{identity}, #{module}, #{operation}, #{httpMethod}, #{path}, " +
            " #{resultCode}, #{resultMsg}, #{httpStatus}, #{costMs}, #{ip})")
    int insert(OperationLogDO operationLogDO);

    /*
     * 分页查询总数
     * 时间范围用字符串拼接比较，避免在 Java 侧解析日期
     * */
    @Select("<script>" +
            " select count(1) from operation_log l " +
            " <where>" +
            "   <if test='param.module != null and param.module != \"\"'> and l.module = #{param.module} </if>" +
            "   <if test='param.identity != null and param.identity != \"\"'> and l.identity = #{param.identity} </if>" +
            "   <if test='param.startTime != null and param.startTime != \"\"'> and l.gmt_create &gt;= concat(#{param.startTime}, ' 00:00:00') </if>" +
            "   <if test='param.endTime != null and param.endTime != \"\"'> and l.gmt_create &lt;= concat(#{param.endTime}, ' 23:59:59') </if>" +
            " </where>" +
            " </script>")
    int count(@Param("param") OperationLogQueryParam param);

    /*
     * 分页查询列表，连表 user 取操作人姓名
     * */
    @Select("<script>" +
            " select l.*, u.user_name as user_name " +
            " from operation_log l left join user u on u.id = l.user_id " +
            " <where>" +
            "   <if test='param.module != null and param.module != \"\"'> and l.module = #{param.module} </if>" +
            "   <if test='param.identity != null and param.identity != \"\"'> and l.identity = #{param.identity} </if>" +
            "   <if test='param.startTime != null and param.startTime != \"\"'> and l.gmt_create &gt;= concat(#{param.startTime}, ' 00:00:00') </if>" +
            "   <if test='param.endTime != null and param.endTime != \"\"'> and l.gmt_create &lt;= concat(#{param.endTime}, ' 23:59:59') </if>" +
            " </where>" +
            " order by l.id desc limit #{offset}, #{pageSize}" +
            " </script>")
    List<OperationLogDO> selectList(@Param("param") OperationLogQueryParam param,
                                    @Param("offset") Integer offset,
                                    @Param("pageSize") Integer pageSize);
}
