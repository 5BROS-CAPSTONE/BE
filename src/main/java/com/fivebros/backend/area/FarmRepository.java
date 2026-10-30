package com.fivebros.backend.area;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fivebros.backend.area.dto.FarmResponse;

public interface FarmRepository extends JpaRepository<Farm, UUID> {
    Page<Farm> findByNameContainingIgnoreCase(String search, Pageable pageable);

    @Query(value = """
            select new com.fivebros.backend.area.dto.FarmResponse(
                farm.id, farm.name, farm.description, farm.createdBy, farm.updatedBy,
                farm.createdAt, farm.updatedAt, farm.version, count(field.id)
            )
            from Farm farm
            left join Field field on field.farmId = farm.id
            where lower(farm.name) like concat('%', lower(:search), '%')
            group by farm.id, farm.name, farm.description, farm.createdBy, farm.updatedBy,
                     farm.createdAt, farm.updatedAt, farm.version
            """, countQuery = """
            select count(farm)
            from Farm farm
            where lower(farm.name) like concat('%', lower(:search), '%')
            """)
    Page<FarmResponse> searchWithFieldCount(@Param("search") String search, Pageable pageable);
}
