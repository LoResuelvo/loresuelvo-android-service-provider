package com.loresuelvo.serviceprovider.domain.account

enum class CalendarConnectionStatus {
    Unavailable, Disconnected, Connected, ActionRequired;

    val canAuthorize: Boolean get() = this == Disconnected || this == ActionRequired
}
