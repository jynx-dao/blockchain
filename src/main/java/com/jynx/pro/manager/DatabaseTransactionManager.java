package com.jynx.pro.manager;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

@Component
public class DatabaseTransactionManager {

    @Getter
    @Autowired
    private EntityManager readEntityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private EntityManager writeEntityManager = null;
    private EntityTransaction tx;

    public EntityManager getWriteEntityManager() {
        if(writeEntityManager == null) {
            throw new JynxProException(ErrorCode.TX_NOT_CREATED);
        }
        return writeEntityManager;
    }

    public void createTransaction() {
        writeEntityManager = entityManagerFactory.createEntityManager();
        tx = writeEntityManager.getTransaction();
        tx.begin();
    }

    public void commit() {
        tx.commit();
        writeEntityManager.close();
    }

    public void rollback() {
        tx.rollback();
        writeEntityManager.close();
    }
}