package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CategoryDto
import com.loresuelvo.serviceprovider.domain.category.Category

/**
 * DTO -> domain translation for service categories. The wire shape
 * already matches the domain field names, so this is a pure copy;
 * keeping it in a mapper preserves the rule that `data/` is the only
 * place that touches DTOs.
 */
internal fun CategoryDto.toDomain(): Category =
    Category(id = id, name = name)

internal fun List<CategoryDto>.toDomain(): List<Category> =
    map { it.toDomain() }