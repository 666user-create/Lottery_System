package org.example.lottery_system;

import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.dao.dataobject.UserDO;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.example.lottery_system.service.UserService;
import org.example.lottery_system.service.enums.UserStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 账号停用相关规则的回归测试。
 *
 * 覆盖「不能停用最后一个管理员」(131) 的前置：单线程下 131 不可自然触发
 * （唯一管理员停自己先被 130 拦、停用已停用账号走幂等），
 * 真正要防的是两个管理员并发互停，由 @Transactional + 行锁在运行时保证。
 * 这里钉住规则周边与状态判定口径，防止后续改动破坏它们。
 */
@SpringBootTest
class LastAdminGuardTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    private Long activeAdminId() {
        return userMapper.selectUserListByIdentity("ADMIN").stream()
                .filter(u -> UserStatusEnum.isActive(u.getStatus()))
                .map(UserDO::getId).findFirst().orElse(null);
    }

    @Test
    void isActive_空状态视为可用_与coalesce口径一致() {
        // DB 侧 coalesce(status,'ACTIVE') 把 NULL 当 ACTIVE；
        // 应用侧 isActive 也必须把空/null 当 ACTIVE，否则两边判定会打架。
        assertTrue(UserStatusEnum.isActive(null), "null 应视为可用");
        assertTrue(UserStatusEnum.isActive(""), "空串应视为可用");
        assertTrue(UserStatusEnum.isActive("ACTIVE"), "ACTIVE 可用");
        assertFalse(UserStatusEnum.isActive("DISABLED"), "DISABLED 不可用");
    }

    @Test
    void forName_只认合法枚举值() {
        assertEquals(UserStatusEnum.ACTIVE, UserStatusEnum.forName("ACTIVE"));
        assertEquals(UserStatusEnum.DISABLED, UserStatusEnum.forName("DISABLED"));
        assertNull(UserStatusEnum.forName("FOO"), "非法值必须返回 null 以触发 132");
        assertNull(UserStatusEnum.forName("active"), "枚举名区分大小写");
    }

    @Test
    void 停用不存在的账号应被拒() {
        Long opId = activeAdminId();
        assertNotNull(opId, "库里应有可用管理员");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> userService.changeUserStatus(opId, 99999999L, "DISABLED"));
        assertEquals(ServiceErrorCodeConstants.LOGIN_INFO_NOT_EXIST.getCode(), ex.getCode());
    }

    @Test
    void 非法状态值应被132拦下() {
        Long opId = activeAdminId();
        assertNotNull(opId);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> userService.changeUserStatus(opId, 1L, "FOO"));
        assertEquals(ServiceErrorCodeConstants.USER_STATUS_INVALID.getCode(), ex.getCode(),
                "非法状态必须报 132，不能再复用 122");
    }

    @Test
    void 停用自己应被130拦下() {
        Long opId = activeAdminId();
        assertNotNull(opId);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> userService.changeUserStatus(opId, opId, "DISABLED"));
        assertEquals(ServiceErrorCodeConstants.CANNOT_DISABLE_SELF.getCode(), ex.getCode());
    }

    @Test
    void 重复停用同一账号应幂等成功() {
        Long opId = activeAdminId();
        assertNotNull(opId);
        // 找一个普通账号，反复切换 ACTIVE/DISABLED，第二次同状态调用应幂等返回
        Long normalId = userMapper.selectUserListByIdentity("NORMAL").stream()
                .filter(u -> UserStatusEnum.isActive(u.getStatus()))
                .map(UserDO::getId).findFirst().orElse(null);
        assertNotNull(normalId, "库里应有普通用户");
        try {
            userService.changeUserStatus(opId, normalId, "DISABLED");
            userService.changeUserStatus(opId, normalId, "DISABLED"); // 幂等，不抛异常
            userService.changeUserStatus(opId, normalId, "ACTIVE");
        } finally {
            userService.changeUserStatus(opId, normalId, "ACTIVE");
        }
    }

    /*
     * 这是 131 唯一可达的路径，也是加事务 + 行锁的全部理由：
     * 两个管理员在同一瞬间互相停用对方。
     *
     * 没有锁时：双方各自读到 count=2、都通过校验 → 两个管理员一起被停用
     *          → 全系统再无人能启用任何账号（此后再怎么调代码都救不回来）。
     * 有锁时：后到的那次拿到的是前一次提交后的 status，count 变成 1 → 被 131 拦下。
     *
     * 断言只要求「两个结果里至少有一个是 131」——即不允许两条都成功。
     * 哪一条先拿到锁是不确定的，不该断言具体是谁。
     */
    @Test
    void 两个管理员并发互停_不能双双成功() throws Exception {
        List<UserDO> allAdmins = userMapper.selectUserListByIdentity("ADMIN");
        List<Long> allIds = allAdmins.stream().map(UserDO::getId).toList();
        // 先把除最后两个之外的管理员全部停用，制造「系统只剩两名管理员」的临界状态。
        // 不这么做的话，库里管理员不止两个，互停两次后仍然还有别人能管理，
        // 规则本来就该放行 —— 那不是 bug，是测试前置没搭对。
        List<Long> spare = allIds.subList(0, Math.max(0, allIds.size() - 2));
        for (Long spareId : spare) {
            userMapper.updateStatus(spareId, UserStatusEnum.DISABLED.name());
        }

        List<Long> admins = allIds.subList(Math.max(0, allIds.size() - 2), allIds.size());
        assertEquals(2, admins.size(), "需要恰好剩下 2 个可用管理员才能构造互停");

        Long a = admins.get(0);
        Long b = admins.get(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);

        Callable<Integer> stopB = () -> {
            ready.countDown();
            fire.await();
            try {
                userService.changeUserStatus(a, b, "DISABLED");
                return 200;
            } catch (ServiceException e) {
                return e.getCode();
            }
        };
        Callable<Integer> stopA = () -> {
            ready.countDown();
            fire.await();
            try {
                userService.changeUserStatus(b, a, "DISABLED");
                return 200;
            } catch (ServiceException e) {
                return e.getCode();
            }
        };

        try {
            Future<Integer> f1 = pool.submit(stopB);
            Future<Integer> f2 = pool.submit(stopA);
            ready.await(5, TimeUnit.SECONDS);
            fire.countDown();               // 同时放行，最大化并发窗口
            int r1 = f1.get(30, TimeUnit.SECONDS);
            int r2 = f2.get(30, TimeUnit.SECONDS);

            assertFalse(r1 == 200 && r2 == 200,
                    "两个管理员被同时停用会导致全系统锁死，至少一方必须被 131 拦下"
                            + " | r1=" + r1 + " r2=" + r2
                            + " | " + a + "=" + userMapper.selectById(a).getStatus()
                            + " " + b + "=" + userMapper.selectById(b).getStatus());
        } finally {
            pool.shutdownNow();
            // 复原：所有管理员一律恢复 ACTIVE，不管本次停用了谁
            for (Long id : allIds) {
                userMapper.updateStatus(id, UserStatusEnum.ACTIVE.name());
            }
        }
    }
}