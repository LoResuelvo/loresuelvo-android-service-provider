package com.loresuelvo.serviceprovider.domain.activity

data class JobRequest(
    val id: Int,
    val consumerName: String,
    val title: String,
    val description: String,
    val images: List<JobRequestImage> = emptyList(),
)

data class JobRequestImage(
    val id: String,
    val url: String,
    val originalName: String,
)
