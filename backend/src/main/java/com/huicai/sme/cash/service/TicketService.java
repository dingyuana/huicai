package com.huicai.sme.cash.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.TicketEntity;
import com.huicai.sme.cash.entity.TicketTransactionEntity;

import java.util.List;

public interface TicketService {

    IPage<TicketEntity> pageQuery(String ticketType, String status, Integer current, Integer size);

    TicketEntity getById(Long id);

    TicketEntity create(TicketEntity entity, Long userId);

    TicketEntity update(Long id, TicketEntity entity);

    void delete(Long id);

    TicketEntity issue(Long id, Long userId);

    TicketEntity cash(Long id, Long userId);

    TicketEntity voidTicket(Long id, Long userId);

    List<TicketTransactionEntity> getTransactions(Long ticketId);
}