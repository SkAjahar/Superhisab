package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class CalculatorWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    for (appWidgetId in appWidgetIds) {
      updateAppWidget(context, appWidgetManager, appWidgetId)
    }
  }

  companion object {
    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
      val intent = Intent(context, MainActivity::class.java).apply {
        action = "com.example.ACTION_OPEN_CALCULATOR"
        putExtra("OPEN_TAB", "calculator")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }

      val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
      )
      val pendingIntent = PendingIntent.getActivity(context, 1001, intent, pendingFlags)

      val views = RemoteViews(context.packageName, R.layout.widget_calculator)
      views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
      views.setOnClickPendingIntent(R.id.widget_btn_open_calc, pendingIntent)
      views.setOnClickPendingIntent(R.id.widget_icon_box, pendingIntent)

      appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun updateAllWidgets(context: Context) {
      try {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, CalculatorWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        for (id in appWidgetIds) {
          updateAppWidget(context, appWidgetManager, id)
        }
      } catch (_: Exception) {}
    }
  }
}
