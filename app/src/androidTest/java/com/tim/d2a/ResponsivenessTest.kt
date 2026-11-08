package com.tim.d2a

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.MdxDictionary
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResponsivenessTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun recoveryAndSettingsPersistenceStayOffMainThread() {
        val backing = rule.activity.getSharedPreferences("responsiveness-test", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        val mainThreadCalls = ConcurrentLinkedQueue<String>()
        fun record(operation: String) {
            if (Looper.myLooper() == Looper.getMainLooper()) mainThreadCalls.add(operation)
        }
        val prefs = object : SharedPreferences by backing {
            override fun getString(key: String?, defValue: String?): String? {
                record("read $key")
                return backing.getString(key, defValue)
            }
            override fun edit(): SharedPreferences.Editor {
                record("edit")
                val editor = backing.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        record("write $key")
                        editor.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        record("commit")
                        return editor.commit()
                    }
                }
            }
        }
        val dictionary = MdxDictionary(rule.activity.contentResolver)
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                DictAnkiTheme { DictAnkiApp(preferencesOverride = prefs, dictionaryOverride = dictionary,
                    openRouterService = TestOpenRouterService()) }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag("openrouter-api-key").performTextInput("responsiveness-key")
        rule.waitUntil(5_000) { backing.getString("openrouter_key", "") == "responsiveness-key" }
        assertTrue("Preference operations ran on main: $mainThreadCalls", mainThreadCalls.isEmpty())
    }
}
