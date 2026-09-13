package com.loresuelvo.serviceprovider.domain.activity

data class JobRequest(
    val id: Int,
    val consumerName: String,
    val title: String,
    val description: String,
)
