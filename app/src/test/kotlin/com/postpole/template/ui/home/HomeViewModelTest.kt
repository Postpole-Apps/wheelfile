package com.postpole.template.ui.home

import com.postpole.template.data.Item
import com.postpole.template.data.ItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.postpole.template.review.ReviewPrompter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeDao : ItemDao {
        val state = MutableStateFlow<List<Item>>(emptyList())
        override fun observeAll(): Flow<List<Item>> = state
        override suspend fun getAll(): List<Item> = state.value.sortedBy { it.id }
        override suspend fun insert(item: Item): Long {
            val id = state.value.size + 1L
            state.value = listOf(item.copy(id = id)) + state.value
            return id
        }
        override suspend fun insertAll(items: List<Item>) {
            state.value = items + state.value
        }
        override suspend fun deleteAll() {
            state.value = emptyList()
        }
        override suspend fun delete(item: Item) {
            state.value = state.value.filterNot { it.id == item.id }
        }
    }

    private class FakeReview(var prompt: Boolean = false) : ReviewPrompter {
        var actions = 0
        override suspend fun recordPositiveAction(): Boolean {
            actions++
            return prompt
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun addItem_trimsAndInserts() = runTest {
        val dao = FakeDao()
        val vm = HomeViewModel(dao, FakeReview())
        vm.addItem("  hello  ", now = 42)
        assertEquals(listOf("hello"), dao.state.value.map { it.title })
        assertEquals(42L, dao.state.value.single().createdAt)
    }

    @Test
    fun addItem_ignoresBlank() = runTest {
        val dao = FakeDao()
        val review = FakeReview()
        val vm = HomeViewModel(dao, review)
        vm.addItem("   ")
        assertEquals(emptyList<Item>(), dao.state.value)
        assertEquals(0, review.actions)
    }

    @Test
    fun addItem_requestsReviewWhenPolicyAllows() = runTest {
        val vm = HomeViewModel(FakeDao(), FakeReview(prompt = true))
        vm.addItem("a")
        vm.reviewRequests.first()
    }
}
