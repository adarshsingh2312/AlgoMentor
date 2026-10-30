package com.algomentor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider;

    @Column(name = "cf_handle")
    private String cfHandle;

    @Column(name = "digest_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean digestEnabled = true;

    @Column(name = "last_digest_date")
    private LocalDate lastDigestDate;
}
