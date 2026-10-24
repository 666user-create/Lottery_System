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

    @Select("select * from user where id = #{id}")
    UserDO selectById(@Param("id") Long id);

    /*
     * 按主键升序锁定全部管理员行，供 changeUserStatus 在事务内防并发互停。
     *
     * 为什么锁「全部管理员」而不是锁目标行：
     * 若只锁目标行，A 停 B 锁 B、B 停 A 锁 A，各锁各的并不互相阻塞，
     * 两边仍会同时通过 count 校验，把两个管理员一起停用 —— 全系统再无人能启用。
     * 两次互停互相等对方目标行还会直接死锁。
     * 锁同一批行 + 固定顺序（主键升序）才能真正串行化，且不会死锁。
     *
     * for update 是「当前读」，读到的是最新已提交版本而非事务快照，
     * 因此后到的请求会看到前一个事务刚写入的 status，从而被 131 正确拦下。
     * 必须配合 @Transactional，否则方法返回即释放锁，形同虚设。
     *
     * 这里刻意不另设 countActiveAdmin()：
     * 「还剩几个可用管理员」必须基于**加锁后读到的这份列表**在内存里数。
     * 若另发一条 select count(*) 会读到事务快照（可能是加锁前的旧值），
     * 于是锁住了却仍按旧数据判断，防并发完全失效。
     *
     * 筛选条件必须与 UserStatusEnum.isActive / LoginInterceptor 的判法一致，否则会锁死：
     *   1. identity 用 upper() —— 全项目其它地方都是 equalsIgnoreCase，
     *      这里若只靠 MySQL 默认排序规则的大小写不敏感，列排序规则一改就漏行；
     *   2. status 用 coalesce(...,'ACTIVE') —— isActive() 刻意把空值视为 ACTIVE
     *      （兼容手工插入的历史行），若按 status='ACTIVE' 过滤，NULL 行不被锁也不计数，
     *      「还能登录的管理员」被算少，判定放行后可能把所有人停用，无人能再启用。
     *   故此处只做「锁行」不做状态过滤，状态由调用方按 isActive() 在内存判定。
     */
    @Select("select id, status from user where upper(identity) = 'ADMIN' order by id for update")
    List<UserDO> selectAdminRowsForUpdate();

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

    /*
     * 修改邮箱
     */
    @Update("update user set email = #{email} where id = #{id}")
    void updateEmail(@Param("id") Long id, @Param("email") String email);

    /*
     * 修改密码
     */
    @Update("update user set password = #{password} where id = #{id}")
    void updatePassword(@Param("id") Long id, @Param("password") String password);

    /*
     * 启用/停用账号。
     * 采用软停用而非删行：user.id 被 activity_user / winning_record / operation_log /
     * human_handover 引用，且全库外键数为 0，删行必然留下孤儿数据、弄断审计链路。
     */
    @Update("update user set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /*
     * 保存收货地址(领奖履约用)。
     * receiverPhone 是 Encrypt 类型,由 EncryptTypeHandler 加密后落库,与 phone_number 一致。
     */
    @Update("update user set receiver_name = #{receiverName}, receiver_phone = #{receiverPhone}, " +
            "receiver_address = #{receiverAddress} where id = #{id}")
    int updateReceiverInfo(UserDO userDO);
}
