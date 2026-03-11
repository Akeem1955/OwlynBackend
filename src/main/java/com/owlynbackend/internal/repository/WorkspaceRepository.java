package com.owlynbackend.internal.repository;


import com.owlynbackend.internal.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface WorkspaceRepository  extends JpaRepository<Workspace, String> {
}
