package com.retail.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.retail.transaction.entity.TransactionType;

@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {
}
