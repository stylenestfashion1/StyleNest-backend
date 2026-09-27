package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.dto.category.CategoryResponse;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.CategoryMapper;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;

/**
 * Covers only the new slug lookup added for the clean /women/{categorySlug}
 * frontend route -- this file doesn't attempt to backfill test coverage for
 * CategoryServiceImpl's other pre-existing, already-shipping behavior.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private final CategoryMapper categoryMapper = new CategoryMapper();

    private CategoryServiceImpl newService() {
        return new CategoryServiceImpl(categoryRepository, categoryMapper, productRepository);
    }

    @Test
    void getCategoryBySlug_found_returnsResponse() {

        CategoryServiceImpl service = newService();

        Category category = Category.builder()
                .id(7L).name("Kurti").slug("women-kurti").gender(Gender.WOMEN).build();

        when(categoryRepository.findBySlug("women-kurti")).thenReturn(Optional.of(category));

        CategoryResponse response = service.getCategoryBySlug("women-kurti");

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getSlug()).isEqualTo("women-kurti");
    }

    @Test
    void getCategoryBySlug_notFound_throwsResourceNotFound() {

        CategoryServiceImpl service = newService();

        when(categoryRepository.findBySlug("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategoryBySlug("does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
