-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: lottery_system
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `lottery_system`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `lottery_system` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `lottery_system`;

--
-- Table structure for table `activity`
--

DROP TABLE IF EXISTS `activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `activity_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '活动名称',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '活动描述',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '活动状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=209 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_prize`
--

DROP TABLE IF EXISTS `activity_prize`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_prize` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `activity_id` bigint NOT NULL COMMENT '活动id',
  `prize_id` bigint NOT NULL COMMENT '活动关联的奖品id',
  `prize_amount` bigint NOT NULL DEFAULT '1' COMMENT '关联奖品的数量',
  `prize_tier` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '奖品等级',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '活动奖品状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_id` (`id`) USING BTREE,
  UNIQUE KEY `uk_a_p_id` (`activity_id`,`prize_id`) USING BTREE,
  KEY `idx_activity_id` (`activity_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=223 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_user`
--

DROP TABLE IF EXISTS `activity_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `activity_id` bigint NOT NULL COMMENT '活动时间',
  `user_id` bigint NOT NULL COMMENT '圈选的用户id',
  `user_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户名',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_id` (`id`) USING BTREE,
  UNIQUE KEY `uk_a_u_id` (`activity_id`,`user_id`) USING BTREE,
  KEY `idx_activity_id` (`activity_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=212 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `faq`
--

DROP TABLE IF EXISTS `faq`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `faq` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `question` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '闂??',
  `answer` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '绛旀?',
  `category` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '閫氱敤' COMMENT '鍒嗙被,濡?鎶藉?瑙勫垯/棰嗗?/璐﹀彿',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '淇?敼鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=197 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI瀹㈡湇鐭ヨ瘑搴';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `faq_embedding`
--

DROP TABLE IF EXISTS `faq_embedding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `faq_embedding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `faq_id` bigint NOT NULL COMMENT '瀵瑰簲faq琛ㄧ殑id',
  `embedding` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '鍚戦噺,JSON鏁扮粍瀛楃?涓?濡?[0.12,-0.35,...]',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '淇?敼鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_faq_id` (`faq_id`)
) ENGINE=InnoDB AUTO_INCREMENT=122 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='FAQ鍚戦噺琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `human_handover`
--

DROP TABLE IF EXISTS `human_handover`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `human_handover` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` bigint DEFAULT NULL COMMENT '鐢ㄦ埛ID(浠巘oken瑙ｆ瀽,鍙?兘涓虹┖)',
  `session_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '浼氳瘽鏍囪瘑',
  `type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'HANDOVER' COMMENT '工单类型:HANDOVER转人工/CLAIM领奖发货',
  `ref_id` bigint DEFAULT NULL COMMENT '关联业务ID(领奖工单=winning_record.id)',
  `question` text COLLATE utf8mb4_general_ci COMMENT '鐢ㄦ埛杞?汉宸ユ椂鏈?悗璇寸殑璇',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'PENDING待处理/PROCESSING处理中/RESOLVED已解决',
  `handler` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '澶勭悊浜',
  `remark` text COLLATE utf8mb4_general_ci COMMENT '澶勭悊澶囨敞',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '淇?敼鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_type_status` (`type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=242 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='杞?汉宸ュ伐鍗曡〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `operation_log`
--

DROP TABLE IF EXISTS `operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID，未登录为空',
  `identity` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份：ADMIN/NORMAL/ANONYMOUS',
  `module` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务模块，如 活动模块',
  `operation` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作描述，如 创建活动',
  `http_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求方法',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求路径',
  `result_code` int DEFAULT NULL COMMENT '业务返回码，200 表示成功',
  `result_msg` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '结果描述/失败原因',
  `http_status` int DEFAULT NULL COMMENT 'HTTP 状态码',
  `cost_ms` bigint DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源IP',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_create` (`gmt_create`),
  KEY `idx_user` (`user_id`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB AUTO_INCREMENT=3251 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `prize`
--

DROP TABLE IF EXISTS `prize`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prize` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '奖品名称',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '奖品描述',
  `price` decimal(10,2) NOT NULL COMMENT '奖品价值',
  `image_url` varchar(2048) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '奖品展示图',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `spring_ai_chat_memory`
--

DROP TABLE IF EXISTS `spring_ai_chat_memory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `spring_ai_chat_memory` (
  `conversation_id` varchar(36) COLLATE utf8mb4_general_ci NOT NULL,
  `content` text COLLATE utf8mb4_general_ci NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `timestamp` timestamp NOT NULL,
  KEY `SPRING_AI_CHAT_MEMORY_IDX` (`conversation_id`,`timestamp`),
  CONSTRAINT `TYPE_CHECK` CHECK ((`type` in (_utf8mb4'USER',_utf8mb4'ASSISTANT',_utf8mb4'SYSTEM',_utf8mb4'TOOL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spring AI Jdbc 浼氳瘽璁板繂';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户姓名',
  `email` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '邮箱',
  `phone_number` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '手机号',
  `password` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '登录密码',
  `identity` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户身份',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `receiver_name` varchar(50) DEFAULT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(255) DEFAULT NULL COMMENT '收货人手机号(EncryptTypeHandler 加密存储)',
  `receiver_address` varchar(500) DEFAULT NULL COMMENT '收货地址',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_id` (`id`) USING BTREE,
  UNIQUE KEY `uk_email` (`email`(30)) USING BTREE,
  UNIQUE KEY `uk_phone_number` (`phone_number`(11)) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=613 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `winning_record`
--

DROP TABLE IF EXISTS `winning_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `winning_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `activity_id` bigint NOT NULL COMMENT '活动id',
  `activity_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '活动名称',
  `prize_id` bigint NOT NULL COMMENT '奖品id',
  `prize_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '奖品名称',
  `prize_tier` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '奖品等级',
  `winner_id` bigint NOT NULL COMMENT '中奖人id',
  `winner_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '中奖人姓名',
  `winner_email` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '中奖人邮箱',
  `winner_phone_number` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '中奖人电话',
  `winning_time` datetime NOT NULL COMMENT '中奖时间',
  `claim_status` varchar(20) NOT NULL DEFAULT 'NOT_CLAIMED' COMMENT '领奖状态:NOT_CLAIMED未领取/CLAIMED已申请待发货/SHIPPED已发货/COMPLETED已签收',
  `claim_time` datetime DEFAULT NULL COMMENT '申请领奖时间',
  `shipping_company` varchar(50) DEFAULT NULL COMMENT '承运公司',
  `shipping_no` varchar(100) DEFAULT NULL COMMENT '物流单号',
  `shipped_time` datetime DEFAULT NULL COMMENT '发货时间',
  `completed_time` datetime DEFAULT NULL COMMENT '确认收货时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_id` (`id`) USING BTREE,
  UNIQUE KEY `uk_w_a_p_id` (`winner_id`,`activity_id`,`prize_id`) USING BTREE,
  KEY `idx_activity_id` (`activity_id`) USING BTREE,
  KEY `idx_winner_claim` (`winner_id`,`claim_status`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'lottery_system'
--

--
-- Dumping routines for database 'lottery_system'
--
/*!50003 DROP PROCEDURE IF EXISTS `cleanup_test_data` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE DEFINER=`root`@`localhost` PROCEDURE `cleanup_test_data`(IN dry_run TINYINT)
BEGIN
    DECLARE v_handover INT DEFAULT 0;
    DECLARE v_user     INT DEFAULT 0;
    DECLARE v_prize    INT DEFAULT 0;
    DECLARE v_activity INT DEFAULT 0;
    DECLARE v_total    INT DEFAULT 0;

    
    SELECT COUNT(*) INTO @unsafe_refs
      FROM winning_record
     WHERE winner_id IN (SELECT id FROM user WHERE email LIKE 'qa_auto_%');

    IF @unsafe_refs > 0 THEN
        SELECT CONCAT('已中止：测试账号被中奖记录引用 ', @unsafe_refs,
                      ' 条（可能误删真实数据，请人工确认后再执行）') AS abort_reason;
    ELSE
        SELECT IF(dry_run = 1,
                  '[干跑模式] 只统计，不做任何删除',
                  '[执行模式] 即将删除测试数据') AS mode;

        
        SELECT COUNT(*) INTO v_handover FROM human_handover
         WHERE session_id LIKE 'qa_auto_%'
            OR user_id IN (SELECT id FROM user WHERE email LIKE 'qa_auto_%');
        SELECT COUNT(*) INTO v_user FROM user WHERE email LIKE 'qa_auto_%';
        SELECT COUNT(*) INTO v_prize FROM prize
         WHERE name LIKE 'qa_auto_%' OR name LIKE 'QA_AUTO_%';
        SELECT COUNT(*) INTO v_activity FROM activity
         WHERE activity_name LIKE 'qa_auto_%' OR activity_name LIKE 'QA_AUTO_%';
        SET v_total = v_handover + v_user + v_prize + v_activity;

        SELECT 'human_handover' AS table_name, v_handover AS will_delete
        UNION ALL SELECT 'user', v_user
        UNION ALL SELECT 'prize', v_prize
        UNION ALL SELECT 'activity', v_activity
        UNION ALL SELECT '合计', v_total;

        IF dry_run = 0 AND v_total > 0 THEN
            
            DELETE FROM human_handover
             WHERE session_id LIKE 'qa_auto_%'
                OR user_id IN (SELECT id FROM user WHERE email LIKE 'qa_auto_%');
            SELECT ROW_COUNT() INTO v_handover;

            DELETE FROM user WHERE email LIKE 'qa_auto_%';
            SELECT ROW_COUNT() INTO v_user;

            DELETE FROM prize WHERE name LIKE 'qa_auto_%' OR name LIKE 'QA_AUTO_%';
            SELECT ROW_COUNT() INTO v_prize;

            DELETE FROM activity
             WHERE activity_name LIKE 'qa_auto_%' OR activity_name LIKE 'QA_AUTO_%';
            SELECT ROW_COUNT() INTO v_activity;

            SELECT 'human_handover' AS table_name, v_handover AS deleted
            UNION ALL SELECT 'user', v_user
            UNION ALL SELECT 'prize', v_prize
            UNION ALL SELECT 'activity', v_activity
            UNION ALL SELECT '合计', v_handover + v_user + v_prize + v_activity;
        ELSEIF dry_run = 0 THEN
            SELECT '无待删数据，未执行任何删除' AS result;
        END IF;

        
        SELECT 'user' AS baseline_item, COUNT(*) AS current_value FROM user
        UNION ALL SELECT 'faq', COUNT(*) FROM faq
        UNION ALL SELECT 'human_handover', COUNT(*) FROM human_handover
        UNION ALL SELECT 'prize', COUNT(*) FROM prize
        UNION ALL SELECT 'activity', COUNT(*) FROM activity;
    END IF;
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 DROP PROCEDURE IF EXISTS `preview_test_data` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE DEFINER=`root`@`localhost` PROCEDURE `preview_test_data`()
BEGIN
    SELECT '[预览] 待删数据（白名单前缀 qa_auto_ / QA_AUTO_）' AS stage;

    SELECT 'human_handover' AS table_name,
           COUNT(*) AS will_delete
      FROM human_handover
     WHERE session_id LIKE 'qa_auto_%'
        OR user_id IN (SELECT id FROM user WHERE email LIKE 'qa_auto_%')
    UNION ALL
    SELECT 'user', COUNT(*) FROM user WHERE email LIKE 'qa_auto_%'
    UNION ALL
    SELECT 'prize', COUNT(*) FROM prize
     WHERE name LIKE 'qa_auto_%' OR name LIKE 'QA_AUTO_%'
    UNION ALL
    SELECT 'activity', COUNT(*) FROM activity
     WHERE activity_name LIKE 'qa_auto_%' OR activity_name LIKE 'QA_AUTO_%';

    SELECT '[预览] 关联安全检查（应全为 0，非 0 表示测试数据被真实数据引用，需人工确认）' AS stage;
    SELECT 'activity_user' AS relation,
           COUNT(*) AS refs FROM activity_user
     WHERE activity_id IN (SELECT id FROM activity
                            WHERE activity_name LIKE 'qa_auto_%' OR activity_name LIKE 'QA_AUTO_%')
    UNION ALL
    SELECT 'activity_prize', COUNT(*) FROM activity_prize
     WHERE activity_id IN (SELECT id FROM activity
                            WHERE activity_name LIKE 'qa_auto_%' OR activity_name LIKE 'QA_AUTO_%')
    UNION ALL
    SELECT 'winning_record(by_activity)', COUNT(*) FROM winning_record
     WHERE activity_id IN (SELECT id FROM activity
                            WHERE activity_name LIKE 'qa_auto_%' OR activity_name LIKE 'QA_AUTO_%')
    UNION ALL
    SELECT 'winning_record(by_winner)', COUNT(*) FROM winning_record
     WHERE winner_id IN (SELECT id FROM user WHERE email LIKE 'qa_auto_%');
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-24 20:10:07
