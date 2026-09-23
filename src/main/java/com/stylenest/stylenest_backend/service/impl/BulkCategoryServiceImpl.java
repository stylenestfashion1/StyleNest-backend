package com.stylenest.stylenest_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryResponse;
import com.stylenest.stylenest_backend.entity.BulkCategory;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.BulkCategoryMapper;
import com.stylenest.stylenest_backend.repository.BulkCategoryRepository;
import com.stylenest.stylenest_backend.repository.BulkProductRepository;
import com.stylenest.stylenest_backend.service.BulkCategoryService;
import com.stylenest.stylenest_backend.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BulkCategoryServiceImpl implements BulkCategoryService {

    private final BulkCategoryRepository bulkCategoryRepository;
    private final BulkProductRepository bulkProductRepository;
    private final BulkCategoryMapper bulkCategoryMapper;

    @Override
    public BulkCategoryResponse createCategory(BulkCategoryRequest request) {

        if (bulkCategoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException(
                    "Bulk category '" + request.getName() + "' already exists.");
        }

        BulkCategory category = bulkCategoryMapper.toEntity(request);

        category.setSlug(SlugUtil.generateSlug(request.getName()));

        category = bulkCategoryRepository.save(category);

        return bulkCategoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkCategoryResponse> getAllCategories() {

        return bulkCategoryRepository.findAll()
                .stream()
                .map(bulkCategoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkCategoryResponse> getActiveCategories() {

        return bulkCategoryRepository.findByActiveTrue()
                .stream()
                .map(bulkCategoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BulkCategoryResponse getCategoryById(Long id) {

        return bulkCategoryMapper.toResponse(findById(id));
    }

    @Override
    public BulkCategoryResponse updateCategory(Long id, BulkCategoryRequest request) {

        BulkCategory category = findById(id);

        boolean nameChanged = !category.getName().equalsIgnoreCase(request.getName());

        if (nameChanged && bulkCategoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException(
                    "Bulk category '" + request.getName() + "' already exists.");
        }

        bulkCategoryMapper.updateEntity(category, request);

        if (nameChanged) {
            category.setSlug(SlugUtil.generateSlug(request.getName()));
        }

        category = bulkCategoryRepository.save(category);

        return bulkCategoryMapper.toResponse(category);
    }

    @Override
    public void deleteCategory(Long id) {

        BulkCategory category = findById(id);

        if (bulkProductRepository.existsByCategory(category)) {
            throw new BadRequestException(
                    "Cannot delete a bulk category while bulk products are assigned to it. "
                            + "Reassign or remove those products first, or deactivate the category instead.");
        }

        bulkCategoryRepository.delete(category);
    }

    private BulkCategory findById(Long id) {

        return bulkCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk category not found with id: " + id));
    }
}
