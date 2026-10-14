package com.restaurant.app.model;

import com.restaurant.app.util.UuidV7Generator;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.util.UUID;

@MappedSuperclass
public abstract class EntidadUuid {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    void asignarUuidSiFalta() {
        if (id == null) {
            id = UuidV7Generator.nuevo();
        }
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof EntidadUuid)) {
            return false;
        }

        EntidadUuid otra = (EntidadUuid) otro;

        if (id == null || otra.id == null) {
            return false;
        }

        return id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + id + ")";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
