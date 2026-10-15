package com.vlp.content.repo;

import com.vlp.content.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByEnabledTrueOrderBySortOrderAsc();
    List<Category> findAllByOrderBySortOrderAsc();
}
