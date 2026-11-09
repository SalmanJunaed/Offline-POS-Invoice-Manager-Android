package com.example.pos

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosViewModelInitializationTest {

    @Test
    fun testPosViewModelInitializesWithoutAbstractMethodError() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()

        // Instantiate PosViewModel - this invokes PosRepository.getOverallOutstandingDue() and refreshHistory()
        val viewModel = PosViewModel(application)

        assertNotNull("PosViewModel must not be null", viewModel)

        // Verify overallDueBdt flow can be queried without throwing AbstractMethodError
        val overallDue = viewModel.overallDueBdt.first()
        assertTrue("overallDue must be >= 0", overallDue >= 0L)

        // Verify periodDueBdt flow can be queried
        val periodDue = viewModel.periodDueBdt.first()
        assertTrue("periodDue must be >= 0", periodDue >= 0L)

        // Verify totalCollectionBdt flow can be queried
        val totalCollection = viewModel.totalCollectionBdt.first()
        assertTrue("totalCollection must be >= 0", totalCollection >= 0L)

        // Verify paymentBreakdown flow can be queried
        val breakdown = viewModel.paymentBreakdown.first()
        assertNotNull("paymentBreakdown must not be null", breakdown)
    }
}
