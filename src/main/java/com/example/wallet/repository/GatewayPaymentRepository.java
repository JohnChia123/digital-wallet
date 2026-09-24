package com.example.wallet.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.wallet.entity.GatewayPayment;

public interface GatewayPaymentRepository extends JpaRepository<GatewayPayment, UUID> {}
