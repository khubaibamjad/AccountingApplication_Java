package com.office.application.repository;

import com.office.application.model.Party;
import com.office.application.model.PartyType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository {
    List<Party> findAll();
    Optional<Party> findById(UUID id);
    List<Party> findByType(PartyType type);
    void save(Party party);
}