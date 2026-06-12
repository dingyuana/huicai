package com.huicai.module.ai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列与交换机配置
 * - 任务下发队列: huicai.ai.task.queue
 * - 结果回调队列: huicai.ai.result.queue
 * - DLQ: huicai.ai.dlq
 */
@Configuration
public class AiRabbitConfig {

    public static final String AI_EXCHANGE      = "huicai.ai.exchange";
    public static final String AI_TASK_QUEUE    = "huicai.ai.task.queue";
    public static final String AI_RESULT_QUEUE  = "huicai.ai.result.queue";
    public static final String AI_DLQ           = "huicai.ai.dlq";
    public static final String DLQ_EXCHANGE     = "huicai.ai.dlq.exchange";

    public static final String TASK_ROUTING_KEY   = "ai.task";
    public static final String RESULT_ROUTING_KEY = "ai.result";
    public static final String DLQ_ROUTING_KEY    = "ai.dlq";

    @Bean
    public DirectExchange aiExchange() {
        return new DirectExchange(AI_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE, true, false);
    }

    @Bean
    public Queue aiTaskQueue() {
        return QueueBuilder.durable(AI_TASK_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue aiResultQueue() {
        return QueueBuilder.durable(AI_RESULT_QUEUE).build();
    }

    @Bean
    public Queue aiDlq() {
        return QueueBuilder.durable(AI_DLQ).build();
    }

    @Bean
    public Binding taskBinding(Queue aiTaskQueue, DirectExchange aiExchange) {
        return BindingBuilder.bind(aiTaskQueue).to(aiExchange).with(TASK_ROUTING_KEY);
    }

    @Bean
    public Binding resultBinding(Queue aiResultQueue, DirectExchange aiExchange) {
        return BindingBuilder.bind(aiResultQueue).to(aiExchange).with(RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue aiDlq, DirectExchange dlqExchange) {
        return BindingBuilder.bind(aiDlq).to(dlqExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
