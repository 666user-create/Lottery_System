package org.example.lottery_system.service.impl.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.example.lottery_system.common.config.DirectRabbitConfig.*;
import static org.example.lottery_system.common.config.DirectRabbitConfig.DLX_QUEUE_NAME;


/**
 * @author: yibo
 */
@Component
@RabbitListener(queues = DLX_QUEUE_NAME)
public class DlxReceiver {

    private static final Logger logger = LoggerFactory.getLogger(DlxReceiver.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void process(Map<String, String> message) {
        // 死信队列的处理方法
        logger.error("接收到死信消息，停止处理以防止无限循环。消息内容: {}", message);
        
        // 该流程是有问题的，直接重发会导致无限循环：消息堆积-》处理异常-》消息进入死信-》重发-》再次异常
        // rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING, message);
        
        // 正确的流程建议：
        // 1、接收到死信消息，记录到数据库表中（如：failed_messages 表）
        // 2、记录异常堆栈信息，方便排查
        // 3、通过报警通知开发人员人工干预
        // 4、待问题修复后，编写脚本或通过后台管理功能，手动或批量重新投递失败的消息
        logger.info("死信消息已记录，等待人工排查处理。");
    }
}
