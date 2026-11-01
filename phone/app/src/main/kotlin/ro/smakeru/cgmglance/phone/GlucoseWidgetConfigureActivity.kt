package ro.smakeru.cgmglance.phone

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Launcher configuration and reconfiguration; backing out never creates a widget. */
class GlucoseWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
            info?.provider != ComponentName(this, GlucoseWidgetProvider::class.java)
        ) {
            finish()
            return
        }
        val initialSource = GlucoseWidgetProvider.loadSource(this, id)?.name ?: ""
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selected by rememberSaveable { mutableStateOf(initialSource) }
                    var saveFailed by rememberSaveable { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.widget_config_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(stringResource(R.string.widget_config_description))
                        val choices =
                            listOf(
                                "" to stringResource(R.string.widget_follow_selected),
                                GlucoseSource.LINX.name to GlucoseSource.LINX.label,
                                GlucoseSource.DEXCOM_ONE_PLUS.name to GlucoseSource.DEXCOM_ONE_PLUS.label,
                            )
                        choices.forEach { (key, label) ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selected == key,
                                            onClick = { selected = key },
                                            role = Role.RadioButton,
                                        ).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == key, onClick = null)
                                Text(label, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                        if (saveFailed) Text(stringResource(R.string.widget_save_failed))
                        Button(
                            onClick = {
                                val source = GlucoseSource.entries.firstOrNull { it.name == selected }
                                if (GlucoseWidgetProvider.saveSource(this@GlucoseWidgetConfigureActivity, id, source)) {
                                    GlucoseWidgetProvider.refresh(this@GlucoseWidgetConfigureActivity)
                                    setResult(
                                        Activity.RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                                    )
                                    finish()
                                } else {
                                    saveFailed = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.widget_save))
                        }
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.widget_cancel))
                        }
                        Text(stringResource(R.string.widget_delay_notice), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
