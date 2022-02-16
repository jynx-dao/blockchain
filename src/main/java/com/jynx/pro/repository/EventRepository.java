package com.jynx.pro.repository;

import com.jynx.pro.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByConfirmed(boolean confirmed);
}