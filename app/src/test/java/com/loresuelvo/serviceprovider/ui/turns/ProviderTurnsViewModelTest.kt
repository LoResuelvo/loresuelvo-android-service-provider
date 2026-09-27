package com.loresuelvo.serviceprovider.ui.turns

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderTurnsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun loads_all_work_orders_without_home_status_filter() = runTest(dispatcher.scheduler) {
        val orders = listOf(
            WorkOrder(1, "Ana", "First", 1, WorkOrderStatus.Scheduled),
            WorkOrder(2, "Bea", "Second", 2, WorkOrderStatus.Paid),
        )
        val repository = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(orders)
        }

        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository))
        assertEquals(ProviderTurnsUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(orders, (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders)
        assertTrue((viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.any { it.status is WorkOrderStatus.Paid })
    }
}
