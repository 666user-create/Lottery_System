package org.example.lottery_system.dao.mapper;

import jakarta.validation.constraints.NotBlank;
import org.apache.ibatis.annotations.*;
import org.example.lottery_system.dao.dataobject.Encrypt;
import org.example.lottery_system.dao.dataobject.UserDO;

import java.util.List;

@Mapper
public interface UserMapper {
    /*
     * 根据邮箱查询用户是否存在
     * */
    @Select("select count(*) from user where email = #{email}")
    int countByMail(@NotBlank(message = "邮箱不能为空") @Param("email") String email);

    /*
     * 根据手机号查询用户是否存在
     **/
    @Select("select count(*) from user where phone_number = #{phoneNumber}")
    int countByPhoneNumber(@NotBlank(message = "手机号不能为空") @Param("phoneNumber") Encrypt phoneNumber);

    /*
     * 插入用户
     */
    @Insert("insert into user(user_name,email,phone_number,password,identity)"
            + " values(#{userName},#{email},#{phoneNumber},#{password},#{identity})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(UserDO userDO);

    @Select("select * from user where phone_number = #{phoneNumber}")
    UserDO selectByPhoneNumber(@Param("phoneNumber") Encrypt phoneNumber);

    @Select("select * from user where email = #{email}")
    UserDO selectByEmail(@Param("email") String email);

    @Select("<script>select * from user <if test=\"identity != null\"> where identity = #{identity} </if> order by id desc</script>")
    List<UserDO> selectUserListByIdentity(@Param("identity") String identity);

    @Select("<script>select id from user where id in <foreach collection='items' item='item' open='(' close=')'  separator=','>#{item}</foreach></script>")
    List<Long> selectExistByUserIds(@Param("items") List<Long> ids);

    @Select("<script>select * from user where id in <foreach collection='items' item='item' open='(' close=')'  separator=','>#{item}</foreach></script>")
    List<UserDO> batchSelectByIds(@Param("items") List<Long> ids);
}
