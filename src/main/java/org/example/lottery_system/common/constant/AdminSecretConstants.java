package org.example.lottery_system.common.constant;

/**
 * 管理员口令：唯一的单点定义。
 *
 * <p>两个消费方必须引用同一个常量，历史上它们各自硬编码一份字符串，
 * 改一处忘一处就出现「注册管理员用的口令」和「口令验证改密用的口令」不一致，
 * 故障表现为功能莫名拒绝、极难定位：
 * <ul>
 *   <li>{@code UserController#applyRegisterIdentityPolicy} —— X-Admin-Secret 注册管理员；</li>
 *   <li>{@code UserServiceImpl#verifyBySecret} —— 管理员 verifyType=secret 改自己的邮箱/密码。</li>
 * </ul>
 *
 * <p>明文写在源码里是本项目的明确取舍：课程项目、不对外部署，口令的作用
 * 是"演示时别让人随手造管理员"，不是生产密钥；外置到配置/环境变量反而
 * 引入"忘配置=空串=带空头 X-Admin-Secret 即可造管理员"的新风险
 * （"".equals("") 为 true），收益没拿到、坑多了一个。
 * 若将来真要部署，必须连同 fail-closed（空配置禁用）一起改，不能只挪值。
 */
public final class AdminSecretConstants {

    public static final String ADMIN_SECRET = "274699";

    private AdminSecretConstants() {
    }
}
