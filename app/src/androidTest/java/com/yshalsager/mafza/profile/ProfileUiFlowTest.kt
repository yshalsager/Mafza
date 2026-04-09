package com.yshalsager.mafza.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yshalsager.mafza.MainActivity
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileUiFlowTest {
    @get:Rule
    val compose_rule = createAndroidComposeRule<MainActivity>()

    private lateinit var profile_store: EncryptedProfileStore
    private lateinit var original_profile: EmergencyProfile

    @Before
    fun set_up(): Unit = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        profile_store = ProfileDataStoreFactory.create_profile_store(
            context = app_context,
            profile_cipher = AndroidKeystoreProfileCipher()
        )
        original_profile = profile_store.read_profile()
        profile_store.write_profile(EmergencyProfile())
    }

    @After
    fun tear_down(): Unit = runBlocking {
        profile_store.write_profile(original_profile)
    }

    @Test
    fun top_bar_add_flow_adds_sms_action_and_marks_profile_dirty() {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val profile_tab_label = app_context.getString(R.string.profile_title)
        val add_action_label = app_context.getString(R.string.profile_topbar_add_action)
        val choose_group_title = app_context.getString(R.string.profile_add_action_choose_group_title)
        val choose_type_title = app_context.getString(R.string.profile_add_action_choose_type_title)
        val communication_group_label = app_context.getString(R.string.profile_action_group_communication)
        val sms_type_label = app_context.getString(R.string.profile_action_type_sms_recipient)
        val no_number_summary = app_context.getString(R.string.profile_action_row_summary_no_number_set)
        val unsaved_changes_text = app_context.getString(R.string.profile_unsaved_changes)

        compose_rule.onNodeWithText(profile_tab_label).performClick()
        compose_rule.onNodeWithContentDescription(add_action_label).performClick()

        compose_rule.onNodeWithText(choose_group_title).assertIsDisplayed()
        compose_rule.onAllNodesWithText(communication_group_label)[1].performClick()

        compose_rule.onNodeWithText(choose_type_title).assertIsDisplayed()
        compose_rule.onAllNodesWithText(sms_type_label)[0].performClick()

        compose_rule.onNodeWithText(no_number_summary).assertIsDisplayed()
        compose_rule.onNodeWithText(unsaved_changes_text).assertIsDisplayed()
    }
}
