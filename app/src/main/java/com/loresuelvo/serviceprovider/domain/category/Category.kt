package com.loresuelvo.serviceprovider.domain.category

/**
 * A service category offered on the platform (e.g. "Plomería",
 * "Electricidad"). Pure domain type: camelCase, no framework
 * dependencies. The backend's snake_case wire shape is mapped in
 * `data/category/mapper/CategoryMapper.kt`.
 */
data class Category(
    val id: Int,
    val name: String,
)